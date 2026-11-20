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
package org.camunda.bpm.webapp.impl.filter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A {@link Filter} implementation that can be used to realize basic templating.
 *
 * @author nico.rehwaldt
 */
public abstract class AbstractTemplateFilter implements Filter {

  private FilterConfig filterConfig;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    this.filterConfig = filterConfig;
  }

  @Override
  public void destroy() {
    filterConfig = null;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

    applyFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
  }

  /**
   * Apply the filter to the given request/response.
   *
   * This method must be provided by subclasses to perform actual work.
   *
   * @param request
   * @param response
   * @param chain
   *
   * @throws IOException
   * @throws ServletException
   */
  protected abstract void applyFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException;

  /**
   * Returns true if the given web resource exists.
   *
   * <p>The name is usually derived from the raw request URI, which is not normalized. A name that
   * could leave the folder it names (see {@link #isTraversalFree(String)}) is never looked up.</p>
   *
   * @param name
   * @return
   */
  protected boolean hasWebResource(String name) {
    if (name == null || name.contains("..") || !isTraversalFree(name)) {
      return false;
    }
    try {
      URL resource = filterConfig.getServletContext().getResource(name);
      return resource != null;
    } catch (MalformedURLException e) {
      return false;
    }
  }

  /**
   * Returns the string contents of a web resource with the given name.
   *
   * The resource must be static and text based.
   *
   * @param name the name of the resource
   *
   * @return the resource contents
   *
   * @throws IOException
   */
  protected String getWebResourceContents(String name) throws IOException {
    if (name == null || name.contains("..") || !isTraversalFree(name)) {
      throw new FileNotFoundException("Web resource name is not allowed");
    }

    InputStream is = null;

    try {
      is = filterConfig.getServletContext().getResourceAsStream(name);
      if (is == null) {
        throw new FileNotFoundException("Web resource not found");
      }

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
        try { is.close(); } catch (IOException e) { }
      }
    }
  }

  /**
   * Returns true if the given name of a web resource stays inside the folder it names: it
   * contains no <code>..</code> segment, no backslash (which some containers treat like a
   * slash), no percent-encoded characters (which could hide either of the two) and no control
   * characters.
   *
   * @param name the name of the resource, e.g. <code>/app/cockpit/index.html</code>
   * @return <code>true</code> if the name can be looked up safely
   */
  public static boolean isTraversalFree(String name) {
    if (name == null || name.contains("..")) {
      return false;
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '\\' || c == '%' || c < ' ' || c == 0x7F) {
        return false;
      }
    }
    return true;
  }
}
