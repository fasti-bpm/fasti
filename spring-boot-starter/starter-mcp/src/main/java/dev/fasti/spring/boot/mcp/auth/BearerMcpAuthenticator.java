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

import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.BearerTokenAuthenticationProvider;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Bearer tokens (JWT access tokens of the OIDC identity provider), checked by the decoder of the
 * REST API (signature, algorithm, issuer, audience, expiry) and mapped to the engine identity
 * with the same claims ({@code fasti.security.oidc.rest.*}).
 */
public class BearerMcpAuthenticator implements McpAuthenticator {

  protected static final String PREFIX = "Bearer ";

  protected final JwtDecoder decoder;
  protected final JwtAuthenticationConverter converter;
  protected final Function<HttpServletRequest, String> resourceMetadataUrl;

  /**
   * @param decoder decoder that validates the token
   * @param userIdClaim claim that holds the user id
   * @param resourceMetadataUrl URL of the protected resource metadata (RFC 9728) for a request,
   *          announced in the challenge; {@code null} for none
   */
  public BearerMcpAuthenticator(JwtDecoder decoder, String userIdClaim,
                                Function<HttpServletRequest, String> resourceMetadataUrl) {
    this.decoder = decoder;
    this.converter = new JwtAuthenticationConverter();
    if (userIdClaim != null && !userIdClaim.isBlank()) {
      this.converter.setPrincipalClaimName(userIdClaim);
    }
    this.resourceMetadataUrl = resourceMetadataUrl;
  }

  @Override
  public boolean supports(String authorizationHeader) {
    return authorizationHeader != null
        && authorizationHeader.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
  }

  @Override
  public McpIdentity authenticate(String authorizationHeader, ProcessEngine engine) {
    String token = authorizationHeader.substring(PREFIX.length()).trim();
    if (token.isEmpty()) {
      return null;
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(token);
    } catch (JwtException e) {
      if (isUnavailable(e)) {
        throw new McpAuthenticationUnavailableException("The identity provider cannot be reached", e);
      }
      return null;
    } catch (RuntimeException e) {
      throw new McpAuthenticationUnavailableException("The identity provider cannot be reached", e);
    }
    AbstractAuthenticationToken authentication = converter.convert(jwt);
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
      return null;
    }
    AuthenticationResult result = BearerTokenAuthenticationProvider.fromJwt(jwtAuthentication);
    if (!result.isAuthenticated()) {
      return null;
    }
    return McpIdentity.resolve(engine.getIdentityService(), result.getAuthenticatedUser(), result.getGroups(),
        result.getTenants());
  }

  /** A failure to reach the identity provider, as opposed to an invalid token. */
  protected boolean isUnavailable(JwtException e) {
    return !(e instanceof org.springframework.security.oauth2.jwt.BadJwtException);
  }

  @Override
  public String challenge(HttpServletRequest request) {
    String metadata = resourceMetadataUrl == null ? null : resourceMetadataUrl.apply(request);
    return metadata == null
        ? "Bearer realm=\"fasti-mcp\""
        : "Bearer realm=\"fasti-mcp\", resource_metadata=\"" + metadata + "\"";
  }
}
