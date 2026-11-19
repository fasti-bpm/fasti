/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.rest.security.auth;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;

/**
 * <p>Checks on startup whether the REST API is deployed with authentication.</p>
 *
 * <p>Out of the box the REST API does not authenticate requests: unless a
 * {@link ProcessEngineAuthenticationFilter} (or another security filter) is
 * registered, every client that can reach the API may perform any operation
 * on the process engine, and the authorizations of the engine do not apply.
 * This default is kept for compatibility, but it must not go unnoticed, so a
 * warning is logged when no authentication is found.</p>
 *
 * <p>The check can only see the filters of the web application. If the API is
 * protected by other means (e.g. security constraints of the container, a
 * reverse proxy or a network policy), the warning can be switched off with the
 * context parameter {@value #WARNING_ENABLED_PARAM} or the system property
 * {@value #WARNING_ENABLED_SYSTEM_PROPERTY} set to <code>false</code>.</p>
 */
public class RestApiAuthenticationCheck {

  /** context parameter (init parameter of the servlet context) to switch the warning off */
  public static final String WARNING_ENABLED_PARAM = "rest-api-authentication-warning-enabled";

  /** system property to switch the warning off */
  public static final String WARNING_ENABLED_SYSTEM_PROPERTY = "camunda.bpm.rest-api.authentication-warning-enabled";

  /** name of the filter (chain) that Spring Security registers in a servlet context */
  protected static final String SPRING_SECURITY_FILTER_NAME = "springSecurityFilterChain";
  protected static final String SPRING_SECURITY_PACKAGE = "org.springframework.security.";

  protected static final Logger LOG = Logger.getLogger(RestApiAuthenticationCheck.class.getName());

  /**
   * Logs a warning if the REST API in the given servlet context is not
   * protected by an authentication filter.
   *
   * @return <code>true</code> if the warning was logged
   */
  public static boolean warnIfUnauthenticated(ServletContext servletContext) {
    if (!isWarningEnabled(servletContext) || isAuthenticationConfigured(servletContext)) {
      return false;
    }

    LOG.log(Level.WARNING, "The REST API of the process engine (context path ''{0}'') is deployed WITHOUT AUTHENTICATION: "
        + "no authentication filter ({1}) is registered. Every client that can reach the REST API has unrestricted "
        + "access to the process engine and authorization checks are not applied. Enable authentication for the REST API "
        + "before exposing it. If access is restricted by other means, this warning can be switched off by setting "
        + "the context parameter ''{2}'' or the system property ''{3}'' to false.",
        new Object[] {
            servletContext.getContextPath(),
            ProcessEngineAuthenticationFilter.class.getName(),
            WARNING_ENABLED_PARAM,
            WARNING_ENABLED_SYSTEM_PROPERTY });

    return true;
  }

  /**
   * @return <code>false</code> if the warning was switched off by the context
   *         parameter or the system property
   */
  public static boolean isWarningEnabled(ServletContext servletContext) {
    String contextParameter = servletContext.getInitParameter(WARNING_ENABLED_PARAM);
    String systemProperty = System.getProperty(WARNING_ENABLED_SYSTEM_PROPERTY);

    return !isFalse(contextParameter) && !isFalse(systemProperty);
  }

  /**
   * @return <code>true</code> if a filter that authenticates requests is
   *         registered in the servlet context, or if the filters of the
   *         servlet context cannot be inspected
   */
  public static boolean isAuthenticationConfigured(ServletContext servletContext) {
    Map<String, ? extends FilterRegistration> filterRegistrations;
    try {
      filterRegistrations = servletContext.getFilterRegistrations();
    } catch (RuntimeException e) {
      // the container does not allow to inspect the filters (e.g. because the caller was
      // not declared in the deployment descriptor): nothing can be said about them
      return true;
    }

    if (filterRegistrations != null) {
      for (FilterRegistration filterRegistration : filterRegistrations.values()) {
        if (isAuthenticationFilter(servletContext, filterRegistration)) {
          return true;
        }
      }
    }

    return false;
  }

  protected static boolean isAuthenticationFilter(ServletContext servletContext, FilterRegistration filterRegistration) {
    if (!isMapped(filterRegistration)) {
      // a filter without URL patterns or servlet names protects no request
      return false;
    }
    String filterName = filterRegistration.getName();
    String className = filterRegistration.getClassName();

    if (SPRING_SECURITY_FILTER_NAME.equals(filterName)
        || (className != null && className.startsWith(SPRING_SECURITY_PACKAGE))) {
      return true;
    }

    Class<?> filterClass = loadClass(servletContext, className);
    return filterClass != null && ProcessEngineAuthenticationFilter.class.isAssignableFrom(filterClass);
  }

  protected static boolean isMapped(FilterRegistration filterRegistration) {
    Collection<String> urlPatterns = filterRegistration.getUrlPatternMappings();
    Collection<String> servletNames = filterRegistration.getServletNameMappings();
    return (urlPatterns != null && !urlPatterns.isEmpty()) || (servletNames != null && !servletNames.isEmpty());
  }

  protected static Class<?> loadClass(ServletContext servletContext, String className) {
    if (className == null) {
      return null;
    }

    ClassLoader classLoader = null;
    try {
      classLoader = servletContext.getClassLoader();
    } catch (RuntimeException e) {
      // not accessible, fall back to the class loader of this class
    }
    if (classLoader == null) {
      classLoader = RestApiAuthenticationCheck.class.getClassLoader();
    }

    try {
      return Class.forName(className, false, classLoader);
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
  }

  protected static boolean isFalse(String value) {
    return value != null && "false".equalsIgnoreCase(value.trim());
  }

}
