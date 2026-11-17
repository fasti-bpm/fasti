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
package org.camunda.bpm.oidc.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.oidc.OidcSettings;
import org.camunda.bpm.webapp.impl.security.auth.AuthenticationUtil;
import org.camunda.bpm.webapp.impl.security.auth.Authentications;
import org.camunda.bpm.webapp.impl.security.auth.UserAuthentication;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;

public class OidcAuthenticationFilterTest {

  static ProcessEngine engine;

  @BeforeClass
  public static void startEngine() {
    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:oidc-auth-filter;DB_CLOSE_DELAY=-1");
    configuration.setProcessEngineName("default");
    configuration.setAuthorizationEnabled(true);
    configuration.setDbMetricsReporterActivate(false);
    engine = configuration.buildProcessEngine();
    Authorization grant = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setGroupId("sales");
    grant.setResource(Resources.APPLICATION);
    grant.setResourceId("tasklist");
    grant.addPermission(Permissions.ACCESS);
    engine.getAuthorizationService().saveAuthorization(grant);
  }

  @AfterClass
  public static void stopEngine() {
    engine.close();
  }

  static OidcAuthenticationFilter filter() {
    Properties p = new Properties();
    p.setProperty("fasti.security.oidc.client-id", "web");
    OidcAuthenticationFilter filter = new OidcAuthenticationFilter() {
      @Override
      protected ProcessEngine getAddressedEngine(String engineName) {
        return "default".equals(engineName) ? engine : null;
      }
    };
    filter.init(OidcSettings.of(p));
    return filter;
  }

  @Test
  public void signedInUserWithoutDatabaseEntryGetsGroupsTenantsAndApps() throws Exception {
    MockServletContext context = new MockServletContext();
    MockHttpSession session = new MockHttpSession(context);
    session.setAttribute(OidcLogin.SESSION_ATTRIBUTE,
        new OidcLogin("carol", "c1", null, "id-token", List.of("sales"), List.of("t1")));
    MockHttpServletRequest request = new MockHttpServletRequest(context, "GET", "/api/admin/auth/user/default");
    request.setSession(session);

    AtomicReference<Authentication> during = new AtomicReference<>();
    FilterChain chain = (req, resp) -> during.set(engine.getIdentityService().getCurrentAuthentication());
    filter().doFilter(request, new MockHttpServletResponse(), chain);

    // the engine identity of this very request
    assertThat(during.get().getUserId()).isEqualTo("carol");
    assertThat(during.get().getGroupIds()).containsExactly("sales");
    assertThat(during.get().getTenantIds()).containsExactly("t1");
    assertThat(engine.getIdentityService().getCurrentAuthentication()).isNull();

    Authentications authentications = AuthenticationUtil.getAuthsFromSession(session);
    UserAuthentication authentication = (UserAuthentication) authentications.getAuthenticationForProcessEngine("default");
    assertThat(authentication.getIdentityId()).isEqualTo("carol");
    assertThat(authentication.getAuthorizedApps()).containsExactlyInAnyOrder("welcome", "tasklist");

    // the authentication filter of the web applications does not drop the user it does not know
    AuthenticationUtil.updateCache(authentications, session, 1);
    assertThat(authentications.getAuthenticationForProcessEngine("default")).isNotNull();
  }

  @Test
  public void withoutLoginNothingHappens() throws Exception {
    MockServletContext context = new MockServletContext();
    MockHttpSession session = new MockHttpSession(context);
    MockHttpServletRequest request = new MockHttpServletRequest(context, "GET", "/api/admin/auth/user/default");
    request.setSession(session);
    AtomicReference<Boolean> called = new AtomicReference<>(false);
    filter().doFilter(request, new MockHttpServletResponse(), (req, resp) -> called.set(true));
    assertThat(called.get()).isTrue();
    assertThat(AuthenticationUtil.getAuthsFromSession(session).getAuthentications()).isEmpty();
  }
}
