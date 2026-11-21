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
package dev.fasti.engine.audit.impl;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationManager;

/**
 * Who may use the audit trail: members of an administrators group, or users with a permission on
 * the resource {@code SYSTEM} (which exists in Camunda 7.24, so no new resource type is needed):
 * {@code READ} to read, export and verify, {@code DELETE} to purge.
 *
 * <p>Readers who are not administrators see, as in the queries of the engine, only the records
 * of the tenants they are authenticated for and the records without tenant, while the tenant check
 * of the engine is on.</p>
 */
public final class AuditAuthorization {

  private AuditAuthorization() {
  }

  public static void checkRead(CommandContext commandContext) {
    check(commandContext, SystemPermissions.READ);
  }

  public static void checkPurge(CommandContext commandContext) {
    check(commandContext, SystemPermissions.DELETE);
  }

  /**
   * The tenants whose records the authenticated user may read, or {@code null} for all: without
   * authentication, for administrators, and with the tenant check turned off.
   */
  public static List<String> readableTenants(CommandContext commandContext) {
    if (!commandContext.getTenantManager().isTenantCheckEnabled()) {
      return null;
    }
    List<String> tenants = commandContext.getAuthentication().getTenantIds();
    return tenants == null ? new ArrayList<>() : new ArrayList<>(tenants);
  }

  static void check(CommandContext commandContext, Permission permission) {
    if (!commandContext.getProcessEngineConfiguration().isAuthorizationEnabled()
        || !commandContext.isAuthorizationCheckEnabled()) {
      return;
    }
    AuthorizationManager authorizationManager = commandContext.getAuthorizationManager();
    Authentication authentication = commandContext.getAuthentication();
    if (authentication == null || authorizationManager.isCamundaAdmin(authentication)) {
      return;
    }
    authorizationManager.checkAuthorization(permission, Resources.SYSTEM);
  }
}
