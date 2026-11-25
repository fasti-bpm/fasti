/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import org.camunda.bpm.webapp.impl.WebappLogger;

/**
 * The one-time token that the initial setup asks for.
 *
 * <p>As long as an engine has no administrator, the web application offers a
 * page that creates the first one without a sign-in. Whoever reaches the
 * server first could take it over. The setup therefore also asks for a token
 * that only the person who runs the server can read: it is printed in the
 * server log (the same idea as the initial password of Jenkins) when the setup
 * page is first requested.
 *
 * <p>A fixed token can be configured with the system property
 * {@value #PROPERTY} or the environment variable {@value #ENV}, for automated
 * installations; it must have at least {@value #MIN_CONFIGURED_LENGTH}
 * characters, a shorter one is ignored.
 *
 * <p>Tokens live in memory, per JVM and engine. After a restart a new one is
 * printed; once an administrator exists the setup is closed and the token is
 * of no use.
 */
public final class InitialSetupToken {

  /** The request header that carries the token. */
  public static final String HEADER = "X-Fasti-Setup-Token";

  /** System property with a fixed token. */
  public static final String PROPERTY = "fasti.setup.token";

  /** Environment variable with a fixed token. */
  public static final String ENV = "FASTI_SETUP_TOKEN";

  static final int MIN_CONFIGURED_LENGTH = 16;

  private static final WebappLogger LOGGER = WebappLogger.INSTANCE;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final ConcurrentMap<String, String> TOKENS = new ConcurrentHashMap<>();

  /** Reads configuration; replaceable in tests. */
  static UnaryOperator<String> environment = System::getenv;

  private InitialSetupToken() {
  }

  /**
   * Makes sure the token of the engine exists and has been written to the
   * log. Called whenever the setup page is served.
   */
  public static void announce(String engineName) {
    tokenFor(engineName);
  }

  /**
   * Whether the presented token is the token of the engine. Compares in
   * constant time.
   */
  public static boolean matches(String engineName, String presented) {
    if (presented == null || presented.trim().isEmpty()) {
      return false;
    }
    byte[] expected = tokenFor(engineName).getBytes(UTF_8);
    return MessageDigest.isEqual(expected, presented.trim().getBytes(UTF_8));
  }

  static String tokenFor(String engineName) {
    String configured = configuredToken();
    if (configured != null) {
      return configured;
    }
    return TOKENS.computeIfAbsent(engineName, name -> {
      String token = generate();
      LOGGER.warnInitialSetupToken(name, token);
      return token;
    });
  }

  static String configuredToken() {
    String configured = System.getProperty(PROPERTY);
    if (configured == null || configured.trim().isEmpty()) {
      configured = environment.apply(ENV);
    }
    if (configured == null || configured.trim().isEmpty()) {
      return null;
    }
    configured = configured.trim();
    if (configured.length() < MIN_CONFIGURED_LENGTH) {
      LOGGER.warnInitialSetupTokenTooShort(MIN_CONFIGURED_LENGTH);
      return null;
    }
    return configured;
  }

  static String generate() {
    byte[] bytes = new byte[18];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Forgets the generated token of the engine after a successful setup. */
  public static void consume(String engineName) {
    TOKENS.remove(engineName);
  }

  /** Forgets every generated token; for tests. */
  static void reset() {
    TOKENS.clear();
  }
}
