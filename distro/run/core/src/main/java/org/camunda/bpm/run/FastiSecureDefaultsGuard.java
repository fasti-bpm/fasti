/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.run;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

/**
 * Refuses to start when {@code fasti.security.enforce-auth=true} (set by the
 * container image) but the effective configuration turns REST authentication
 * or authorization off, or would create an administrator without an explicit
 * password.
 * <p>
 * The check runs on the resolved Spring environment, so it covers every
 * source: YAML files mounted from a former installation, environment
 * variables, {@code SPRING_APPLICATION_JSON}, system properties and arguments.
 * It runs before any bean is created, so nothing is served in an insecure
 * state.
 */
public class FastiSecureDefaultsGuard implements BeanFactoryPostProcessor {

  public static final String ENFORCE_PROPERTY = "fasti.security.enforce-auth";

  protected final Environment environment;

  public FastiSecureDefaultsGuard(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    List<String> problems = check(environment);
    if (!problems.isEmpty()) {
      throw new IllegalStateException("Insecure configuration refused (" + ENFORCE_PROPERTY + "=true): "
          + String.join("; ", problems)
          + ". Use FASTI_INSECURE_DEMO=true for an open demo instance.");
    }
  }

  public static List<String> check(Environment environment) {
    List<String> problems = new ArrayList<>();
    if (!environment.getProperty(ENFORCE_PROPERTY, Boolean.class, false)) {
      return problems;
    }
    if (!environment.getProperty("camunda.bpm.run.auth.enabled", Boolean.class, false)) {
      problems.add("camunda.bpm.run.auth.enabled is false");
    }
    if (!environment.getProperty("camunda.bpm.authorization.enabled", Boolean.class, false)) {
      problems.add("camunda.bpm.authorization.enabled is false");
    }
    String adminId = environment.getProperty("camunda.bpm.admin-user.id");
    String adminPassword = environment.getProperty("camunda.bpm.admin-user.password");
    if (adminId != null && !adminId.isBlank() && (adminPassword == null || adminPassword.isEmpty())) {
      // the starter would fall back to the user id as password
      problems.add("camunda.bpm.admin-user.id is set without camunda.bpm.admin-user.password");
    }
    return problems;
  }
}
