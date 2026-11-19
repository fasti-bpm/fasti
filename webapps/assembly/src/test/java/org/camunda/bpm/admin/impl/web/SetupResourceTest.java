/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.admin.impl.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GLOBAL;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_REVOKE;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.plugin.AdministratorAuthorizationPlugin;
import org.camunda.bpm.engine.rest.dto.identity.UserCredentialsDto;
import org.camunda.bpm.engine.rest.dto.identity.UserDto;
import org.camunda.bpm.engine.rest.dto.identity.UserProfileDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.camunda.commons.testing.ProcessEngineLoggingRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * The setup resource is reachable without authentication and creates a user
 * with full administrator rights. It must therefore refuse to work as soon as
 * an administrator is defined in any way the engine recognizes.
 */
public class SetupResourceTest {

  protected static final String ENGINE_NAME = "webapps-test-engine";
  protected static final String INITIAL_USER_ID = "initialAdmin";

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule("camunda-test-engine.cfg.xml");

  @Rule
  public ProcessEngineLoggingRule loggingRule = new ProcessEngineLoggingRule()
      .watch("org.camunda.bpm.webapp")
      .level(Level.WARN);

  protected ProcessEngine processEngine;
  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected IdentityService identityService;
  protected AuthorizationService authorizationService;

  /** the engine handed to the resource, may be replaced by a test */
  protected ProcessEngine lookedUpEngine;
  protected SetupResource resource;

  protected boolean authorizationEnabledInitialValue;
  protected List<String> adminUsersInitialValue;
  protected List<String> adminGroupsInitialValue;

  @Before
  public void setUp() {
    processEngine = processEngineRule.getProcessEngine();
    processEngineConfiguration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    identityService = processEngine.getIdentityService();
    authorizationService = processEngine.getAuthorizationService();

    authorizationEnabledInitialValue = processEngineConfiguration.isAuthorizationEnabled();
    adminUsersInitialValue = processEngineConfiguration.getAdminUsers();
    adminGroupsInitialValue = processEngineConfiguration.getAdminGroups();

    processEngineConfiguration.setAdminUsers(new ArrayList<>(adminUsersInitialValue));
    processEngineConfiguration.setAdminGroups(new ArrayList<>(adminGroupsInitialValue));
    processEngineConfiguration.setAuthorizationEnabled(true);

    lookedUpEngine = processEngine;
    resource = new SetupResource() {
      @Override
      protected ProcessEngine lookupProcessEngine(String engineName) {
        return ENGINE_NAME.equals(engineName) ? lookedUpEngine : null;
      }
    };
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    processEngineConfiguration.setAuthorizationEnabled(authorizationEnabledInitialValue);
    processEngineConfiguration.setAdminUsers(adminUsersInitialValue);
    processEngineConfiguration.setAdminGroups(adminGroupsInitialValue);

    for (User user : identityService.createUserQuery().list()) {
      identityService.deleteUser(user.getId());
    }
    for (Group group : identityService.createGroupQuery().list()) {
      identityService.deleteGroup(group.getId());
    }
    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
  }

  // setup available ////////////////////////////////////////////////////////////

  @Test
  public void shouldCreateInitialUserWhenNoAdministratorIsDefined() throws Exception {
    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
    for (Resource builtInResource : Resources.values()) {
      assertThat(authorizationService.createAuthorizationQuery()
          .groupIdIn(Groups.CAMUNDA_ADMIN)
          .resourceType(builtInResource)
          .resourceId(ANY)
          .count()).isEqualTo(1);
    }
  }

