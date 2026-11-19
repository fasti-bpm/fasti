/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import jakarta.servlet.FilterConfig;

import org.camunda.bpm.webapp.impl.filter.ClassicWebappsFilter;

/**
 * Keeps the classic web applications (Cockpit, Tasklist, Admin and Welcome,
 * built on AngularJS 1.8 and Bootstrap 3, both end of life) from being served,
 * while the new web UI below <code>/app/next/</code> works as before; see
 * {@link ClassicWebappsFilter} for the details. Registered by Spring Boot only
 * if the classic applications are switched off
 * (<code>fasti.webapp.classic.enabled=false</code>), which the initializer
 * decides; this filter does not read any setting itself.
 */
public class ClassicWebappsDisabledFilter extends ClassicWebappsFilter {

  public ClassicWebappsDisabledFilter() {
    this.classicEnabled = false;
  }

  public ClassicWebappsDisabledFilter(String applicationPath) {
    this();
    this.applicationPath = applicationPath == null ? "" : applicationPath;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    String path = filterConfig.getInitParameter(APPLICATION_PATH_PARAM);
    if (path != null) {
      applicationPath = path;
    }
  }
}
