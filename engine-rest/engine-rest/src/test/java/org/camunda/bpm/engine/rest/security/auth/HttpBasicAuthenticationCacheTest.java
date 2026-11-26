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
package org.camunda.bpm.engine.rest.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.rest.security.auth.impl.HttpBasicAuthenticationProvider;
import org.junit.Before;
import org.junit.Test;

/**
 * Fasti: the cache of successful HTTP basic authentications.
 */
public class HttpBasicAuthenticationCacheTest {

  protected ProcessEngine engine;
  protected IdentityService identityService;
  protected HttpBasicAuthenticationProvider provider;
  protected GroupQuery groupQuery;
  protected TenantQuery tenantQuery;

  @Before
  public void setUp() {
    engine = mock(ProcessEngine.class);
    identityService = mock(IdentityService.class);
    when(engine.getIdentityService()).thenReturn(identityService);
    when(engine.getName()).thenReturn("default");
    when(identityService.checkPassword("kermit", "secret")).thenReturn(true);

    // a user query that, like LDAP or a case-insensitive collation, ignores the case of the id
    User kermit = mock(User.class);
    when(kermit.getId()).thenReturn("kermit");
    when(identityService.createUserQuery()).thenAnswer(invocation -> {
      UserQuery userQuery = mock(UserQuery.class, RETURNS_SELF);
      when(userQuery.userId(anyString())).thenAnswer(call -> {
        when(userQuery.singleResult()).thenReturn("kermit".equalsIgnoreCase(call.getArgument(0)) ? kermit : null);
        return userQuery;
      });
      return userQuery;
    });

    groupQuery = mock(GroupQuery.class, RETURNS_SELF);
    Group group = mock(Group.class);
    when(group.getId()).thenReturn("sales");
    when(groupQuery.list()).thenReturn(Collections.singletonList(group));
    when(identityService.createGroupQuery()).thenReturn(groupQuery);

    tenantQuery = mock(TenantQuery.class, RETURNS_SELF);
    Tenant tenant = mock(Tenant.class);
    when(tenant.getId()).thenReturn("tenant-1");
    when(tenantQuery.list()).thenReturn(Collections.singletonList(tenant));
    when(identityService.createTenantQuery()).thenReturn(tenantQuery);

    provider = new HttpBasicAuthenticationProvider();
  }

