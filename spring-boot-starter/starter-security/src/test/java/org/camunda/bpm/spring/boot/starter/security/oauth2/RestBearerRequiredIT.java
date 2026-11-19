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
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Bearer mode without any engine authentication filter (a starter-only
 * application): the REST API must never be reachable anonymously.
 */
@TestPropertySource(locations = "/oauth2-mock.properties", properties = {
    "fasti.security.oidc.rest.bearer-enabled=true",
    "fasti.security.oidc.rest.issuer-uri=http://localhost/issuer1",
    "fasti.security.oidc.rest.audiences=fasti"
})
public class RestBearerRequiredIT extends AbstractSpringSecurityIT {

  @Autowired
  private TestRestTemplate restTemplate;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Before
  public void stubDecoder() {
    doThrow(new BadJwtException("invalid")).when(jwtDecoder).decode(anyString());
    Jwt jwt = Jwt.withTokenValue("good").header("alg", "RS256").subject("alice")
        .audience(List.of("fasti")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    doReturn(jwt).when(jwtDecoder).decode("good");
  }

  private HttpStatusCode get(String authorization) {
    HttpHeaders headers = new HttpHeaders();
    if (authorization != null) {
      headers.set(HttpHeaders.AUTHORIZATION, authorization);
    }
    return restTemplate.exchange(baseUrl + "/engine-rest/engine", HttpMethod.GET, new HttpEntity<>(headers), String.class)
        .getStatusCode();
  }

  @Test
  public void rejectsRequestWithoutCredentials() {
    assertThat(get(null).value()).isEqualTo(401);
  }

  @Test
  public void rejectsUncheckedBasicCredentials() {
    assertThat(get("Basic ZGVtbzpkZW1v").value()).isEqualTo(401);
  }

  @Test
  public void rejectsInvalidToken() {
    assertThat(get("Bearer bad").value()).isEqualTo(401);
  }

  @Test
  public void acceptsValidToken() {
    assertThat(get("Bearer good").value()).isEqualTo(200);
  }
}
