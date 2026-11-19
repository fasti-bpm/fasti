/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.util;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Answers requests without writing a response body:
 * <ul>
 *   <li><code>/no-body</code>: empty response
 *   <li><code>/new-session-id</code>: empty response, the id of the session
 *       is changed (as it happens when a user logs in)
 * </ul>
 */
public class SessionTestServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if ("/new-session-id".equals(request.getPathInfo())) {
      // a new session replaces the current one
      request.getSession().invalidate();
      request.getSession(true);
    }

    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

}
