/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.rest;

import org.camunda.bpm.engine.rest.security.auth.RestApiAuthenticationCheck;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.context.ServletContextAware;

import jakarta.servlet.ServletContext;

/**
 * Logs a warning if the REST API is deployed without authentication, see
 * {@link RestApiAuthenticationCheck}.
 *
 * <p>The check runs once all singletons are instantiated: at this point the
 * web server is created and every filter of the application, no matter how it
 * is contributed (filter beans, registration beans, servlet context
 * initializers), is registered in the servlet context.</p>
 *
 * <p>The warning is switched off with
 * <code>camunda.bpm.rest-api.authentication-warning-enabled=false</code>.</p>
 */
public class RestApiAuthenticationWarning implements SmartInitializingSingleton, ServletContextAware {

  protected ServletContext servletContext;

  @Override
  public void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (servletContext != null) {
      RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);
    }
  }

}
