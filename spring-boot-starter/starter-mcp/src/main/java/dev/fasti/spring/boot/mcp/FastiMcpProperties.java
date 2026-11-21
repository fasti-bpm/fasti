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
package dev.fasti.spring.boot.mcp;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the MCP server ({@code fasti.mcp.*}). The server is off unless
 * {@code fasti.mcp.enabled=true}; tools that change data are off unless
 * {@code fasti.mcp.write-tools.enabled=true}.
 */
@ConfigurationProperties(FastiMcpProperties.PREFIX)
public class FastiMcpProperties {

  public static final String PREFIX = "fasti.mcp";

  /** Whether the MCP server is served. */
  private boolean enabled = false;

  /** Path of the MCP endpoint (Streamable HTTP), relative to the context path. */
  private String path = "/mcp";

  private final WriteTools writeTools = new WriteTools();

  /** Whether HTTP basic authentication against the users of the engine is accepted. */
  private boolean basicAuthEnabled = true;

  /**
   * Origins (scheme://host[:port], a port may be {@code *}) that may call the endpoint from a
   * browser. A request with an {@code Origin} header that is not listed is refused with 403,
   * which protects against DNS rebinding. Empty: every request with an {@code Origin} header is
   * refused; MCP clients outside a browser send none.
   */
  private List<String> allowedOrigins = new ArrayList<>();

  /**
   * Accepted values of the {@code Host} header (host[:port], a port may be {@code *}). Empty: the
   * header is not checked.
   */
  private List<String> allowedHosts = new ArrayList<>();

  /** Largest accepted request body. */
  private DataSize maxRequestSize = DataSize.ofKilobytes(256);

  /** Number of items a list tool returns when the call does not ask for fewer. */
  private int defaultResults = 25;

  /** Most items a list tool returns in one call. */
  private int maxResults = 100;

  /** Most variables returned for one process instance or task. */
  private int maxVariables = 100;

  /** Longest returned variable value (characters); longer values are cut and marked. */
  private int maxValueLength = 2000;

  /** Most variables a write tool accepts in one call. */
  private int maxInputVariables = 50;

  /** Longest accepted string value of a variable passed to a write tool (characters). */
  private int maxInputValueLength = 10000;

  /** Largest BPMN or DMN XML returned (characters); longer models are cut and marked. */
  private int maxXmlLength = 200_000;

  /** Longest returned stack trace or error details of a failed job (characters). */
  private int maxStacktraceLength = 8000;

  /**
   * Variable names that contain one of these words (case-insensitive) are returned masked, never
   * with their value.
   */
  private List<String> maskedVariableNames = new ArrayList<>(
      List.of("password", "secret", "token", "credential", "apikey", "api_key"));

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public WriteTools getWriteTools() {
    return writeTools;
  }

  public boolean isBasicAuthEnabled() {
    return basicAuthEnabled;
  }

  public void setBasicAuthEnabled(boolean basicAuthEnabled) {
    this.basicAuthEnabled = basicAuthEnabled;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public List<String> getAllowedHosts() {
    return allowedHosts;
  }

  public void setAllowedHosts(List<String> allowedHosts) {
    this.allowedHosts = allowedHosts;
  }

  public DataSize getMaxRequestSize() {
    return maxRequestSize;
  }

  public void setMaxRequestSize(DataSize maxRequestSize) {
    this.maxRequestSize = maxRequestSize;
  }

  public int getDefaultResults() {
    return defaultResults;
  }

  public void setDefaultResults(int defaultResults) {
    this.defaultResults = defaultResults;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public void setMaxResults(int maxResults) {
    this.maxResults = maxResults;
  }

  public int getMaxVariables() {
    return maxVariables;
  }

  public void setMaxVariables(int maxVariables) {
    this.maxVariables = maxVariables;
  }

  public int getMaxValueLength() {
    return maxValueLength;
  }

  public void setMaxValueLength(int maxValueLength) {
    this.maxValueLength = maxValueLength;
  }

  public int getMaxInputVariables() {
    return maxInputVariables;
  }

  public void setMaxInputVariables(int maxInputVariables) {
    this.maxInputVariables = maxInputVariables;
  }

  public int getMaxInputValueLength() {
    return maxInputValueLength;
  }

  public void setMaxInputValueLength(int maxInputValueLength) {
    this.maxInputValueLength = maxInputValueLength;
  }

  public int getMaxXmlLength() {
    return maxXmlLength;
  }

  public void setMaxXmlLength(int maxXmlLength) {
    this.maxXmlLength = maxXmlLength;
  }

  public int getMaxStacktraceLength() {
    return maxStacktraceLength;
  }

  public void setMaxStacktraceLength(int maxStacktraceLength) {
    this.maxStacktraceLength = maxStacktraceLength;
  }

  public List<String> getMaskedVariableNames() {
    return maskedVariableNames;
  }

  public void setMaskedVariableNames(List<String> maskedVariableNames) {
    this.maskedVariableNames = maskedVariableNames;
  }

  /** Tools that start, claim, complete or retry: they change data and are off by default. */
  public static class WriteTools {

    /** Whether tools that change data are offered. */
    private boolean enabled = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
