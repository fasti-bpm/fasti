/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class CookieConfiguratorAttributeTest {

  protected CookieConfigurator configurator(boolean secure) {
    CookieConfigurator configurator = new CookieConfigurator();
    configurator.isSameSiteCookieEnabled = true;
    configurator.sameSiteCookieValue = "Lax";
    configurator.isSecureCookieEnabled = secure;
    return configurator;
  }

  @Test
  public void keepsTheSameSiteAttributeWrittenByTheContainer() {
    // Tomcat writes "; SameSite=Strict" (with a space) when server.servlet.session.cookie.same-site is set
    String header = "JSESSIONID=ABC; Path=/; Secure; HttpOnly; SameSite=Strict";

    assertThat(configurator(true).getConfig(header)).isEqualTo(header);
  }

  @Test
  public void addsSameSiteAndSecureWhenMissing() {
    assertThat(configurator(true).getConfig("JSESSIONID=ABC; Path=/; HttpOnly"))
        .isEqualTo("JSESSIONID=ABC; Path=/; HttpOnly;SameSite=Lax;Secure");
  }
}
