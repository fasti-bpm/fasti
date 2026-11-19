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
package org.camunda.bpm.webapp.impl.util;

import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.webapp.impl.security.auth.AuthenticationFilter;
import org.camunda.bpm.webapp.impl.security.auth.UserAuthenticationResource;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Date;

/**
 * With Camunda Platform 7.13 we introduced the application path prefix /camunda to Spring Boot.
 * The application path is set in Spring Boot's servlet context and is consumed by filters and
 * servlets of the Camunda Platform Webapp. This util class holds the methods to get and set the
 * application path.
 */
public class ServletContextUtil {

  protected static final String APP_PATH_ATTR_NAME =
    "org.camunda.bpm.spring.boot.starter.webapp.applicationPath";

  protected static final String SUCCESSFUL_ET_ATTR_NAME =
    "org.camunda.bpm.webapp.telemetry.data.stored";

  protected static final String AUTH_CACHE_TTL_ATTR_NAME =
    "org.camunda.bpm.webapp.auth.cache.ttl";

  protected static final String INITIAL_SETUP_ENABLED_ATTR_NAME =
    "org.camunda.bpm.webapp.initial.setup.enabled";

  protected static final String DEFAULT_APP_ATTR_NAME =
    "org.camunda.bpm.webapp.default.app";

  protected static final String FORMS_ISOLATION_ATTR_NAME =
    "dev.fasti.webapp.forms.isolation";

  protected static final String FORMS_ORIGIN_ATTR_NAME =
    "dev.fasti.webapp.forms.origin";

  protected static final String CLASSIC_WEBAPPS_ATTR_NAME =
    "dev.fasti.webapp.classic.enabled";

  /**
   * Consumed by Camunda Platform CE & EE Webapp:
   * Retrieves the application path from Spring Boot's servlet context.
   *
   * @param servletContext that holds the application path
   * @return a non-empty <code>String</code> containing the application path or an empty
   * <code>String</code> when no application path was set.
   */
  public static String getAppPath(ServletContext servletContext) {
    String applicationPath = (String) servletContext.getAttribute(APP_PATH_ATTR_NAME);

    if (applicationPath == null) {
      return "";

    } else {
      return applicationPath;

    }
  }

  /**
   * Sets an application path into Spring Boot's servlet context.
   *
   * @param applicationPath to be set into Spring Boot's servlet context
   * @param servletContext of Spring Boot the application path should be set into
   */
  public static void setAppPath(String applicationPath, ServletContext servletContext) {
    servletContext.setAttribute(APP_PATH_ATTR_NAME, applicationPath);
  }

  /**
   * @return whether the web application has already successfully been sent to
   *         the engine as telemetry info or not.
   */
  public static boolean isTelemetryDataSentAlready(String webappName, String engineName, ServletContext servletContext) {
    return servletContext.getAttribute(buildTelemetrySentAttribute(webappName, engineName)) != null;
  }

  /**
   * Marks the web application as successfully sent to the engine as telemetry
   * info
   */
  public static void setTelemetryDataSent(String webappName, String engineName, ServletContext servletContext) {
    servletContext.setAttribute(buildTelemetrySentAttribute(webappName, engineName), true);
  }

  protected static String buildTelemetrySentAttribute(String webappName, String engineName) {
    return SUCCESSFUL_ET_ATTR_NAME + "." + webappName + "." + engineName;
  }

  /**
   * Sets {@param cacheTimeToLive} in the {@link AuthenticationFilter} to be used on initial login authentication.
   * See {@link AuthenticationFilter#doFilter(ServletRequest, ServletResponse, FilterChain)}
   */
  public static void setCacheTTLForLogin(long cacheTimeToLive, ServletContext servletContext) {
    servletContext.setAttribute(AUTH_CACHE_TTL_ATTR_NAME, cacheTimeToLive);
  }

  /**
   * Returns {@code authCacheValidationTime} from servlet context to be used on initial login authentication.
   * See {@link UserAuthenticationResource#doLogin(String, String, String, String)}
   */
  public static Date getAuthCacheValidationTime(ServletContext servletContext) {
    Long cacheTimeToLive = (Long) servletContext.getAttribute(AUTH_CACHE_TTL_ATTR_NAME);

    if (cacheTimeToLive != null) {
      return new Date(ClockUtil.getCurrentTime().getTime() + cacheTimeToLive);

    } else {
      return null;

    }
  }

