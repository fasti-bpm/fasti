/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fasti.engine.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Only administrators and users with a permission on SYSTEM may use the audit trail. */
public class AuditAuthorizationTest {

  static ProcessEngine engine;
  static AuditService audit;

  @BeforeClass
  public static void buildEngine() {
    engine = AuditTestSupport.engine("authz", plugin -> plugin.setRetentionDays(0),
        configuration -> configuration.setAuthorizationEnabled(true));
    audit = AuditTrail.of(engine);
    engine.getRepositoryService().createDeployment().addModelInstance("a.bpmn", AuditTestSupport.userTaskProcess("a")).deploy();
    Authorization read = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    read.setUserId("reader");
    read.setResource(Resources.SYSTEM);
    read.setResourceId("*");
    read.addPermission(SystemPermissions.READ);
    engine.getAuthorizationService().saveAuthorization(read);
    Authorization delete = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    delete.setUserId("purger");
    delete.setResource(Resources.SYSTEM);
    delete.setResourceId("*");
    delete.addPermission(SystemPermissions.DELETE);
    engine.getAuthorizationService().saveAuthorization(delete);
  }

  @AfterClass
  public static void closeEngine() {
    engine.getProcessEngineConfiguration().setAuthorizationEnabled(false);
    engine.getAuthorizationService().createAuthorizationQuery().list()
        .forEach(a -> engine.getAuthorizationService().deleteAuthorization(a.getId()));
    AuditTestSupport.cleanUp(engine, true);
  }

  @After
  public void clearAuthentication() {
    engine.getIdentityService().clearAuthentication();
  }

  @Test
  public void shouldRefuseUsersWithoutPermission() {
    engine.getIdentityService().setAuthentication("nobody", List.of("staff"));
    assertThatThrownBy(() -> audit.createQuery().count()).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> audit.createQuery().list()).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(audit::verify).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> audit.recordExport("csv", "all", 0)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> audit.purge(new Date())).isInstanceOf(AuthorizationException.class);
  }

  @Test
  public void shouldLetReadersReadExportAndVerifyButNotPurge() {
    engine.getIdentityService().setAuthentication("reader", List.of());
    assertThat(audit.createQuery().count()).isPositive();
    assertThat(audit.verify().isIntact()).isTrue();
    audit.recordExport("json", "all", 1);
    assertThatThrownBy(() -> audit.purge(new Date())).isInstanceOf(AuthorizationException.class);
  }

  @Test
  public void shouldLetUsersWithDeletePurge() {
    engine.getIdentityService().setAuthentication("purger", List.of());
    assertThat(audit.purge(new Date(0)).getRemoved()).isZero();
  }

  @Test
  public void shouldLetAdministratorsDoEverything() {
    engine.getIdentityService().setAuthentication("root", List.of(Groups.CAMUNDA_ADMIN));
    assertThat(audit.createQuery().count()).isPositive();
    assertThat(audit.verify().isIntact()).isTrue();
    assertThat(audit.purge(new Date(0)).getRemoved()).isZero();
  }

  @Test
  public void shouldRecordWhoReadsTheTrailAsActorOfVerifications() {
    engine.getIdentityService().setAuthentication("reader", List.of());
    audit.verify();
    engine.getIdentityService().setAuthentication("root", List.of(Groups.CAMUNDA_ADMIN));
    assertThat(audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_VERIFY).actor("reader").count())
        .isPositive();
  }
}
