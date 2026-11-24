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
package dev.fasti.engine.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_REVOKE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.fasti.engine.audit.StatementTrace;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The authorization check uses only the groups of the authenticated user that have
 * authorizations. Fasti reads that per group through the index on {@code GROUP_ID_}; it must
 * give the same result as reading the group ids of all authorizations, which Camunda 7.24 does
 * on every command.
 */
public class AuthorizedGroupFilterTest {

  static final StatementTrace TRACE = new StatementTrace();
  static ProcessEngine engine;
  static TaskService taskService;
  static AuthorizationService authorizationService;
  static IdentityService identityService;

  @BeforeClass
  public static void buildEngine() {
    ProcessEngineConfigurationImpl configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-authorized-groups;DB_CLOSE_DELAY=10");
    PooledDataSource dataSource = new PooledDataSource(ReflectUtil.getClassLoader(), configuration.getJdbcDriver(),
        configuration.getJdbcUrl(), configuration.getJdbcUsername(), configuration.getJdbcPassword());
    configuration.setDataSource(TRACE.wrap(dataSource));
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("authorized-groups");
    configuration.setJobExecutorActivate(false);
    configuration.setAuthorizationEnabled(true);
    engine = configuration.buildProcessEngine();
    taskService = engine.getTaskService();
    authorizationService = engine.getAuthorizationService();
    identityService = engine.getIdentityService();

    for (String id : Arrays.asList("t-granted", "t-other", "t-revoked")) {
      Task task = taskService.newTask(id);
      taskService.saveTask(task);
    }
    // group "readers" may read all tasks, group "empty" has no authorization at all
    grant("readers", Resources.TASK, "*", Permissions.READ);
    // user "ann" may read one task directly
    Authorization userGrant = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
    userGrant.setUserId("ann");
    userGrant.setResource(Resources.TASK);
    userGrant.setResourceId("t-other");
    userGrant.addPermission(Permissions.READ);
    authorizationService.saveAuthorization(userGrant);
  }

  static void grant(String groupId, Resources resource, String resourceId, Permissions permission) {
    Authorization authorization = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
    authorization.setGroupId(groupId);
    authorization.setResource(resource);
    authorization.setResourceId(resourceId);
    authorization.addPermission(permission);
    authorizationService.saveAuthorization(authorization);
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  @After
  public void clearAuthentication() {
    identityService.clearAuthentication();
  }

  List<String> visibleTaskIds() {
    List<String> ids = new ArrayList<>();
    for (Task task : taskService.createTaskQuery().orderByTaskName().asc().list()) {
      ids.add(task.getId());
    }
    return ids;
  }

  @Test
  public void shouldUseTheGroupsThatHaveAuthorizations() {
    identityService.setAuthentication("bob", Arrays.asList("empty", "readers"));
    assertThat(visibleTaskIds()).containsExactly("t-granted", "t-other", "t-revoked");
  }

  @Test
  public void shouldIgnoreGroupsWithoutAuthorizations() {
    identityService.setAuthentication("ann", Arrays.asList("empty", "unknown"));
    assertThat(visibleTaskIds()).containsExactly("t-other");
  }

  @Test
  public void shouldNotReadTheGroupIdsOfAllAuthorizations() {
    identityService.setAuthentication("bob", Arrays.asList("empty", "readers"));
    TRACE.clear();
    visibleTaskIds();
    List<String> statements = TRACE.executed();
    assertThat(statements).as("statements %s", statements)
        .noneMatch(sql -> sql.contains("select distinct(a.group_id_) from act_ru_authorization a")
            && !sql.contains("where a.group_id_ in"));
    assertThat(StatementTrace.first(statements, "where a.group_id_ in")).as("statements %s", statements)
        .isGreaterThanOrEqualTo(0);
  }

  @Test
  public void shouldHandleMoreGroupsThanOneStatementTakes() {
    List<String> groups = new ArrayList<>();
    for (int i = 0; i < 1234; i++) {
      groups.add("g" + i);
    }
    groups.add(1100, "readers");
    identityService.setAuthentication("bob", groups);
    assertThat(visibleTaskIds()).containsExactly("t-granted", "t-other", "t-revoked");
    assertThat(authorizationService.isUserAuthorized("bob", groups, Permissions.READ, Resources.TASK, "t-granted"))
        .isTrue();
  }

  @Test
  public void shouldApplyRevokesOfTheUsersGroups() {
    Authorization revoke = authorizationService.createNewAuthorization(AUTH_TYPE_REVOKE);
    revoke.setGroupId("blocked");
    revoke.setResource(Resources.TASK);
    revoke.setResourceId("t-revoked");
    revoke.removePermission(Permissions.READ);
    authorizationService.saveAuthorization(revoke);
    try {
      List<String> groups = Arrays.asList("readers", "blocked");
      assertThat(authorizationService.isUserAuthorized("carl", groups, Permissions.READ, Resources.TASK, "t-revoked"))
          .isFalse();
      assertThat(authorizationService.isUserAuthorized("carl", groups, Permissions.READ, Resources.TASK, "t-granted"))
          .isTrue();
    } finally {
      authorizationService.deleteAuthorization(revoke.getId());
    }
  }
}
