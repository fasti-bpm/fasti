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

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * After a login started from the login page of the new web application, the
 * user returns to the page that was remembered by the
 * {@link SsoLoginSupportFilter}; otherwise to the request that required the
 * login, or to the root of the server.
 */
public class ReturnUrlAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    Object returnUrl = session != null ? session.getAttribute(SsoLoginSupportFilter.RETURN_URL_ATTRIBUTE) : null;
    if (returnUrl instanceof String url) {
      session.removeAttribute(SsoLoginSupportFilter.RETURN_URL_ATTRIBUTE);
      clearAuthenticationAttributes(request);
      // validated by SsoLoginSupportFilter; it already contains the context path
      response.sendRedirect(url);
      return;
    }
    super.onAuthenticationSuccess(request, response, authentication);
  }
}
