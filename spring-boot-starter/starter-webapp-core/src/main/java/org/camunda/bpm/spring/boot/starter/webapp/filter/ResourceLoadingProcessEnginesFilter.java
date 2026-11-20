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
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import org.camunda.bpm.spring.boot.starter.property.WebappProperty;

import org.camunda.bpm.webapp.impl.engine.ProcessEnginesFilter;
import org.camunda.bpm.webapp.impl.filter.AbstractTemplateFilter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

public class ResourceLoadingProcessEnginesFilter extends ProcessEnginesFilter implements ResourceLoaderDependingFilter {

  protected ResourceLoader resourceLoader;
  protected WebappProperty webappProperty;

  @Override
  protected void applyFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    String contextPath = request.getContextPath();
    String requestUri = request.getRequestURI().substring(contextPath.length());
    String applicationPath = webappProperty.getApplicationPath();

    requestUri = trimChar(requestUri, '/');
    String appPath = trimChar(applicationPath, '/');
    if (requestUri.equals(appPath)) {
      // only redirect from index ("/") if index redirect is enabled
      if(!requestUri.isEmpty() || webappProperty.isIndexRedirectEnabled()) {
        // "/app/" picks the configured default app (the new web UI unless opted out)
        response.sendRedirect(String.format("%s%s/app/", contextPath, applicationPath));
        return;
      }
    }

    super.applyFilter(request, response, chain);
  }

  @Override
  protected String getWebResourceContents(String name) throws IOException {
    // the name must not leave the folder of the web jar on the class path
    if (name == null || name.contains("..") || !AbstractTemplateFilter.isTraversalFree(name)) {
      throw new FileNotFoundException("Web resource name is not allowed");
    }
    InputStream is = null;

    try {
      Resource resource = resourceLoader.getResource("classpath:"+webappProperty.getWebjarClasspath() + name);
      is = resource.getInputStream();

      BufferedReader reader = new BufferedReader(new InputStreamReader(is));

      StringWriter writer = new StringWriter();
      String line = null;

      while ((line = reader.readLine()) != null) {
        writer.write(line);
        writer.append("\n");
      }

      return writer.toString();
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
        }
      }
    }
  }

  /**
   * @return the resourceLoader
   */
  public ResourceLoader getResourceLoader() {
    return resourceLoader;
  }

  /**
   * @param resourceLoader
   *          the resourceLoader to set
   */
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  /**
   * @return the webappProperty
   */
  public WebappProperty getWebappProperty() {
        return webappProperty;
    }

  /**
   * @param webappProperty
   *          webappProperty to set
   */
  public void setWebappProperty(WebappProperty webappProperty) {
    this.webappProperty = webappProperty;
  }

  /**
   * @param input - String to trim
   * @param charachter - Char to trim
   * @return the trimmed String
   */
  protected String trimChar(String input, char charachter) {
    input = StringUtils.trimLeadingCharacter(input, charachter);
    input = StringUtils.trimTrailingCharacter(input, charachter);

    return input;
  }
}
