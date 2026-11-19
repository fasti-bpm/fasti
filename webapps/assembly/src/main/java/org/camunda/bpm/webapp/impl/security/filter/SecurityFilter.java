/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.webapp.impl.security.filter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.impl.util.IoUtil;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.camunda.bpm.webapp.impl.security.auth.Authentications;
import org.camunda.bpm.webapp.impl.security.filter.util.FilterRules;


/**
 * <p>Simple filter implementation which delegates to a list of {@link SecurityFilterRule FilterRules},
 * evaluating their {@link SecurityFilterRule#setAuthorized(org.camunda.bpm.webapp.impl.security.filter.AppRequest)} condition
 * for the given request.</p>
 *
 * <p>This filter must be configured using a init-param in the web.xml file. The parameter must be named
 * "configFile" and point to the configuration file located in the servlet context.</p>
 *
 * @author Daniel Meyer
 * @author nico.rehwaldt
 */
public class SecurityFilter implements Filter {

  public List<SecurityFilterRule> filterRules = new ArrayList<SecurityFilterRule>();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    doFilterSecure((HttpServletRequest) request, (HttpServletResponse) response, chain);
  }

  public void doFilterSecure(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

    String requestUri = getRequestUri(request);
    String requestPath = getRequestPath(request);

    Authorization authorization = authorize(request.getMethod(), requestUri, requestPath);

    // attach authorization headers
    // to response
    authorization.attachHeaders(response);

    if (authorization.isGranted()) {

      // if request is authorized
      chain.doFilter(request, response);
    } else
    if (authorization.isAuthenticated()) {
      String application = authorization.getApplication();

      if (application != null) {
        sendForbiddenApplicationAccess(application, request, response);
      } else {
        sendForbidden(request, response);
      }
    } else {
      sendUnauthorized(request, response);
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    ServletContext servletContext = filterConfig.getServletContext();

    String applicationPath = ServletContextUtil.getAppPath(servletContext);
    loadFilterRules(filterConfig, applicationPath);
  }

  @Override
  public void destroy() {

  }

  /**
   * Iterate over a number of filter rules and match them against
   * the specified request.
   *
   * @param request
   * @param filterRules
   *
   * @return the joined {@link AuthorizationStatus} for this request matched against all filter rules
   */
  public static Authorization authorize(String requestMethod, String requestUri, List<SecurityFilterRule> filterRules) {
    return FilterRules.authorize(requestMethod, requestUri, filterRules);
  }

  /**
   * <p>Authorizes a request for both of its representations: the request URI
   * as it was sent by the client and the path the container resolved it to.</p>
   *
   * <p>The container selects the servlet of a request by the decoded and
   * normalized path without path parameters, whereas the request URI is the
   * raw value of the request line. Both may differ (e.g.
   * <code>/api/engine;foo/engine/default</code> or
   * <code>/app/../api/engine/engine/default</code> are resolved to
   * <code>/api/engine/engine/default</code>), so a rule that protects a
   * resource must not be evaded by spelling its URI differently. Access is
   * only granted if it is granted for both representations.</p>
   *
   * @param requestMethod the HTTP method of the request
   * @param requestUri the request URI within the context, as sent by the client
   * @param requestPath the path within the context, as resolved by the container
   */
  protected Authorization authorize(String requestMethod, String requestUri, String requestPath) {
    Authorization authorization = authorize(requestMethod, requestUri, filterRules);

    if (authorization.isGranted() && requestPath != null && !requestPath.equals(requestUri)) {
      Authorization pathAuthorization = authorize(requestMethod, requestPath, filterRules);

      if (!pathAuthorization.isGranted()) {
        return pathAuthorization;
      }
    }

    return authorization;
  }

  protected void loadFilterRules(FilterConfig filterConfig,
                                 String applicationPath) throws ServletException {
    String configFileName = filterConfig.getInitParameter("configFile");
    InputStream configFileResource = filterConfig.getServletContext().getResourceAsStream(configFileName);
    if (configFileResource == null) {
      throw new ServletException("Could not read security filter config file '"+configFileName+"': no such resource in servlet context.");
    } else {
      try {
        filterRules = FilterRules.load(configFileResource, applicationPath);
      } catch (Exception e) {
        throw new RuntimeException("Exception while parsing '" + configFileName + "'", e);
      } finally {
        IoUtil.closeSilently(configFileResource);
      }
    }
  }

  protected void sendForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.sendError(403);
  }

  protected void sendUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.sendError(401);
  }

  protected void sendForbiddenApplicationAccess(String application, HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.sendError(403, "No access rights for " + application);
  }

  protected boolean isAuthenticated(HttpServletRequest request) {
    return Authentications.getCurrent() != null;
  }

  protected String getRequestUri(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    return request.getRequestURI().substring(contextPath.length());
  }

  /**
   * @return the path of the request within the context as it was resolved by
   *         the container to select the servlet, i.e. decoded, normalized
   *         and without path parameters
   */
  protected String getRequestPath(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    String pathInfo = request.getPathInfo();

    StringBuilder requestPath = new StringBuilder();
    if (servletPath != null) {
      requestPath.append(servletPath);
    }
    if (pathInfo != null) {
      requestPath.append(pathInfo);
    }
    return requestPath.toString();
  }
}
