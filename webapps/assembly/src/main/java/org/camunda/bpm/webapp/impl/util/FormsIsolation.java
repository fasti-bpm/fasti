/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;

/**
 * How the new web UI isolates embedded HTML forms (form key
 * <code>embedded:...</code>) from itself. Embedded forms contain markup and
 * scripts of whoever deployed the process; they are rendered by the form
 * frame (<code>/app/forms/{engine}/</code>).
 */
public enum FormsIsolation {

  /**
   * Default. The frame is sandboxed without <code>allow-same-origin</code>,
   * so that it has an opaque origin: no cookies, no session, no access to the
   * page that embeds it. The embedding page performs the few REST calls a form
   * needs on its behalf, after checking each of them against the task or
   * start event the form is shown for.
   */
  SANDBOXED("sandboxed"),

  /**
   * The frame is served from another origin (see
   * {@link ServletContextUtil#getFormsOrigin}). It keeps
   * <code>allow-same-origin</code> for that origin (local storage, modules,
   * fonts work as usual), but has no session of the web UI either: REST calls
   * are brokered as in {@link #SANDBOXED}.
   */
  SEPARATE_ORIGIN("separate-origin"),

  /**
   * The behaviour before the isolation: the frame shares the origin and the
   * session of the web UI. Scripts of a form can reach the embedding page and
   * act with the session of the user beyond the form. Only for installations
   * whose forms cannot run isolated and whose process deployers are trusted.
   */
  SAME_ORIGIN("same-origin");

  public static final FormsIsolation DEFAULT = SANDBOXED;

  private final String value;

  FormsIsolation(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * Parses a configured value; a misspelled value must not silently weaken
   * the isolation.
   *
   * @param name the name of the setting, for the error message
   */
  public static FormsIsolation parse(String configured, String name) {
    if (configured == null || configured.trim().isEmpty()) {
      return DEFAULT;
    }
    String normalized = configured.trim().toLowerCase(Locale.ROOT);
    for (FormsIsolation isolation : values()) {
      if (isolation.value.equals(normalized)) {
        return isolation;
      }
    }
    throw new IllegalWebAppConfigurationException("'" + name
        + "' must be one of sandboxed, separate-origin, same-origin but is '" + configured + "'.");
  }

  /**
   * Checks the origin the form frame is served from in mode
   * {@link #SEPARATE_ORIGIN}: an http(s) origin without path, query or
   * credentials, e.g. <code>https://forms.example.com</code>.
   *
   * @return the normalized origin, or <code>null</code> if nothing is configured
   */
  public static String parseOrigin(String configured, String name) {
    if (configured == null || configured.trim().isEmpty()) {
      return null;
    }
    String trimmed = configured.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    try {
      URI uri = new URI(trimmed);
      String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
      boolean valid = ("http".equals(scheme) || "https".equals(scheme))
          && uri.getHost() != null
          && uri.getRawUserInfo() == null
          && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null
          && uri.getHost().matches("[A-Za-z0-9.:\\[\\]-]+");
      if (valid) {
        String origin = scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
      }
    } catch (URISyntaxException e) {
      // reported below
    }
    throw new IllegalWebAppConfigurationException("'" + name
        + "' must be an http or https origin such as https://forms.example.com but is '" + configured + "'.");
  }

  /**
   * Checks a combination of mode and origin: the separate origin mode needs
   * an origin.
   */
  public static void check(FormsIsolation isolation, String origin, String name) {
    if (isolation == SEPARATE_ORIGIN && origin == null) {
      throw new IllegalWebAppConfigurationException("The forms isolation 'separate-origin' needs '" + name + "'.");
    }
  }
}
