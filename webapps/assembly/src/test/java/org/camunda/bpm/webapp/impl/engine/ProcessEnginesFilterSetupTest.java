/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.impl.DefaultCockpitRuntimeDelegate;
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
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The filter sends users to the setup page as long as the engine needs an
 * initial user. It has to take the same decision as the setup resource.
 */
public class ProcessEnginesFilterSetupTest {

  protected static final String ENGINE_NAME = "webapps-test-engine";

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule("camunda-test-engine.cfg.xml");

  protected ProcessEngine processEngine;
  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected IdentityService identityService;
  protected AuthorizationService authorizationService;

  /** the engine handed to the filter, may be replaced by a test */
  protected ProcessEngine lookedUpEngine;
  protected ProcessEnginesFilter filter;
  protected MockServletContext servletContext;

  /** the apps for which the filter served the index page */
  protected List<String> servedIndexPages;

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
    Cockpit.setCockpitRuntimeDelegate(new DefaultCockpitRuntimeDelegate() {
      @Override
      public ProcessEngine getProcessEngine(String processEngineName) {
        return ENGINE_NAME.equals(processEngineName) ? lookedUpEngine : null;
      }
    });

    servletContext = new MockServletContext();
    servedIndexPages = new ArrayList<>();
    filter = new ProcessEnginesFilter() {
      @Override
      protected void serveIndexPage(String appName, String engineName, String applicationPath, String contextPath,
          HttpServletRequest request, HttpServletResponse response) throws IOException {
        // the web resources are not available in this test
        servedIndexPages.add(appName);
      }
    };
  }

  @After
  public void tearDown() {
    Cockpit.setCockpitRuntimeDelegate(null);

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

  @Test
  public void shouldNeedInitialUserWhenNoAdministratorIsDefined() throws Exception {
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isTrue();
  }

  @Test
  public void shouldNeedInitialUserWhenOnlyUnprivilegedUsersExist() throws Exception {
    // given
    createUser("john");

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isTrue();
  }

  @Test
  public void shouldNotNeedInitialUserForUnknownEngine() throws Exception {
    assertThat(filter.needsInitialUser("unknownEngine")).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenAdminGroupHasMember() throws Exception {
    // given
    createUser("admin");
    identityService.saveGroup(identityService.newGroup(Groups.CAMUNDA_ADMIN));
    identityService.createMembership("admin", Groups.CAMUNDA_ADMIN);

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenAdminUserIsConfigured() throws Exception {
    // given
    processEngineConfiguration.getAdminUsers().add("alice");

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenAdminGroupIsConfigured() throws Exception {
    // given
    processEngineConfiguration.getAdminGroups().add("operators");

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenUserIsGrantedAllPermissionsOnAllResources() throws Exception {
    // given
    for (Resource resource : Resources.values()) {
      Authorization authorization = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
      authorization.setUserId("bob");
      authorization.setResource(resource);
      authorization.setResourceId(ANY);
      authorization.addPermission(Permissions.ALL);
      authorizationService.saveAuthorization(authorization);
    }

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenUserIsGrantedAllPermissionsOnAuthorizationsOnly() throws Exception {
    // given
    Authorization authorization = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
    authorization.setUserId("bob");
    authorization.setResource(Resources.AUTHORIZATION);
    authorization.setResourceId(ANY);
    authorization.addPermission(Permissions.ALL);
    authorizationService.saveAuthorization(authorization);

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenAuthorizationIsDisabledAndUserExists() throws Exception {
    // given
    processEngineConfiguration.setAuthorizationEnabled(false);
    createUser("john");

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldNotNeedInitialUserWhenIdentityServiceIsReadOnly() throws Exception {
    // given
    IdentityService readOnlyIdentityService = spy(identityService);
    doReturn(true).when(readOnlyIdentityService).isReadOnly();
    lookedUpEngine = spy(processEngine);
    doReturn(readOnlyIdentityService).when(lookedUpEngine).getIdentityService();

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
  }

  @Test
  public void shouldDecideIndependentlyOfAuthenticatedUser() throws Exception {
    // given an authenticated user who may not see the administrator
    createUser("admin");
    identityService.saveGroup(identityService.newGroup(Groups.CAMUNDA_ADMIN));
    identityService.createMembership("admin", Groups.CAMUNDA_ADMIN);
    createUser("john");
    identityService.setAuthenticatedUserId("john");

    // then
    assertThat(filter.needsInitialUser(ENGINE_NAME)).isFalse();
    assertThat(identityService.getCurrentAuthentication().getUserId()).isEqualTo("john");
  }

  // setup page ////////////////////////////////////////////////////////////////

  @Test
  public void shouldRedirectToSetupPageWhenInitialUserIsNeeded() throws Exception {
    // given
    initFilter(null);

    // when
    MockHttpServletResponse response = request("/app/tasklist/" + ENGINE_NAME + "/");

    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/app/admin/" + ENGINE_NAME + "/setup/#/setup");
    assertThat(servedIndexPages).isEmpty();
  }

  @Test
  public void shouldServeSetupPageWhenInitialUserIsNeeded() throws Exception {
    // given
    initFilter("true");

    // when
    MockHttpServletResponse response = request("/app/admin/" + ENGINE_NAME + "/setup/");

    // then
    assertThat(response.getRedirectedUrl()).isNull();
    assertThat(servedIndexPages).containsExactly("admin");
  }

  @Test
  public void shouldNotRedirectToSetupPageWhenInitialSetupIsSwitchedOff() throws Exception {
    // given no administrator is defined, but the setup is switched off
    initFilter("false");

    // when
    MockHttpServletResponse response = request("/app/tasklist/" + ENGINE_NAME + "/");

    // then
    assertThat(response.getRedirectedUrl()).isNull();
    assertThat(servedIndexPages).containsExactly("tasklist");
  }

  @Test
  public void shouldNotServeSetupPageWhenInitialSetupIsSwitchedOff() throws Exception {
    // given
    initFilter(" FALSE ");

    // when
    MockHttpServletResponse response = request("/app/admin/" + ENGINE_NAME + "/setup/");

    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/app/admin/" + ENGINE_NAME + "/");
    assertThat(servedIndexPages).isEmpty();
  }

  @Test
  public void shouldPublishInitialSetupSettingToServletContext() throws Exception {
    // given
    assertThat(ServletContextUtil.isInitialSetupEnabled(servletContext)).isTrue();

    // when
    initFilter("false");

    // then
    assertThat(ServletContextUtil.isInitialSetupEnabled(servletContext)).isFalse();
  }

  @Test
  public void shouldKeepInitialSetupSettingOfServletContextWhenParameterIsMissing() throws Exception {
    // given the setup was switched off by other means, e.g. the Spring Boot starter
    ServletContextUtil.setInitialSetupEnabled(false, servletContext);

    // when
    initFilter(null);

    // then
    assertThat(ServletContextUtil.isInitialSetupEnabled(servletContext)).isFalse();
    assertThat(request("/app/tasklist/" + ENGINE_NAME + "/").getRedirectedUrl()).isNull();
  }

  @Test
  public void shouldRejectInvalidInitialSetupParameter() {
    assertThatThrownBy(() -> initFilter("flase"))
        .isInstanceOf(IllegalWebAppConfigurationException.class)
        .hasMessageContaining("initialSetupEnabled")
        .hasMessageContaining("flase");
  }

  protected void initFilter(String initialSetupEnabled) throws ServletException {
    MockFilterConfig filterConfig = new MockFilterConfig(servletContext);
    if (initialSetupEnabled != null) {
      filterConfig.addInitParameter(ProcessEnginesFilter.INITIAL_SETUP_ENABLED_INIT_PARAM_NAME, initialSetupEnabled);
    }
    filter.init(filterConfig);
  }

  protected MockHttpServletResponse request(String requestUri) throws IOException, ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", requestUri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  protected void createUser(String userId) {
    User user = identityService.newUser(userId);
    user.setPassword("aSecretPassword");
    identityService.saveUser(user);
  }

}
