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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.engine.rest.security.auth.impl.HttpBasicAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Authentication provider of the REST API that accepts a bearer token
 * (validated beforehand by the Spring Security resource server filter chain)
 * or, as before, basic authentication.
 * <p>
 * Register it as {@code authentication-provider} of the
 * {@code ProcessEngineAuthenticationFilter}. A browser session never
 * authenticates a REST API call: only a {@link JwtAuthenticationToken} that
 * was built from the {@code Authorization} header of the request counts.
 */
public class BearerTokenAuthenticationProvider extends HttpBasicAuthenticationProvider {

  protected static final String BEARER_PREFIX = "Bearer ";

  @Override
  public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {
    String header = request.getHeader("Authorization");
    if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
        return AuthenticationResult.unsuccessful();
      }
      return fromJwt(jwtAuthentication);
    }
    return super.extractAuthenticatedUser(request, engine);
  }

  /**
   * Maps a validated JWT to the engine identity: the user plus, when mapped,
   * groups and tenants.
   */
  public static AuthenticationResult fromJwt(JwtAuthenticationToken jwtAuthentication) {
    String userId = jwtAuthentication.getName();
    if (userId == null || userId.isBlank()) {
      return AuthenticationResult.unsuccessful();
    }
    OidcClaimMapper mapper = OidcClaimMapper.current();
    var claims = jwtAuthentication.getToken().getClaims();
    AuthenticationResult result = AuthenticationResult.successful(userId);
    String groupsClaim = mapper.getRestGroupsClaim();
    if (mapper.mapsGroups(groupsClaim)) {
      result.setGroups(mapper.groups(claims, groupsClaim));
    }
    if (mapper.mapsTenants()) {
      result.setTenants(mapper.tenants(claims));
    }
    return result;
  }

  @Override
  public void augmentResponseByAuthenticationChallenge(HttpServletResponse response, ProcessEngine engine) {
    super.augmentResponseByAuthenticationChallenge(response, engine);
    response.addHeader("WWW-Authenticate", "Bearer realm=\"" + engine.getName() + "\"");
  }

}
