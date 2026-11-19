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
