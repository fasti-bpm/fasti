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
package org.camunda.bpm.oidc;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Settings of single sign-on in a servlet container, read from a properties
 * file. The keys follow the {@code fasti.security.oidc.*} settings of Run and
 * Spring Boot where they mean the same; see {@code fasti-oidc.properties} in
 * the configuration directory of the distribution.
 * <p>
 * The file is looked up, in this order, at the path given by the init-param
 * {@code config-file} of a filter, the system property
 * {@code fasti.oidc.config}, the environment variable {@code FASTI_OIDC_CONFIG},
 * {@code ${catalina.base}/conf/fasti-oidc.properties} (Tomcat) and
 * {@code ${jboss.server.config.dir}/fasti-oidc.properties} (WildFly). Without a
 * file nothing changes: users sign in with a password as before.
 * <p>
 * Values may refer to system properties or environment variables as
 * {@code ${NAME}} or {@code ${NAME:default}}, e.g. for the client secret.
 */
public class OidcSettings {

  private static final Logger LOG = Logger.getLogger(OidcSettings.class.getName());

  public static final String PREFIX = "fasti.security.oidc.";
  public static final String CONFIG_FILE_PARAM = "config-file";
  public static final String CONFIG_FILE_PROPERTY = "fasti.oidc.config";
  public static final String CONFIG_FILE_ENV = "FASTI_OIDC_CONFIG";
  public static final String FILE_NAME = "fasti-oidc.properties";

  protected static final Map<String, OidcSettings> CACHE = new ConcurrentHashMap<>();
  protected static final OidcSettings DISABLED = new OidcSettings(new Properties(), null, System::getenv);

  protected final Properties properties;
  protected final String source;
  protected final Function<String, String> environment;

  protected OidcSettings(Properties properties, String source, Function<String, String> environment) {
    this.properties = properties;
    this.source = source;
    this.environment = environment;
  }

  /** Settings of the given properties, e.g. in tests. */
  public static OidcSettings of(Properties properties) {
    return new OidcSettings(properties, "(properties)", System::getenv);
  }

  public static OidcSettings of(Properties properties, Function<String, String> environment) {
    return new OidcSettings(properties, "(properties)", environment);
  }

  /**
   * Loads the settings from the file named by the init-param, or from the
   * default locations; the result is cached per file. Without a file the
   * result has single sign-on switched off.
   */
  public static OidcSettings load(String configuredPath) {
    Path path = locate(configuredPath);
    if (path == null) {
      return DISABLED;
    }
    String key = path.toAbsolutePath().normalize().toString();
    return CACHE.computeIfAbsent(key, k -> read(path));
  }

  protected static Path locate(String configuredPath) {
    List<String> candidates = new ArrayList<>();
    candidates.add(configuredPath);
    candidates.add(System.getProperty(CONFIG_FILE_PROPERTY));
    candidates.add(System.getenv(CONFIG_FILE_ENV));
    String explicit = firstNonBlank(candidates);
    if (explicit != null) {
      Path path = Paths.get(explicit);
      if (!Files.isRegularFile(path)) {
        throw new IllegalStateException("Single sign-on configuration " + path + " not found");
      }
      return path;
    }
    String catalinaBase = System.getProperty("catalina.base");
    if (catalinaBase != null && !catalinaBase.isBlank()) {
      Path path = Paths.get(catalinaBase, "conf", FILE_NAME);
      if (Files.isRegularFile(path)) {
        return path;
      }
    }
    String jbossConfig = System.getProperty("jboss.server.config.dir");
    if (jbossConfig != null && !jbossConfig.isBlank()) {
      Path path = Paths.get(jbossConfig, FILE_NAME);
      if (Files.isRegularFile(path)) {
        return path;
      }
    }
    return null;
  }

  protected static String firstNonBlank(List<String> values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  protected static OidcSettings read(Path path) {
    Properties properties = new Properties();
    try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read the single sign-on configuration " + path, e);
    }
    LOG.info("Single sign-on configuration read from " + path);
    return new OidcSettings(properties, path.toString(), System::getenv);
  }

  public String getSource() {
    return source;
  }

