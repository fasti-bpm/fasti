/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.admin.plugin.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.camunda.bpm.admin.impl.plugin.resources.MetricsRestService;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * The aggregated metrics may only be read by administrators and by users who
 * are permitted to read system data, which is what the engine requires for
 * its diagnostics data.
 */
public class MetricsRestServiceAuthorizationTest extends AbstractAdminPluginTest {

  protected static final String USER_ID = "john";

  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected IdentityService identityService;
  protected AuthorizationService authorizationService;

  protected boolean authorizationEnabledInitialValue;
  protected List<String> adminUsersInitialValue;

  protected MetricsRestService resource;
  protected UriInfo uriInfo;

  @Before
  public void setUp() {
    processEngineConfiguration = (ProcessEngineConfigurationImpl) getProcessEngine().getProcessEngineConfiguration();
    identityService = getProcessEngine().getIdentityService();
    authorizationService = getProcessEngine().getAuthorizationService();

    authorizationEnabledInitialValue = processEngineConfiguration.isAuthorizationEnabled();
    adminUsersInitialValue = processEngineConfiguration.getAdminUsers();
    processEngineConfiguration.setAdminUsers(new ArrayList<>(adminUsersInitialValue));
    processEngineConfiguration.setAuthorizationEnabled(true);

    resource = new MetricsRestService(getProcessEngine().getName());

    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    queryParameters.add("subscriptionStartDate", "2024-01-01");
    queryParameters.add("groupBy", "year");
    uriInfo = Mockito.mock(UriInfo.class);
    Mockito.doReturn(queryParameters).when(uriInfo).getQueryParameters();
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    processEngineConfiguration.setAuthorizationEnabled(authorizationEnabledInitialValue);
    processEngineConfiguration.setAdminUsers(adminUsersInitialValue);

    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
  }

  @Test
  public void shouldRefuseUserWithoutPermissions() {
    // given
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertRefused();
  }

  @Test
  public void shouldRefuseUserWithAccessToAdminOnly() {
    // given a user who may open the Admin web application
    grant(Resources.APPLICATION, "admin", Permissions.ACCESS);
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertRefused();
  }

  @Test
  public void shouldRefuseUserWithOtherSystemPermissions() {
    // given
    grant(Resources.SYSTEM, ANY, SystemPermissions.SET, SystemPermissions.DELETE);
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertRefused();
  }

  @Test
  public void shouldPermitMemberOfAdminGroup() {
    // given
    identityService.setAuthentication(USER_ID, Arrays.asList(Groups.CAMUNDA_ADMIN));

    // then
    assertThat(resource.getAggregatedMetrics(uriInfo)).isEmpty();
  }

  @Test
  public void shouldPermitConfiguredAdminUser() {
    // given
    processEngineConfiguration.getAdminUsers().add(USER_ID);
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertThat(resource.getAggregatedMetrics(uriInfo)).isEmpty();
  }

  @Test
  public void shouldPermitUserWithReadPermissionOnSystem() {
    // given
    grant(Resources.SYSTEM, ANY, SystemPermissions.READ);
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertThat(resource.getAggregatedMetrics(uriInfo)).isEmpty();
  }

  @Test
  public void shouldPermitEveryUserWhenAuthorizationIsDisabled() {
    // given
    processEngineConfiguration.setAuthorizationEnabled(false);
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertThat(resource.getAggregatedMetrics(uriInfo)).isEmpty();
  }

  @Test
  public void shouldCheckPermissionBeforeParameters() {
    // given a request that is invalid as well
    Mockito.doReturn(new MultivaluedHashMap<String, String>()).when(uriInfo).getQueryParameters();
    identityService.setAuthentication(USER_ID, Collections.<String>emptyList());

    // then
    assertRefused();
  }

  protected void assertRefused() {
    assertThatThrownBy(() -> resource.getAggregatedMetrics(uriInfo))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(SystemPermissions.READ.getName())
        .hasMessageContaining(Resources.SYSTEM.resourceName());
  }

  protected void grant(Resource resource, String resourceId, Permission... permissions) {
    Authorization authorization = authorizationService.createNewAuthorization(AUTH_TYPE_GRANT);
    authorization.setUserId(USER_ID);
    authorization.setResource(resource);
    authorization.setResourceId(resourceId);
    for (Permission permission : permissions) {
      authorization.addPermission(permission);
    }
    authorizationService.saveAuthorization(authorization);
  }

}
