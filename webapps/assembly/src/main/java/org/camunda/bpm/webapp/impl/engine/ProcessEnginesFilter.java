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
package org.camunda.bpm.webapp.impl.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.admin.Admin;
import org.camunda.bpm.admin.AdminRuntimeDelegate;
import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.CockpitRuntimeDelegate;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.util.WebApplicationUtil;
import org.camunda.bpm.tasklist.Tasklist;
import org.camunda.bpm.tasklist.TasklistRuntimeDelegate;
import org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider;
import org.camunda.bpm.webapp.impl.util.FormsIsolation;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;
import org.camunda.bpm.webapp.impl.filter.AbstractTemplateFilter;
import org.camunda.bpm.webapp.impl.security.InitialAdminSetup;
import org.camunda.bpm.webapp.impl.security.InitialSetupToken;
import org.camunda.bpm.webapp.impl.security.SecurityActions;
import org.camunda.bpm.webapp.impl.security.SecurityActions.SecurityAction;
import org.camunda.bpm.webapp.plugin.spi.AppPlugin;
import org.camunda.bpm.welcome.Welcome;
import org.camunda.bpm.welcome.WelcomeRuntimeDelegate;

/**
 *
 * @author nico.rehwaldt
 * @author Daniel Meyer
 * @author Roman Smirnov
 * @author Sebastian Stamm
 *
 */
public class ProcessEnginesFilter extends AbstractTemplateFilter {

  protected static final String COCKPIT_APP_NAME = "cockpit";
  protected static final String ADMIN_APP_NAME = "admin";
  protected static final String TASKLIST_APP_NAME = "tasklist";
  protected static final String WELCOME_APP_NAME = "welcome";
  /** the new web UI; it has a setup page of its own */
  protected static final String NEXT_APP_NAME = "next";

  /** the application that requests to <code>/</code> and <code>/app/</code> are sent to */
  protected static final String DEFAULT_APP = NEXT_APP_NAME;
  protected static final Set<String> DEFAULT_APP_CHOICES = Set.of(NEXT_APP_NAME, WELCOME_APP_NAME,
      TASKLIST_APP_NAME, COCKPIT_APP_NAME, ADMIN_APP_NAME);
  protected static final String INDEX_PAGE = "index.html";

  protected static final String SETUP_PAGE = "setup/";

  /**
   * Name of the filter init parameter that switches the initial setup of the
   * webapps (the creation of the first administrator by an unauthenticated
   * user) on or off. Allowed values are <code>true</code> (default) and
   * <code>false</code>. If set to <code>false</code>, users are never sent to
   * the setup page and the setup resource refuses every request, regardless
   * of whether an administrator is defined for the engine. This is meant for
   * installations whose administrators are not visible to the webapps, e.g.
   * because users and groups are supplied by an external authentication
   * mechanism.
   */
  public static final String INITIAL_SETUP_ENABLED_INIT_PARAM_NAME = "initialSetupEnabled";

  /**
   * Name of the filter init parameter that selects the web application that
   * requests to the root of the web applications (<code>/</code> and
   * <code>/app/</code>) are sent to. Allowed values are <code>next</code>
   * (default, the new web UI), <code>welcome</code> (the classic Welcome
   * application), <code>tasklist</code>, <code>cockpit</code> and
   * <code>admin</code>. Every application stays reachable at its own URL,
   * whatever is configured.
   */
  public static final String DEFAULT_APP_INIT_PARAM_NAME = "defaultApp";

  /**
   * Name of the filter init parameter that selects how the new web UI
   * isolates embedded HTML forms: <code>sandboxed</code> (default; the form
   * frame has an opaque origin and no session), <code>separate-origin</code>
   * (the form frame is served from {@link #FORMS_ORIGIN_INIT_PARAM_NAME}) or
   * <code>same-origin</code> (the form frame shares origin and session with
   * the web UI; scripts of forms can act with the session of the user).
   */
  public static final String FORMS_ISOLATION_INIT_PARAM_NAME = "formsIsolation";