  // ---------------------------------------------------------------- raw access

  /** The value of {@code fasti.security.oidc.<key>}, placeholders resolved, or null if empty. */
  public String get(String key) {
    String value = properties.getProperty(PREFIX + key);
    if (value == null) {
      return null;
    }
    value = resolve(value.trim()).trim();
    return value.isEmpty() ? null : value;
  }

  public String get(String key, String defaultValue) {
    String value = get(key);
    return value != null ? value : defaultValue;
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    String value = get(key);
    if (value == null) {
      return defaultValue;
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalStateException(PREFIX + key + " must be true or false, not \"" + value + "\"");
  }

  public long getLong(String key, long defaultValue) {
    String value = get(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalStateException(PREFIX + key + " must be a number, not \"" + value + "\"");
    }
  }

  /** A comma-separated list; the indexed form {@code key[0]=...} is accepted as well. */
  public List<String> getList(String key) {
    List<String> result = new ArrayList<>();
    String value = get(key);
    if (value != null) {
      for (String item : value.split(",")) {
        if (!item.isBlank()) {
          result.add(item.trim());
        }
      }
    }
    for (int i = 0; ; i++) {
      String item = get(key + "[" + i + "]");
      if (item == null) {
        break;
      }
      result.add(item);
    }
    return Collections.unmodifiableList(result);
  }

  /** The entries {@code <key>.<name>=<value>}, e.g. {@code groups.mapping.<claim value>=<group>}. */
  public Map<String, String> getMap(String key) {
    String prefix = PREFIX + key + ".";
    Map<String, String> result = new LinkedHashMap<>();
    for (String name : properties.stringPropertyNames()) {
      if (name.startsWith(prefix) && name.length() > prefix.length()) {
        String value = resolve(properties.getProperty(name).trim()).trim();
        if (!value.isEmpty()) {
          result.put(name.substring(prefix.length()), value);
        }
      }
    }
    return result;
  }

  protected String resolve(String value) {
    StringBuilder result = new StringBuilder();
    int i = 0;
    while (i < value.length()) {
      int start = value.indexOf("${", i);
      int end = start < 0 ? -1 : value.indexOf('}', start);
      if (start < 0 || end < 0) {
        result.append(value, i, value.length());
        break;
      }
      result.append(value, i, start);
      String expression = value.substring(start + 2, end);
      int colon = expression.indexOf(':');
      String name = colon < 0 ? expression : expression.substring(0, colon);
      String fallback = colon < 0 ? null : expression.substring(colon + 1);
      String replacement = System.getProperty(name);
      if (replacement == null) {
        replacement = environment.apply(name);
      }
      if (replacement == null) {
        if (fallback == null) {
          throw new IllegalStateException("Single sign-on configuration: ${" + name + "} is not set");
        }
        replacement = fallback;
      }
      result.append(replacement);
      i = end + 1;
    }
    return result.toString();
  }

  // ---------------------------------------------------------------- typed access

  /** Whether the browser login of the web applications is configured. */
  public boolean isLoginEnabled() {
    return get("client-id") != null && getBoolean("login.enabled", true);
  }

  /** Whether bearer tokens are accepted on the REST API. */
  public boolean isRestBearerEnabled() {
    return getBoolean("rest.bearer-enabled", false);
  }

  public String getIssuerUri() {
    return get("issuer-uri");
  }

  public long getClockSkewSeconds() {
    return getLong("clock-skew-seconds", 60);
  }

  public ClaimRules claimRules() {
    return ClaimRules.builder()
        .groupsClaim(get("groups-claim"))
        .groupsDelimiter(get("groups-delimiter"))
        .restGroupsClaim(get("rest.groups-claim"))
        .admin(getList("groups.admin"))
        .mapping(getMap("groups.mapping"))
        .includeUnmapped(getBoolean("groups.include-unmapped", true))
        .tenantsClaim(get("tenants.claim"))
        .firstNameClaim(get("user.first-name-claim", "given_name"))
        .lastNameClaim(get("user.last-name-claim", "family_name"))
        .emailClaim(get("user.email-claim", "email"))
        .build();
  }
}
