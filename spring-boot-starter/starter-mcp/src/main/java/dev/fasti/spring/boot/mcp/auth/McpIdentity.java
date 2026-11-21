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
package dev.fasti.spring.boot.mcp.auth;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;

/**
 * The engine identity an MCP request was authenticated as. Every tool call runs with it set on
 * the {@link IdentityService}, so the authorization checks of the engine apply.
 *
 * @param userId the authenticated user
 * @param groups the groups of the user
 * @param tenants the tenants of the user
 */
public record McpIdentity(String userId, List<String> groups, List<String> tenants) {

  /** Request attribute holding the identity of an authenticated MCP request. */
  public static final String REQUEST_ATTRIBUTE = McpIdentity.class.getName();

  /** Key of the identity in the MCP transport context. */
  public static final String CONTEXT_KEY = "fasti.identity";

  public McpIdentity {
    groups = groups == null ? List.of() : List.copyOf(groups);
    tenants = tenants == null ? List.of() : List.copyOf(tenants);
  }

  /**
   * The identity of a user with the given groups and tenants; groups or tenants that are
   * {@code null} are read from the identity service, as the authentication filter of the REST API
   * does.
   */
  public static McpIdentity resolve(IdentityService identityService, String userId, List<String> groups,
                                    List<String> tenants) {
    List<String> groupIds = groups;
    if (groupIds == null) {
      groupIds = new ArrayList<>();
      for (Group group : identityService.createGroupQuery().groupMember(userId).list()) {
        groupIds.add(group.getId());
      }
    }
    List<String> tenantIds = tenants;
    if (tenantIds == null) {
      tenantIds = new ArrayList<>();
      for (Tenant tenant : identityService.createTenantQuery().userMember(userId).includingGroupsOfUser(true).list()) {
        tenantIds.add(tenant.getId());
      }
    }
    return new McpIdentity(userId, groupIds, tenantIds);
  }
}
