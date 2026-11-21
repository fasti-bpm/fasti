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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class BearerMcpAuthenticatorTest {

  static final JwtDecoder DECODER = token -> {
    if ("good".equals(token)) {
      return Jwt.withTokenValue(token).header("alg", "RS256").subject("subject-1").claim("preferred_username", "alice")
          .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
    if ("unreachable".equals(token)) {
      throw new JwtException("Couldn't retrieve remote JWK set");
    }
    throw new BadJwtException("invalid signature");
  };

  ProcessEngine engine() {
    ProcessEngine engine = mock(ProcessEngine.class, RETURNS_DEEP_STUBS);
    Group group = mock(Group.class);
    when(group.getId()).thenReturn("staff");
    when(engine.getIdentityService().createGroupQuery().groupMember("alice").list()).thenReturn(List.of(group));
    when(engine.getIdentityService().createTenantQuery().userMember("alice").includingGroupsOfUser(true).list())
        .thenReturn(List.<Tenant>of());
    return engine;
  }

  @Test
  void mapsAValidTokenToTheUserOfTheConfiguredClaim() {
    BearerMcpAuthenticator authenticator = new BearerMcpAuthenticator(DECODER, "preferred_username", null);

    McpIdentity identity = authenticator.authenticate("Bearer good", engine());

    assertThat(identity.userId()).isEqualTo("alice");
    assertThat(identity.groups()).containsExactly("staff");
  }

  @Test
  void refusesInvalidTokens() {
    BearerMcpAuthenticator authenticator = new BearerMcpAuthenticator(DECODER, "preferred_username", null);

    assertThat(authenticator.authenticate("Bearer forged", engine())).isNull();
    assertThat(authenticator.authenticate("Bearer ", engine())).isNull();
  }

  @Test
  void tellsAnUnreachableIdentityProviderApart() {
    BearerMcpAuthenticator authenticator = new BearerMcpAuthenticator(DECODER, "sub", null);

    assertThatThrownBy(() -> authenticator.authenticate("Bearer unreachable", engine()))
        .isInstanceOf(McpAuthenticationUnavailableException.class);
  }

  @Test
  void announcesTheResourceMetadata() {
    BearerMcpAuthenticator authenticator = new BearerMcpAuthenticator(DECODER, "sub",
        request -> "https://bpm.example/.well-known/oauth-protected-resource/mcp");

    assertThat(authenticator.supports("bearer abc")).isTrue();
    assertThat(authenticator.supports("Basic abc")).isFalse();
    assertThat(authenticator.challenge(null))
        .isEqualTo("Bearer realm=\"fasti-mcp\", resource_metadata=\"https://bpm.example/.well-known/oauth-protected-resource/mcp\"");
  }
}
