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
package org.camunda.bpm.oidc.rest;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.engine.rest.security.auth.impl.HttpBasicAuthenticationProvider;
import org.camunda.bpm.oidc.ClaimRules;
import org.camunda.bpm.oidc.OidcSettings;

/**
 * Authenticates REST API requests with {@code Authorization: Bearer <JWT>}:
 * the user is the claim {@code rest.user-id-claim} (default {@code sub}),
 * groups and tenants come from the claims as configured. Basic authentication
 * keeps working unless {@code rest.basic-enabled=false}.
 */
public class OidcBearerAuthenticationProvider extends HttpBasicAuthenticationProvider {

  protected static final String BEARER_PREFIX = "Bearer ";

  protected final BearerTokenValidator validator;
  protected final ClaimRules claimRules;
  protected final String userIdClaim;
  protected final boolean basicEnabled;

  public OidcBearerAuthenticationProvider(OidcSettings settings) {
    this(BearerTokenValidator.of(settings), settings.claimRules(), settings.get("rest.user-id-claim", "sub"),
        settings.getBoolean("rest.basic-enabled", true));
  }

  public OidcBearerAuthenticationProvider(BearerTokenValidator validator, ClaimRules claimRules, String userIdClaim,
                                          boolean basicEnabled) {
    this.validator = validator;
    this.claimRules = claimRules;
    this.userIdClaim = userIdClaim;
    this.basicEnabled = basicEnabled;
  }

  public static boolean hasBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    return header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
  }

  @Override
  public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {
    if (hasBearerToken(request)) {
      return fromToken(request.getHeader("Authorization").substring(BEARER_PREFIX.length()).trim());
    }
    if (basicEnabled) {
      return super.extractAuthenticatedUser(request, engine);
    }
    return AuthenticationResult.unsuccessful();
  }

  /** The identity of a token, unsuccessful if the token is not valid. */
  public AuthenticationResult fromToken(String token) {
    Map<String, Object> claims = token.isEmpty() ? null : validator.validate(token);
    if (claims == null) {
      return AuthenticationResult.unsuccessful();
    }
    String userId = ClaimRules.string(claims, userIdClaim);
    if (userId == null) {
      return AuthenticationResult.unsuccessful();
    }
    AuthenticationResult result = AuthenticationResult.successful(userId);
    String groupsClaim = claimRules.getRestGroupsClaim();
    if (claimRules.mapsGroups(groupsClaim)) {
      result.setGroups(claimRules.groups(claims, groupsClaim));
    }
    if (claimRules.mapsTenants()) {
      result.setTenants(claimRules.tenants(claims));
    }
    return result;
  }

  @Override
  public void augmentResponseByAuthenticationChallenge(HttpServletResponse response, ProcessEngine engine) {
    if (basicEnabled && engine != null) {
      super.augmentResponseByAuthenticationChallenge(response, engine);
    }
    response.addHeader("WWW-Authenticate", "Bearer realm=\"" + (engine != null ? engine.getName() : "default") + "\"");
  }

  public boolean isBasicEnabled() {
    return basicEnabled;
  }
}