  /**
   * Switches the initial setup of the webapps, i.e. the creation of the first
   * administrator by an unauthenticated user, on or off for the given servlet
   * context. See the init parameter
   * {@link org.camunda.bpm.webapp.impl.engine.ProcessEnginesFilter#INITIAL_SETUP_ENABLED_INIT_PARAM_NAME}
   * of the engines filter.
   */
  public static void setInitialSetupEnabled(boolean initialSetupEnabled, ServletContext servletContext) {
    servletContext.setAttribute(INITIAL_SETUP_ENABLED_ATTR_NAME, initialSetupEnabled);
  }

  /**
   * @return <code>false</code> if the initial setup of the webapps has been
   *         switched off for the given servlet context, <code>true</code> if
   *         it is switched on or nothing has been configured.
   */
  public static boolean isInitialSetupEnabled(ServletContext servletContext) {
    return !Boolean.FALSE.equals(servletContext.getAttribute(INITIAL_SETUP_ENABLED_ATTR_NAME));
  }

  /**
   * Sets the web application that requests to the root of the web
   * applications (<code>/</code> and <code>/app/</code>) are sent to. See the
   * init parameter
   * {@link org.camunda.bpm.webapp.impl.engine.ProcessEnginesFilter#DEFAULT_APP_INIT_PARAM_NAME}
   * of the engines filter.
   */
  public static void setDefaultApp(String defaultApp, ServletContext servletContext) {
    servletContext.setAttribute(DEFAULT_APP_ATTR_NAME, defaultApp);
  }

  /**
   * @return the web application that requests to the root of the web
   *         applications are sent to, or <code>null</code> if nothing has
   *         been configured for the given servlet context.
   */
  public static String getDefaultApp(ServletContext servletContext) {
    return (String) servletContext.getAttribute(DEFAULT_APP_ATTR_NAME);
  }

  /**
   * Sets how embedded forms are isolated from the new web UI, and the origin
   * the form frame is served from in mode
   * {@link FormsIsolation#SEPARATE_ORIGIN} (<code>null</code> otherwise).
   */
  public static void setFormsIsolation(FormsIsolation isolation, String origin, ServletContext servletContext) {
    servletContext.setAttribute(FORMS_ISOLATION_ATTR_NAME, isolation);
    servletContext.setAttribute(FORMS_ORIGIN_ATTR_NAME, origin);
  }

  /**
   * @return how embedded forms are isolated, {@link FormsIsolation#DEFAULT}
   *         if nothing has been configured for the given servlet context
   */
  public static FormsIsolation getFormsIsolation(ServletContext servletContext) {
    Object isolation = servletContext.getAttribute(FORMS_ISOLATION_ATTR_NAME);
    return isolation instanceof FormsIsolation ? (FormsIsolation) isolation : FormsIsolation.DEFAULT;
  }

  /**
   * @return the origin the form frame is served from in mode
   *         {@link FormsIsolation#SEPARATE_ORIGIN}, otherwise <code>null</code>
   */
  public static String getFormsOrigin(ServletContext servletContext) {
    if (getFormsIsolation(servletContext) != FormsIsolation.SEPARATE_ORIGIN) {
      return null;
    }
    return (String) servletContext.getAttribute(FORMS_ORIGIN_ATTR_NAME);
  }

  /**
   * Sets whether the classic web applications (Cockpit, Tasklist, Admin and
   * Welcome) are served. The new web UI hides its links to them if not.
   */
  public static void setClassicWebappsEnabled(boolean enabled, ServletContext servletContext) {
    servletContext.setAttribute(CLASSIC_WEBAPPS_ATTR_NAME, enabled);
  }

  /**
   * @return whether the classic web applications are served; <code>true</code>
   *         if nothing has been configured for the given servlet context
   */
  public static boolean isClassicWebappsEnabled(ServletContext servletContext) {
    return !Boolean.FALSE.equals(servletContext.getAttribute(CLASSIC_WEBAPPS_ATTR_NAME));
  }

}
