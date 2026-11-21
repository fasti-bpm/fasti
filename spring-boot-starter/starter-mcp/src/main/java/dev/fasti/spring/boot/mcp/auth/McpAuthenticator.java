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

import jakarta.servlet.http.HttpServletRequest;

import org.camunda.bpm.engine.ProcessEngine;

/**
 * Checks one kind of credentials in the {@code Authorization} header of an MCP request.
 */
public interface McpAuthenticator {

  /** Whether the header carries credentials of the kind this authenticator checks. */
  boolean supports(String authorizationHeader);

  /**
   * The identity the credentials belong to, or {@code null} if they are not valid.
   *
   * @throws McpAuthenticationUnavailableException if the credentials cannot be checked right now
   */
  McpIdentity authenticate(String authorizationHeader, ProcessEngine engine);

  /** Value of the {@code WWW-Authenticate} header that asks for these credentials. */
  String challenge(HttpServletRequest request);
}
