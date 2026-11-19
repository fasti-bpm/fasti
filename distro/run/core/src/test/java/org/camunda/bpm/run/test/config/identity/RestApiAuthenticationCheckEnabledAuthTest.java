/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.run.test.config.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.engine.rest.security.auth.RestApiAuthenticationCheck;
import org.camunda.bpm.run.test.AbstractRestTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import jakarta.servlet.ServletContext;

/**
 * With <code>camunda.bpm.run.auth.enabled</code> the authentication filter
 * is registered for the REST API, there is nothing to warn about.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles(profiles = { "test-auth-enabled" })
public class RestApiAuthenticationCheckEnabledAuthTest extends AbstractRestTest {

  @Autowired
  protected ServletContext servletContext;

  @Test
  public void shouldDetectAuthenticationFilter() {
    assertThat(RestApiAuthenticationCheck.isAuthenticationConfigured(servletContext)).isTrue();
    assertThat(RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext)).isFalse();
  }

}