  @Test
  public void shouldCreateInitialUserWhenAuthorizationIsDisabledAndNoUserExists() throws Exception {
    // given
    processEngineConfiguration.setAuthorizationEnabled(false);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenOnlyUnprivilegedUsersExist() throws Exception {
    // given
    createUser("john");
    createGroup("sales");
    identityService.createMembership("john", "sales");
    grant(AUTH_TYPE_GRANT, "john", null, Permissions.READ, Resources.PROCESS_DEFINITION, Resources.TASK);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAdminGroupHasNoMember() throws Exception {
    // given the built-in admin group and its authorizations are left over,
    // but its last member was removed
    createGroup(Groups.CAMUNDA_ADMIN);
    grant(AUTH_TYPE_GRANT, null, Groups.CAMUNDA_ADMIN, Permissions.ALL, Resources.values());

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAllPermissionsAreGrantedOnSomeResourcesOnly() throws Exception {
    // given
    createUser("john");
    grant(AUTH_TYPE_GRANT, "john", null, Permissions.ALL, Resources.PROCESS_DEFINITION, Resources.TASK);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAllPermissionsAreRevoked() throws Exception {
    // given a revoke authorization does not make anybody an administrator
    createUser("john");
    grant(AUTH_TYPE_REVOKE, "john", null, Permissions.ALL, Resources.values());

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAllPermissionsAreGrantedGloballyAndNoUserExists() throws Exception {
    // given
    grant(AUTH_TYPE_GLOBAL, ANY, null, Permissions.ALL, Resources.values());

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenUserMayOnlyReadAndDeleteAuthorizations() throws Exception {
    // given a user who can neither create nor change an authorization
    createUser("john");
    Authorization authorization = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
    authorization.setUserId("john");
    authorization.setResource(Resources.AUTHORIZATION);
    authorization.setResourceId(ANY);
    authorization.addPermission(Permissions.READ);
    authorization.addPermission(Permissions.DELETE);
    authorizationService.saveAuthorization(authorization);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAllPermissionsAreGrantedOnSingleAuthorization() throws Exception {
    // given a user who can only change one existing authorization
    createUser("john");
    Authorization authorization = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
    authorization.setUserId("john");
    authorization.setResource(Resources.AUTHORIZATION);
    authorization.setResourceId("anAuthorizationId");
    authorization.addPermission(Permissions.ALL);
    authorizationService.saveAuthorization(authorization);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAuthorizationPermissionsAreRevoked() throws Exception {
    // given
    createUser("john");
    grant(AUTH_TYPE_REVOKE, "john", null, Permissions.ALL, Resources.AUTHORIZATION);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenAuthorizationsAreManagedGloballyAndNoUserExists() throws Exception {
    // given
    grant(AUTH_TYPE_GLOBAL, ANY, null, Permissions.ALL, Resources.AUTHORIZATION);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenInitialSetupIsNotConfigured() throws Exception {
    // given a servlet context without any setting
    resource.request = new MockHttpServletRequest(new MockServletContext());

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  @Test
  public void shouldCreateInitialUserWhenInitialSetupIsSwitchedOn() throws Exception {
    // given
    MockServletContext servletContext = new MockServletContext();
    ServletContextUtil.setInitialSetupEnabled(true, servletContext);
    resource.request = new MockHttpServletRequest(servletContext);

    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertInitialUserCreated();
  }

  // setup refused //////////////////////////////////////////////////////////////

  @Test
  public void shouldRefuseSetupWhenAdminGroupHasMember() {
    // given
    createUser("admin");
    createGroup(Groups.CAMUNDA_ADMIN);
    identityService.createMembership("admin", Groups.CAMUNDA_ADMIN);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenSecondInitialUserIsRequested() throws Exception {
    // given
    resource.createInitialUser(ENGINE_NAME, initialUser());

    UserDto secondUser = user("secondAdmin");

    // when
    try {
      resource.createInitialUser(ENGINE_NAME, secondUser);
      fail("expected exception");
    } catch (InvalidRequestException e) {
      // then
      assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN);
    }
    assertThat(identityService.createUserQuery().userId("secondAdmin").count()).isZero();
  }

  @Test
  public void shouldCreateOnlyOneInitialUserForConcurrentRequests() throws Exception {
    // given two requests that have both checked the availability of the setup
    // before any of them creates its user, unless the check and the creation
    // are performed as one step
    final CyclicBarrier availabilityChecked = new CyclicBarrier(2);
    final SetupResource concurrentResource = new SetupResource() {
      @Override
      protected ProcessEngine lookupProcessEngine(String engineName) {
        return processEngine;
      }

      @Override
      protected void ensureSetupAvailable(ProcessEngine processEngine) {
        super.ensureSetupAvailable(processEngine);
        try {
          availabilityChecked.await(2, TimeUnit.SECONDS);
        } catch (Exception e) {
          // the other request did not get this far in time, carry on alone
        }
      }
    };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Future<Status>> responses = new ArrayList<>();

    // when
    try {
      for (final String userId : new String[] { "firstAdmin", "secondAdmin" }) {
        responses.add(executor.submit(new Callable<Status>() {
          public Status call() throws Exception {
            try {
              concurrentResource.createInitialUser(ENGINE_NAME, user(userId));
              return Status.NO_CONTENT;
            } catch (InvalidRequestException e) {
              return e.getStatus();
            }
          }
        }));
      }

      // then
      List<Status> statuses = new ArrayList<>();
      for (Future<Status> response : responses) {
        statuses.add(response.get(30, TimeUnit.SECONDS));
      }
      assertThat(statuses).containsExactlyInAnyOrder(Status.NO_CONTENT, Status.FORBIDDEN);

    } finally {
      executor.shutdownNow();
    }

    assertThat(identityService.createUserQuery().count()).isEqualTo(1);
    assertThat(identityService.createUserQuery().memberOfGroup(Groups.CAMUNDA_ADMIN).count()).isEqualTo(1);
  }

  @Test
  public void shouldRefuseSetupWhenConfiguredAdminUserExists() {
    // given
    createUser("alice");
    processEngineConfiguration.getAdminUsers().add("alice");

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenConfiguredAdminUserDoesNotExistInIdentityService() {
    // given a configured administrator that may be supplied by an external
    // authentication mechanism
    processEngineConfiguration.getAdminUsers().add("alice");

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldNotLetAnybodyRegisterTheConfiguredAdminUser() {
    // given
    processEngineConfiguration.getAdminUsers().add("alice");

    // when
    try {
      resource.createInitialUser(ENGINE_NAME, user("alice"));
      fail("expected exception");
    } catch (InvalidRequestException e) {
      // then
      assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN);
    } catch (Exception e) {
      fail("unexpected exception", e);
    }
    assertThat(identityService.createUserQuery().userId("alice").count()).isZero();
  }

  @Test
  public void shouldRefuseSetupWhenConfiguredAdminGroupHasMember() {
    // given
    createUser("alice");
    createGroup("operators");
    identityService.createMembership("alice", "operators");
    processEngineConfiguration.getAdminGroups().add("operators");

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenConfiguredAdminGroupHasNoMember() {
    // given
    createGroup("operators");
    processEngineConfiguration.getAdminGroups().add("operators");

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenConfiguredAdminGroupDoesNotExistInIdentityService() {
    // given
    processEngineConfiguration.getAdminGroups().add("operators");

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenAdministratorAuthorizationPluginDefinesUser() {
    // given
    AdministratorAuthorizationPlugin plugin = new AdministratorAuthorizationPlugin();
    plugin.setAdministratorUserName("externalAdmin");
    plugin.postInit(processEngineConfiguration);
    plugin.postProcessEngineBuild(processEngine);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenAdministratorAuthorizationPluginDefinesGroup() {
    // given
    AdministratorAuthorizationPlugin plugin = new AdministratorAuthorizationPlugin();
    plugin.setAdministratorGroupName("externalAdmins");
    plugin.postInit(processEngineConfiguration);
    plugin.postProcessEngineBuild(processEngine);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenExistingUserIsGrantedAllPermissionsOnAllResources() {
    // given
    createUser("bob");
    grant(AUTH_TYPE_GRANT, "bob", null, Permissions.ALL, Resources.values());

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenUnknownUserIsGrantedAllPermissionsOnAllResources() {
    // given a privileged user that is not (yet) known to the identity service
    grant(AUTH_TYPE_GRANT, "bob", null, Permissions.ALL, Resources.values());

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenGroupWithMemberIsGrantedAllPermissionsOnAllResources() {
    // given
    createUser("bob");
    createGroup("operators");
    identityService.createMembership("bob", "operators");
    grant(AUTH_TYPE_GRANT, null, "operators", Permissions.ALL, Resources.values());

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenGroupWithoutMemberIsGrantedAllPermissionsOnAllResources() {
    // given a privileged group whose members may be supplied by an external
    // authentication mechanism
    grant(AUTH_TYPE_GRANT, null, "operators", Permissions.ALL, Resources.values());

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenUserIsGrantedAllPermissionsOnAuthorizationsOnly() {
    // given a user who can grant himself every other permission
    createUser("bob");
    grant(AUTH_TYPE_GRANT, "bob", null, Permissions.ALL, Resources.AUTHORIZATION);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenUnknownUserIsGrantedAllPermissionsOnAuthorizationsOnly() {
    // given
    grant(AUTH_TYPE_GRANT, "bob", null, Permissions.ALL, Resources.AUTHORIZATION);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenUserMayCreateAuthorizations() {
    // given
    createUser("bob");
    grant(AUTH_TYPE_GRANT, "bob", null, Permissions.CREATE, Resources.AUTHORIZATION);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenUserMayUpdateAuthorizations() {
    // given
    createUser("bob");
    grant(AUTH_TYPE_GRANT, "bob", null, Permissions.UPDATE, Resources.AUTHORIZATION);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenGroupIsGrantedAllPermissionsOnAuthorizationsOnly() {
    // given
    grant(AUTH_TYPE_GRANT, null, "operators", Permissions.ALL, Resources.AUTHORIZATION);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenAuthorizationsAreManagedGloballyAndUserExists() {
    // given every user can grant himself every other permission
    createUser("john");
    grant(AUTH_TYPE_GLOBAL, ANY, null, Permissions.ALL, Resources.AUTHORIZATION);

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenAuthorizationIsDisabledAndUserExists() {
    // given every user is unrestricted
    processEngineConfiguration.setAuthorizationEnabled(false);
    createUser("john");

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenAllPermissionsAreGrantedGloballyAndUserExists() {
    // given every user is unrestricted
    createUser("john");
    grant(AUTH_TYPE_GLOBAL, ANY, null, Permissions.ALL, Resources.values());

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenIdentityServiceIsReadOnly() {
    // given
    IdentityService readOnlyIdentityService = spy(identityService);
    doReturn(true).when(readOnlyIdentityService).isReadOnly();
    lookedUpEngine = spy(processEngine);
    doReturn(readOnlyIdentityService).when(lookedUpEngine).getIdentityService();

    // then
    assertSetupRefused();
  }

  @Test
  public void shouldRefuseSetupWhenInitialSetupIsSwitchedOff() {
    // given no administrator is defined, but the setup is switched off
    MockServletContext servletContext = new MockServletContext();
    ServletContextUtil.setInitialSetupEnabled(false, servletContext);
    resource.request = new MockHttpServletRequest(servletContext);

    // then
    assertSetupRefused();

    List<ILoggingEvent> log = loggingRule.getFilteredLog("setup");
    assertThat(log).hasSize(1);
    assertThat(log.get(0).getFormattedMessage()).contains("switched off");
  }

  @Test
  public void shouldRefuseSetupForAuthenticatedCallerWithoutPermissions() {
    // given an administrator exists and the caller may not see any user
    createUser("admin");
    createGroup(Groups.CAMUNDA_ADMIN);
    identityService.createMembership("admin", Groups.CAMUNDA_ADMIN);
    createUser("john");
    identityService.setAuthenticatedUserId("john");

    // then
    assertSetupRefused();
    assertThat(identityService.getCurrentAuthentication().getUserId()).isEqualTo("john");
  }

  @Test
  public void shouldLogRefusedSetup() {
    // given
    processEngineConfiguration.getAdminUsers().add("alice");

    // when
    assertSetupRefused();

    // then
    List<ILoggingEvent> log = loggingRule.getFilteredLog("setup");
    assertThat(log).hasSize(1);
    assertThat(log.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(log.get(0).getFormattedMessage())
        .contains(ENGINE_NAME)
        .contains("administrator user is defined in the process engine configuration");
  }

  @Test
  public void shouldNotLogSuccessfulSetupAsRefused() throws Exception {
    // when
    resource.createInitialUser(ENGINE_NAME, initialUser());

    // then
    assertThat(loggingRule.getFilteredLog("setup")).isEmpty();
  }

  @Test
  public void shouldRejectUnknownEngine() throws Exception {
    try {
      resource.createInitialUser("unknownEngine", initialUser());
      fail("expected exception");
    } catch (InvalidRequestException e) {
      assertThat(e.getStatus()).isEqualTo(Status.BAD_REQUEST);
    }
  }

  // helpers ////////////////////////////////////////////////////////////////////

  protected void assertSetupRefused() {
    try {
      resource.createInitialUser(ENGINE_NAME, initialUser());
      fail("expected the setup to be refused");
    } catch (InvalidRequestException e) {
      assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN);
      assertThat(e.getMessage()).isEqualTo("Setup action not available");
    } catch (Exception e) {
      fail("unexpected exception", e);
    }

    String authenticatedUserId = null;
    if (identityService.getCurrentAuthentication() != null) {
      authenticatedUserId = identityService.getCurrentAuthentication().getUserId();
    }
    identityService.clearAuthentication();
    try {
      assertThat(identityService.createUserQuery().userId(INITIAL_USER_ID).count()).isZero();
      assertThat(identityService.createUserQuery().memberOfGroup(Groups.CAMUNDA_ADMIN).userId(INITIAL_USER_ID).count()).isZero();
    } finally {
      if (authenticatedUserId != null) {
        identityService.setAuthenticatedUserId(authenticatedUserId);
      }
    }
  }

  protected void assertInitialUserCreated() {
    assertThat(identityService.createUserQuery().userId(INITIAL_USER_ID).count()).isEqualTo(1);
    assertThat(identityService.createUserQuery().memberOfGroup(Groups.CAMUNDA_ADMIN).userId(INITIAL_USER_ID).count())
        .isEqualTo(1);
  }

  protected UserDto initialUser() {
    return user(INITIAL_USER_ID);
  }

  protected UserDto user(String userId) {
    UserProfileDto profile = new UserProfileDto();
    profile.setId(userId);
    profile.setFirstName("Initial");
    profile.setLastName("Administrator");

    UserCredentialsDto credentials = new UserCredentialsDto();
    credentials.setPassword("aSecretPassword");

    UserDto user = new UserDto();
    user.setProfile(profile);
    user.setCredentials(credentials);
    return user;
  }

  protected void createUser(String userId) {
    User user = identityService.newUser(userId);
    user.setPassword("anotherSecretPassword");
    identityService.saveUser(user);
  }

  protected void createGroup(String groupId) {
    identityService.saveGroup(identityService.newGroup(groupId));
  }

  protected void grant(int authorizationType, String userId, String groupId, Permissions permission, Resource... resources) {
    for (Resource resource : resources) {
      Authorization authorization = authorizationService.createNewAuthorization(authorizationType);
      if (userId != null && authorizationType != AUTH_TYPE_GLOBAL) {
        authorization.setUserId(userId);
      }
      if (groupId != null) {
        authorization.setGroupId(groupId);
      }
      authorization.setResource(resource);
      authorization.setResourceId(ANY);
      if (authorizationType != AUTH_TYPE_REVOKE) {
        // a new revoke authorization does not revoke anything yet, i.e. it
        // still carries all permissions
        authorization.addPermission(permission);
      }
      authorizationService.saveAuthorization(authorization);
    }
  }

}
