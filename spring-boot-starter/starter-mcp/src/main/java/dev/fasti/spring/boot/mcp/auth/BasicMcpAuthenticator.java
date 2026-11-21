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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;

/**
 * HTTP basic authentication against the users of the engine
 * ({@link IdentityService#checkPassword}, including its lockout after failed attempts), as the
 * REST API of Run checks it.
 */
public class BasicMcpAuthenticator implements McpAuthenticator {

  protected static final String PREFIX = "Basic ";

  @Override
  public boolean supports(String authorizationHeader) {
    return authorizationHeader != null
        && authorizationHeader.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
  }

  @Override
  public McpIdentity authenticate(String authorizationHeader, ProcessEngine engine) {
    String decoded;
    try {
      byte[] bytes = Base64.getDecoder().decode(authorizationHeader.substring(PREFIX.length()).trim());
      decoded = new String(bytes, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
    int colon = decoded.indexOf(':');
    if (colon <= 0) {
      return null;
    }
    String userId = decoded.substring(0, colon);
    String password = decoded.substring(colon + 1);
    IdentityService identityService = engine.getIdentityService();
    if (password.isEmpty() || !identityService.checkPassword(userId, password)) {
      return null;
    }
    return McpIdentity.resolve(identityService, userId, null, null);
  }

  @Override
  public String challenge(HttpServletRequest request) {
    return "Basic realm=\"fasti-mcp\", charset=\"UTF-8\"";
  }
}