  /**
   * Name of the filter init parameter with the origin the form frame is
   * served from in mode <code>separate-origin</code>, e.g.
   * <code>https://forms.example.com</code>.
   */
  public static final String FORMS_ORIGIN_INIT_PARAM_NAME = "formsOrigin";

  public static final String APP_ROOT_PLACEHOLDER = "$APP_ROOT";
  public static final String FORMS_ISOLATION_PLACEHOLDER = "$FORMS_ISOLATION";
  public static final String FORMS_ORIGIN_PLACEHOLDER = "$FORMS_ORIGIN";
  /** whether the classic web applications are served, for the links of the new web UI */
  public static final String CLASSIC_WEBAPPS_PLACEHOLDER = "$CLASSIC_WEBAPPS";
  public static final String BASE_PLACEHOLDER = "$BASE";

  public static final String PLUGIN_DEPENDENCIES_PLACEHOLDER = "$PLUGIN_DEPENDENCIES";
  public static final String PLUGIN_PACKAGES_PLACEHOLDER = "$PLUGIN_PACKAGES";
  public static final String CSP_NONCE_PLACEHOLDER = "$CSP_NONCE";

  public static Pattern APP_PREFIX_PATTERN = Pattern.compile("/app/(?:([\\w-]+?)/(?:(index\\.html|[\\w-]+)?/?([^?]*)?)?)?");

  protected final CockpitRuntimeDelegate cockpitRuntimeDelegate;
  protected final AdminRuntimeDelegate adminRuntimeDelegate;
  protected final TasklistRuntimeDelegate tasklistRuntimeDelegate;
  protected final WelcomeRuntimeDelegate welcomeRuntimeDelegate;

  // accepts two times the plugin name
  protected final String pluginPackageFormat;

  // accepts two times the plugin name
  protected final String pluginDependencyFormat;

  public ProcessEnginesFilter() {
    this.cockpitRuntimeDelegate = Cockpit.getRuntimeDelegate();
    this.adminRuntimeDelegate = Admin.getRuntimeDelegate();
    this.tasklistRuntimeDelegate = Tasklist.getRuntimeDelegate();
    this.welcomeRuntimeDelegate = Welcome.getRuntimeDelegate();
    this.pluginPackageFormat = "{ name: '%s-plugin-%s', " +
      "location: '%s%s/api/%s/plugin/%s/static/app', main: 'plugin.js' }";
    this.pluginDependencyFormat = "{ ngModuleName: '%s.plugin.%s', requirePackageName: '%s-plugin-%s' }";
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);

    String initialSetupEnabled = filterConfig.getInitParameter(INITIAL_SETUP_ENABLED_INIT_PARAM_NAME);
    if (initialSetupEnabled != null && !initialSetupEnabled.trim().isEmpty()) {
      initialSetupEnabled = initialSetupEnabled.trim();

      // a value that is misspelled must not be mistaken for any of the two
      if ("true".equalsIgnoreCase(initialSetupEnabled)) {
        ServletContextUtil.setInitialSetupEnabled(true, filterConfig.getServletContext());

      } else if ("false".equalsIgnoreCase(initialSetupEnabled)) {
        ServletContextUtil.setInitialSetupEnabled(false, filterConfig.getServletContext());

      } else {
        throw new IllegalWebAppConfigurationException("'" + INITIAL_SETUP_ENABLED_INIT_PARAM_NAME
            + "' must be either 'true' or 'false' but is '" + initialSetupEnabled + "'.");
      }
    }

    String defaultApp = filterConfig.getInitParameter(DEFAULT_APP_INIT_PARAM_NAME);
    if (defaultApp != null && !defaultApp.trim().isEmpty()) {
      ServletContextUtil.setDefaultApp(validDefaultApp(defaultApp), filterConfig.getServletContext());
    }

