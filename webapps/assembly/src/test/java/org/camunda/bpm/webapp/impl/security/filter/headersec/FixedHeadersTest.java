/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.filter.headersec;

import org.camunda.bpm.webapp.impl.util.HeaderRule;
import org.junit.Rule;
import org.junit.Test;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/** Referrer-Policy, Permissions-Policy and Cross-Origin-Opener-Policy, sent in every distribution. */
public class FixedHeadersTest {

  @Rule
  public HeaderRule headerRule = new HeaderRule();

  @Test
  public void shouldSendThePolicyHeadersByDefault() {
    // given
    headerRule.startServer("web.xml", "headersec");

    // when
    headerRule.performRequest();

    // then
    assertThat(headerRule.getHeader("Referrer-Policy"), is("same-origin"));
    assertThat(headerRule.getHeader("Cross-Origin-Opener-Policy"), is("same-origin"));
    assertThat(headerRule.getHeader("Permissions-Policy"), is(
        "camera=(), microphone=(), geolocation=(), payment=(), usb=(), serial=(), bluetooth=()"));
  }
}
