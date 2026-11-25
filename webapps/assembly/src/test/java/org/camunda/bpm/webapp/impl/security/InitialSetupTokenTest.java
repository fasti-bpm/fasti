/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import org.camunda.commons.testing.ProcessEngineLoggingRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class InitialSetupTokenTest {

  @Rule
  public ProcessEngineLoggingRule loggingRule = new ProcessEngineLoggingRule()
      .watch("org.camunda.bpm.webapp")
      .level(Level.WARN);

  @Before
  public void setUp() {
    InitialSetupToken.reset();
    System.clearProperty(InitialSetupToken.PROPERTY);
    InitialSetupToken.environment = name -> null;
  }

  @After
  public void tearDown() {
    InitialSetupToken.reset();
    System.clearProperty(InitialSetupToken.PROPERTY);
    InitialSetupToken.environment = System::getenv;
  }

  @Test
  public void shouldGenerateOneTokenPerEngineAndLogIt() {
    // when
    InitialSetupToken.announce("default");
    InitialSetupToken.announce("default");
    String token = InitialSetupToken.tokenFor("default");

    // then
    assertThat(token).hasSizeGreaterThanOrEqualTo(24).matches("[A-Za-z0-9_-]+");
    assertThat(loggingRule.getFilteredLog("enter this setup token: " + token)).hasSize(1);
    assertThat(InitialSetupToken.tokenFor("other")).isNotEqualTo(token);
  }

  @Test
  public void shouldMatchOnlyTheToken() {
    String token = InitialSetupToken.tokenFor("default");

    assertThat(InitialSetupToken.matches("default", token)).isTrue();
    assertThat(InitialSetupToken.matches("default", " " + token + " ")).isTrue();
    assertThat(InitialSetupToken.matches("default", token + "x")).isFalse();
    assertThat(InitialSetupToken.matches("default", token.substring(1))).isFalse();
    assertThat(InitialSetupToken.matches("default", "")).isFalse();
    assertThat(InitialSetupToken.matches("default", null)).isFalse();
    assertThat(InitialSetupToken.matches("other", token)).isFalse();
  }

  @Test
  public void shouldUseConfiguredToken() {
    // given
    System.setProperty(InitialSetupToken.PROPERTY, "configured-token-0123456789");

    // then
    assertThat(InitialSetupToken.matches("default", "configured-token-0123456789")).isTrue();
    assertThat(loggingRule.getFilteredLog("enter this setup token")).isEmpty();
  }

  @Test
  public void shouldUseTokenFromEnvironment() {
    // given
    InitialSetupToken.environment = name -> InitialSetupToken.ENV.equals(name) ? "token-from-the-environment" : null;

    // then
    assertThat(InitialSetupToken.matches("default", "token-from-the-environment")).isTrue();
  }

  @Test
  public void shouldIgnoreShortConfiguredToken() {
    // given
    System.setProperty(InitialSetupToken.PROPERTY, "short");

    // when
    boolean matches = InitialSetupToken.matches("default", "short");

    // then
    assertThat(matches).isFalse();
    assertThat(loggingRule.getFilteredLog("The configured setup token is ignored")).isNotEmpty();
    assertThat(loggingRule.getFilteredLog("enter this setup token")).hasSize(1);
  }
}
