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

import jakarta.servlet.ServletContext;

/**
 * Without <code>camunda.bpm.run.auth.enabled</code> the REST API is not
 * authenticated, which is reported on startup.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RestApiAuthenticationCheckDisabledAuthTest extends AbstractRestTest {

  @Autowired
  protected ServletContext servletContext;

  @Test
  public void shouldDetectMissingAuthentication() {
    assertThat(RestApiAuthenticationCheck.isAuthenticationConfigured(servletContext)).isFalse();
    assertThat(RestApiAuthenticationCheck.isWarningEnabled(servletContext)).isTrue();
    assertThat(RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext)).isTrue();
  }

}