  protected HttpServletRequest request(String user, String password) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    String credentials = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    when(request.getHeader("Authorization")).thenReturn("Basic " + credentials);
    return request;
  }

  @Test
  public void shouldCheckEveryRequestWithoutCache() {
    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);
    AuthenticationResult result = provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getGroups()).isNull();
    verify(identityService, times(2)).checkPassword("kermit", "secret");
  }

  @Test
  public void shouldCheckOnceWithinTheTimeToLive() {
    provider.setCacheTimeToLiveMillis(60_000);

    AuthenticationResult first = provider.extractAuthenticatedUser(request("kermit", "secret"), engine);
    AuthenticationResult second = provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    assertThat(second.isAuthenticated()).isTrue();
    assertThat(second.getAuthenticatedUser()).isEqualTo("kermit");
    assertThat(second.getGroups()).containsExactly("sales");
    assertThat(second.getTenants()).containsExactly("tenant-1");
    assertThat(first.getGroups()).isEqualTo(second.getGroups());
    verify(identityService, times(1)).checkPassword("kermit", "secret");
    verify(identityService, times(1)).createGroupQuery();
    verify(identityService, times(1)).createTenantQuery();
  }

  @Test
  public void shouldNotCacheFailedChecks() {
    provider.setCacheTimeToLiveMillis(60_000);

    AuthenticationResult first = provider.extractAuthenticatedUser(request("kermit", "wrong"), engine);
    AuthenticationResult second = provider.extractAuthenticatedUser(request("kermit", "wrong"), engine);

    assertThat(first.isAuthenticated()).isFalse();
    assertThat(second.isAuthenticated()).isFalse();
    verify(identityService, times(2)).checkPassword("kermit", "wrong");
  }

  @Test
  public void shouldCheckAnotherPasswordAgain() {
    provider.setCacheTimeToLiveMillis(60_000);
    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    AuthenticationResult result = provider.extractAuthenticatedUser(request("kermit", "other"), engine);

    assertThat(result.isAuthenticated()).isFalse();
    verify(identityService).checkPassword("kermit", "other");
  }

  @Test
  public void shouldCheckAgainAfterTheTimeToLive() throws InterruptedException {
    provider.setCacheTimeToLiveMillis(20);
    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);
    Thread.sleep(40);

    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    verify(identityService, times(2)).checkPassword("kermit", "secret");
  }

  @Test
  public void shouldCheckAgainAfterClear() {
    provider.setCacheTimeToLiveMillis(60_000);
    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    provider.clearCache();
    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    verify(identityService, times(2)).checkPassword("kermit", "secret");
  }

  // the stored user id instead of the name as typed

  @Test
  public void shouldAuthenticateACaseVariantAsTheStoredUserId() {
    when(identityService.checkPassword("KERMIT", "secret")).thenReturn(true);

    AuthenticationResult result = provider.extractAuthenticatedUser(request("KERMIT", "secret"), engine);

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getAuthenticatedUser()).isEqualTo("kermit");
  }

  @Test
  public void shouldAuthenticateCaseVariantsAsTheStoredUserIdThroughTheCache() {
    when(identityService.checkPassword("KERMIT", "secret")).thenReturn(true);
    when(identityService.checkPassword("Kermit", "secret")).thenReturn(true);
    provider.setCacheTimeToLiveMillis(60_000);

    AuthenticationResult first = provider.extractAuthenticatedUser(request("kermit", "secret"), engine);
    AuthenticationResult upper = provider.extractAuthenticatedUser(request("KERMIT", "secret"), engine);
    AuthenticationResult upperCached = provider.extractAuthenticatedUser(request("KERMIT", "secret"), engine);
    AuthenticationResult mixed = provider.extractAuthenticatedUser(request("Kermit", "secret"), engine);

    assertThat(first.getAuthenticatedUser()).isEqualTo("kermit");
    assertThat(upper.getAuthenticatedUser()).isEqualTo("kermit");
    assertThat(upperCached.getAuthenticatedUser()).isEqualTo("kermit");
    assertThat(mixed.getAuthenticatedUser()).isEqualTo("kermit");
    assertThat(upperCached.getGroups()).containsExactly("sales");
    // each spelling is checked once; memberships are always read for the stored id
    verify(identityService, times(1)).checkPassword("KERMIT", "secret");
    verify(identityService, times(1)).checkPassword("Kermit", "secret");
    verify(groupQuery, times(3)).groupMember("kermit");
    verify(groupQuery, never()).groupMember("KERMIT");
    verify(tenantQuery, times(3)).userMember("kermit");
  }

  @Test
  public void shouldNotCacheAFailedCheckOfACaseVariant() {
    provider.setCacheTimeToLiveMillis(60_000);
    provider.extractAuthenticatedUser(request("kermit", "secret"), engine);

    AuthenticationResult result = provider.extractAuthenticatedUser(request("KERMIT", "secret"), engine);

    assertThat(result.isAuthenticated()).isFalse();
    verify(identityService).checkPassword("KERMIT", "secret");
  }

  @Test
  public void shouldKeepTheTypedNameIfTheUserCannotBeRead() {
    when(identityService.checkPassword("gonzo", "secret")).thenReturn(true);

    AuthenticationResult result = provider.extractAuthenticatedUser(request("gonzo", "secret"), engine);

    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getAuthenticatedUser()).isEqualTo("gonzo");
  }

  @Test
  public void shouldRejectNegativeTimeToLive() {
    assertThatThrownBy(() -> provider.setCacheTimeToLiveMillis(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  // the filter: init-param and clearing after identity changes

  protected ProcessEngineAuthenticationFilter filter(String timeToLive) throws ServletException {
    FilterConfig config = mock(FilterConfig.class);
    when(config.getInitParameter(ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM))
        .thenReturn(HttpBasicAuthenticationProvider.class.getName());
    when(config.getInitParameter(ProcessEngineAuthenticationFilter.BASIC_AUTHENTICATION_CACHE_TTL_PARAM))
        .thenReturn(timeToLive);
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter() {
      @Override
      protected ProcessEngine getAddressedEngine(String engineName) {
        return engine;
      }
    };
    filter.init(config);
    return filter;
  }

  protected HttpServletRequest filterRequest(String method, String path) {
    HttpServletRequest request = request("kermit", "secret");
    when(request.getMethod()).thenReturn(method);
    when(request.getContextPath()).thenReturn("/engine-rest");
    when(request.getServletPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/engine-rest" + path);
    return request;
  }

  @Test
  public void shouldReadTheTimeToLiveFromTheInitParameter() throws Exception {
    ProcessEngineAuthenticationFilter filter = filter("30000");
    assertThat(((HttpBasicAuthenticationProvider) filter.authenticationProvider).getCacheTimeToLiveMillis())
        .isEqualTo(30_000);
    assertThatThrownBy(() -> filter("soon")).isInstanceOf(ServletException.class);
  }

  @Test
  public void shouldClearTheCacheAfterAChangeOfUsersGroupsOrTenants() throws Exception {
    ProcessEngineAuthenticationFilter filter = filter("60000");
    FilterChain chain = mock(FilterChain.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    filter.doFilter(filterRequest("GET", "/engine/default/task"), response, chain);
    filter.doFilter(filterRequest("GET", "/user/kermit/profile"), response, chain);
    verify(identityService, times(1)).checkPassword("kermit", "secret");

    filter.doFilter(filterRequest("PUT", "/engine/default/group/sales/members/kermit"), response, chain);
    filter.doFilter(filterRequest("POST", "/task"), response, chain);
    verify(identityService, times(2)).checkPassword("kermit", "secret");

    for (String path : Arrays.asList("/user/create", "/tenant/t1/user-members/kermit", "/identity/password-policy")) {
      filter.doFilter(filterRequest("POST", path), response, chain);
    }
    // the first is answered from the cache, each clears it
    verify(identityService, times(4)).checkPassword("kermit", "secret");
    verify(response, never()).setStatus(401);
    verify(identityService, times(7)).setAuthentication(anyString(), any(), any());
  }
}