    String formsIsolation = filterConfig.getInitParameter(FORMS_ISOLATION_INIT_PARAM_NAME);
    String formsOrigin = filterConfig.getInitParameter(FORMS_ORIGIN_INIT_PARAM_NAME);
    if (!isBlank(formsIsolation) || !isBlank(formsOrigin)) {
      FormsIsolation isolation = FormsIsolation.parse(formsIsolation, FORMS_ISOLATION_INIT_PARAM_NAME);
      String origin = FormsIsolation.parseOrigin(formsOrigin, FORMS_ORIGIN_INIT_PARAM_NAME);
      FormsIsolation.check(isolation, origin, FORMS_ORIGIN_INIT_PARAM_NAME);
      ServletContextUtil.setFormsIsolation(isolation, origin, filterConfig.getServletContext());
    }
  }

  protected static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Checks the name of a default application; a misspelled name must not
   * silently fall back to another application.
   */
  public static String validDefaultApp(String defaultApp) {
    String name = defaultApp.trim().toLowerCase(Locale.ROOT);
    if (!DEFAULT_APP_CHOICES.contains(name)) {
      throw new IllegalWebAppConfigurationException("'" + DEFAULT_APP_INIT_PARAM_NAME
          + "' must be one of " + new TreeSet<>(DEFAULT_APP_CHOICES) + " but is '" + defaultApp + "'.");
    }
    return name;
  }

  /** The application that requests to the root of the web applications are sent to. */
  protected String getDefaultApp(ServletContext servletContext) {
    String configured = ServletContextUtil.getDefaultApp(servletContext);
    return configured != null ? configured : DEFAULT_APP;
  }

  @Override
  protected void applyFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

    String contextPath = request.getContextPath();
    String requestUri = request.getRequestURI().substring(contextPath.length());

    ServletContext servletContext = request.getServletContext();
    String applicationPath = ServletContextUtil.getAppPath(servletContext);

    int applicationPathLength = applicationPath.length();
    if (applicationPathLength > 0) {
      requestUri = requestUri.substring(applicationPathLength);
    }

    Matcher uriMatcher = APP_PREFIX_PATTERN.matcher(requestUri);

    if (uriMatcher.matches()) {
      String appName = uriMatcher.group(1);
      String engineName = uriMatcher.group(2);
      String pageUri = uriMatcher.group(3);

      // this happens on weblogic - /app/cockpit/index.html
      if (INDEX_PAGE.equals(engineName)) {
        engineName = null;
      }

      if (pageUri == null || pageUri.isEmpty() || SETUP_PAGE.equals(pageUri)) {
        serveIndexPage(appName, engineName, pageUri, applicationPath,
          contextPath, request, response);
        return;
      }

      if (INDEX_PAGE.equals(pageUri)) {
        response.sendRedirect(String.format("%s%s/app/%s/%s/", contextPath, applicationPath,
          appName, engineName));
        return;
      }

      if (pageUri.endsWith(".html")) {
        serveTemplate(requestUri, appName, pageUri, applicationPath, request, response, chain);
        return;
      }

    }

    chain.doFilter(request, response);
  }

  protected void serveIndexPage(String appName,
                                String engineName,
                                String pageUri,
                                String applicationPath,
                                String contextPath,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException, ServletException {

    // access to /
    if (appName == null) {

      // redirect to {defaultApp}/{defaultEngineName}
      response.sendRedirect(String.format("%s%s/app/%s/%s/", contextPath, applicationPath,
        getDefaultApp(request.getServletContext()), getDefaultEngineName()));
    } else

    // access to /app/
    // redirect to /app/{defaultEngineName}/
    if (engineName == null) {
      // redirect to {defaultApp}/{defaultEngineName}
      response.sendRedirect(String.format("%s%s/app/%s/%s/", contextPath, applicationPath,
        appName, getDefaultEngineName()));
    } else {

      // access to /app/{engineName}/
      boolean setupPage = SETUP_PAGE.equals(pageUri);

      if (ServletContextUtil.isInitialSetupEnabled(request.getServletContext()) && needsInitialUser(engineName)) {
        // print the one-time setup token to the server log, the setup page asks for it
        InitialSetupToken.announce(engineName);
        if (!setupPage || !NEXT_APP_NAME.equals(appName)) {
          // redirect to the setup page of the new web UI, which asks for the setup token; the
          // classic Admin has no field for it
          String setupApp = NEXT_APP_NAME;
          String setupPath = "%s%s/app/%s/%s/setup/#/setup";
          response.sendRedirect(String.format(setupPath, contextPath, applicationPath, setupApp, engineName));
        } else {
          // serve the index page as a setup page
          // setup will be handled by app
          serveIndexPage(appName, engineName, applicationPath, contextPath, request, response);
        }
      } else {
        if (!setupPage) {
          // correctly serving index page
          serveIndexPage(appName, engineName, applicationPath, contextPath, request, response);
        } else {
          response.sendRedirect(String.format("%s%s/app/%s/%s/", contextPath, applicationPath,
            appName, engineName));
        }
      }
    }
  }

  protected String getDefaultEngineName() {
    CockpitRuntimeDelegate runtimeDelegate = Cockpit.getRuntimeDelegate();

    Set<String> processEngineNames = runtimeDelegate.getProcessEngineNames();
    if(processEngineNames.isEmpty()) {
      throw new IllegalWebAppConfigurationException("No process engine found. camunda Webapp cannot work without a process engine. ");

    } else {
      ProcessEngine defaultProcessEngine = runtimeDelegate.getDefaultProcessEngine();
      if(defaultProcessEngine != null) {
        return defaultProcessEngine.getName();

      } else {
        return processEngineNames.iterator().next();

      }
    }
  }

  protected void serveTemplate(String requestUri,
                               String appName,
                               String pageUri,
                               String applicationPath,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               FilterChain chain) throws IOException, ServletException {

    // check if resource exists
    if (hasWebResource(requestUri)) {

      // if so, include it
      chain.doFilter(request, response);
    } else {
      // strip engine namespace and check if resource would exist
      String cleanAppUri = String.format("%s/app/%s/%s", applicationPath, appName, pageUri);

      if (hasWebResource(cleanAppUri)) {
        request.getRequestDispatcher(cleanAppUri).forward(request, response);
      } else {
        chain.doFilter(request, response);
      }
    }
  }

  protected boolean needsInitialUser(String engineName) throws IOException, ServletException {
    final ProcessEngine processEngine = Cockpit.getProcessEngine(engineName);
    if (processEngine == null) {
      return false;
    }

    // the setup page is only offered if the setup resource would accept the
    // request, i.e. no administrator is defined for the engine
    return SecurityActions.runWithoutAuthentication(new SecurityAction<Boolean>() {
      public Boolean execute() {
        return InitialAdminSetup.isAvailable(processEngine);
      }
    }, processEngine);

  }

  protected void serveIndexPage(String appName,
                                String engineName,
                                String applicationPath,
                                String contextPath,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
    setWebappInTelemetry(engineName, appName, request.getServletContext());
    String data = getWebResourceContents("/app/" + appName + "/index.html");
    // the nonce that was announced in the header of this very response
    final String cspNonce = (String) request.getAttribute(ContentSecurityPolicyProvider.ATTR_CSP_FILTER_NONCE);

    data = replacePlaceholder(data, appName, engineName, applicationPath, contextPath, cspNonce);
    data = replaceFormsPlaceholders(data, request.getServletContext());

    // the length is counted in UTF-8, so the writer must encode in UTF-8 too (the servlet
    // default would be ISO-8859-1, which also contradicts the meta charset of the page)
    response.setContentType("text/html;charset=UTF-8");
    response.setContentLength(data.getBytes(StandardCharsets.UTF_8).length);

    response.getWriter().append(data);
  }

  protected void setWebappInTelemetry(String engineName, String appName, ServletContext servletContext) {
    if (!ServletContextUtil.isTelemetryDataSentAlready(appName, engineName, servletContext) &&
        WebApplicationUtil.setWebapp(engineName, appName)) {
      ServletContextUtil.setTelemetryDataSent(appName, engineName, servletContext);
    }
  }

  protected String replacePlaceholder(String data,
                                      String appName,
                                      String engineName,
                                      String applicationPath,
                                      String contextPath,
                                      String cspNonce) {
    return data.replace(APP_ROOT_PLACEHOLDER, contextPath + applicationPath)
               .replace(BASE_PLACEHOLDER, String.format("%s%s/app/%s/%s/", contextPath,
                 applicationPath, appName, engineName))
               .replace(PLUGIN_PACKAGES_PLACEHOLDER, createPluginPackagesStr(appName,
                 applicationPath, contextPath))
               .replace(PLUGIN_DEPENDENCIES_PLACEHOLDER, createPluginDependenciesStr(appName))
               .replace(CSP_NONCE_PLACEHOLDER, cspNonce == null ? "" : String.format("nonce=\"%s\"", cspNonce));
  }

  /**
   * Tells the new web UI how embedded forms are isolated and whether the
   * classic web applications are served. Only the values validated at startup
   * are written; they contain no markup characters.
   */
  protected String replaceFormsPlaceholders(String data, ServletContext servletContext) {
    String origin = ServletContextUtil.getFormsOrigin(servletContext);
    return data.replace(FORMS_ISOLATION_PLACEHOLDER, ServletContextUtil.getFormsIsolation(servletContext).getValue())
               .replace(FORMS_ORIGIN_PLACEHOLDER, origin == null ? "" : origin)
               .replace(CLASSIC_WEBAPPS_PLACEHOLDER,
                   Boolean.toString(ServletContextUtil.isClassicWebappsEnabled(servletContext)));
  }

  protected <T extends AppPlugin> CharSequence createPluginPackagesStr(String appName,
                                                                       String applicationPath,
                                                                       String contextPath) {
    final List<T> plugins = getPlugins(appName);

    StringBuilder builder = new StringBuilder();

    for (T plugin : plugins) {
      if (builder.length() > 0) {
        builder.append(", ").append("\n");
      }

      String pluginId = plugin.getId();
      String definition = String.format(pluginPackageFormat, appName, pluginId, contextPath,
        applicationPath, appName, pluginId);

      builder.append(definition);
    }

    return "[" + builder.toString() + "]";
  }

  protected <T extends AppPlugin> CharSequence createPluginDependenciesStr(String appName) {
    final List<T> plugins = getPlugins(appName);

    StringBuilder builder = new StringBuilder();

    for (T plugin : plugins) {
      if (builder.length() > 0) {
        builder.append(", ").append("\n");
      }

      String pluginId = plugin.getId();
      String definition = String.format(pluginDependencyFormat, appName, pluginId, appName, pluginId);

      builder.append(definition);
    }

    return "[" + builder.toString() + "]";
  }

  @SuppressWarnings("unchecked")
  protected <T extends AppPlugin> List<T> getPlugins(String appName) {
    if (COCKPIT_APP_NAME.equals(appName)) {
      return (List<T>) cockpitRuntimeDelegate.getAppPluginRegistry().getPlugins();

    } else if (ADMIN_APP_NAME.equals(appName)) {
      return (List<T>) adminRuntimeDelegate.getAppPluginRegistry().getPlugins();

    } else if (TASKLIST_APP_NAME.equals(appName)) {
        return (List<T>) tasklistRuntimeDelegate.getAppPluginRegistry().getPlugins();

    } else if (WELCOME_APP_NAME.equals(appName)) {
      return (List<T>) welcomeRuntimeDelegate.getAppPluginRegistry().getPlugins();

    } else {
      return Collections.emptyList();

    }
  }

}
