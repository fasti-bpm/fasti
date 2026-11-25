/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class LoginRegistrationOnlyResolverTest {

  private static ClientRegistration registration(String id) {
    return ClientRegistration.withRegistrationId(id)
        .clientId(id + "-client")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("https://" + id + ".example/authorize")
        .tokenUri("https://" + id + ".example/token")
        .scope("openid")
        .build();
  }

  private final CamundaSpringSecurityOAuth2AutoConfiguration.LoginRegistrationOnlyResolver resolver =
      new CamundaSpringSecurityOAuth2AutoConfiguration.LoginRegistrationOnlyResolver(
          new DefaultOAuth2AuthorizationRequestResolver(
              new InMemoryClientRegistrationRepository(registration("idpa"), registration("idpb")),
              "/oauth2/authorization"),
          "idpa");

  private static MockHttpServletRequest request(String registrationId) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/" + registrationId);
    request.setServletPath("/oauth2/authorization/" + registrationId);
    return request;
  }

  @Test
  void shouldResolveTheLoginRegistration() {
    OAuth2AuthorizationRequest request = resolver.resolve(request("idpa"));

    assertThat(request).isNotNull();
    assertThat(request.getAuthorizationUri()).isEqualTo("https://idpa.example/authorize");
  }

  @Test
  void shouldRefuseAnotherRegistration() {
    assertThat(resolver.resolve(request("idpb"))).isNull();
    assertThat(resolver.resolve(request("idpb"), "idpb")).isNull();
  }

  @Test
  void shouldPostTheBackChannelLogoutToTheLocalConnector() {
    assertThat(CamundaSpringSecurityOAuth2AutoConfiguration.internalLogoutUri(new MockEnvironment()))
        .isEqualTo("http://localhost:8080/logout");
    assertThat(CamundaSpringSecurityOAuth2AutoConfiguration.internalLogoutUri(new MockEnvironment()
        .withProperty("server.port", "9443").withProperty("server.ssl.enabled", "true")))
        .isEqualTo("https://localhost:9443/logout");
    assertThat(CamundaSpringSecurityOAuth2AutoConfiguration.internalLogoutUri(new MockEnvironment()
        .withProperty("server.port", "0")))
        .isEqualTo("{baseUrl}/logout");
  }
}
