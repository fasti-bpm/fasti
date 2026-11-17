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
package org.camunda.bpm.identity.impl.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class OidcIdentityProviderTest {

  static ProcessEngine engine;
  static IdentityService identityService;

  @BeforeClass
  public static void start() {
    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:oidc-identity;DB_CLOSE_DELAY=-1");
    configuration.setProcessEngineName("oidc-identity");
    configuration.setAuthorizationEnabled(true);
    configuration.setDbMetricsReporterActivate(false);
    configuration.getProcessEnginePlugins().add(new OidcIdentityProviderPlugin());
    engine = configuration.buildProcessEngine();
    identityService = engine.getIdentityService();
    // a database user and a group with a name
    identityService.saveUser(identityService.newUser("dora"));
    var sales = identityService.newGroup("sales");
    sales.setName("Sales");
    identityService.saveGroup(sales);
    identityService.createMembership("dora", "sales");
  }

  @AfterClass
  public static void stop() {
    engine.close();
  }

  @After
  public void clear() {
    identityService.clearAuthentication();
  }

  @Test
  public void groupsAndTenantsOfTheSignedInUserComeFromTheAuthentication() {
    identityService.setAuthentication("carol", List.of("sales", "operators"), List.of("t1"));

    assertThat(identityService.createGroupQuery().groupMember("carol").list())
        .extracting(Group::getId, Group::getName)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("operators", "operators"),
            org.assertj.core.groups.Tuple.tuple("sales", "Sales"));
    assertThat(identityService.createGroupQuery().groupMember("carol").groupNameLike("Sal%").count()).isEqualTo(1);
    assertThat(identityService.createTenantQuery().userMember("carol").list()).extracting(Tenant::getId).containsExactly("t1");
    assertThat(identityService.createUserQuery().userId("carol").singleResult().getId()).isEqualTo("carol");
  }

  @Test
  public void otherUsersAndRequestsWithoutAuthenticationUseTheDatabase() {
    identityService.setAuthentication("carol", List.of("operators"), List.of());
    // other users are not visible to a user without READ permissions
    assertThat(identityService.createUserQuery().userId("eve").count()).isZero();
    identityService.clearAuthentication();

    assertThat(identityService.createGroupQuery().groupMember("dora").list()).extracting(Group::getId).containsExactly("sales");
    assertThat(identityService.createUserQuery().userId("carol").count()).isZero();
  }
}
