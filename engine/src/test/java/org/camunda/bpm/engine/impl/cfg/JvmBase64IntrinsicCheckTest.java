/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.cfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.After;
import org.junit.Test;

public class JvmBase64IntrinsicCheckTest {

  protected static final String PROPERTY = JvmBase64IntrinsicCheck.CHECK_ENABLED_SYSTEM_PROPERTY;

  @After
  public void clearProperty() {
    System.clearProperty(PROPERTY);
  }

  @Test
  public void shouldFlagBuildsOlderThanTheFix() {
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("21.0.2"))).isTrue();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("21.0.13+9"))).isTrue();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("17.0.21"))).isTrue();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("25.0.5"))).isTrue();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("27.0.1"))).isTrue();
  }

  @Test
  public void shouldAcceptFixedAndNewerBuilds() {
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("21.0.13.1+2"))).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("21.0.14"))).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("17.0.21.1"))).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("25.0.6"))).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("27.0.1.1"))).isFalse();
  }

  @Test
  public void shouldNotJudgeOtherReleaseLines() {
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("23.0.1"))).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isOlderThanFixedBuild(Runtime.Version.parse("11.0.20"))).isFalse();
  }

  @Test
  public void shouldOnlyFlagX86WithTheIntrinsicOnOrUnknown() {
    Runtime.Version old = Runtime.Version.parse("21.0.2");
    assertThat(JvmBase64IntrinsicCheck.isExposed(old, "amd64", Boolean.TRUE)).isTrue();
    assertThat(JvmBase64IntrinsicCheck.isExposed(old, "x86_64", null)).isTrue();
    assertThat(JvmBase64IntrinsicCheck.isExposed(old, "amd64", Boolean.FALSE)).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isExposed(old, "aarch64", Boolean.TRUE)).isFalse();
    assertThat(JvmBase64IntrinsicCheck.isExposed(Runtime.Version.parse("21.0.13.1"), "amd64", Boolean.TRUE)).isFalse();
  }

  @Test
  public void shouldNeverThrow() {
    assertThatCode(JvmBase64IntrinsicCheck::check).doesNotThrowAnyException();
    System.setProperty(PROPERTY, "false");
    assertThatCode(JvmBase64IntrinsicCheck::check).doesNotThrowAnyException();
  }

}
