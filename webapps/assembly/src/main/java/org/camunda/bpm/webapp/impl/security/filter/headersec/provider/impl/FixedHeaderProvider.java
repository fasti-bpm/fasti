/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl;

import java.util.Map;
import org.camunda.bpm.webapp.impl.security.filter.headersec.provider.HeaderSecurityProvider;
import org.camunda.bpm.webapp.impl.util.ServletFilterUtil;

/**
 * A response header with a fixed default value that the filter configuration may change or turn
 * off, for headers that need no further logic: {@code Referrer-Policy},
 * {@code Permissions-Policy} and {@code Cross-Origin-Opener-Policy}.
 */
public class FixedHeaderProvider extends HeaderSecurityProvider {

  public static final String PERMISSIONS_POLICY =
      "camera=(), microphone=(), geolocation=(), payment=(), usb=(), serial=(), bluetooth=()";

  protected final String headerName;
  protected final String defaultValue;
  protected final String disabledParam;
  protected final String valueParam;

  public FixedHeaderProvider(String headerName, String defaultValue, String paramPrefix) {
    this.headerName = headerName;
    this.defaultValue = defaultValue;
    this.disabledParam = paramPrefix + "Disabled";
    this.valueParam = paramPrefix + "Value";
  }

  public static FixedHeaderProvider referrerPolicy() {
    return new FixedHeaderProvider("Referrer-Policy", "same-origin", "referrerPolicy");
  }

  public static FixedHeaderProvider permissionsPolicy() {
    return new FixedHeaderProvider("Permissions-Policy", PERMISSIONS_POLICY, "permissionsPolicy");
  }

  public static FixedHeaderProvider crossOriginOpenerPolicy() {
    return new FixedHeaderProvider("Cross-Origin-Opener-Policy", "same-origin", "crossOriginOpenerPolicy");
  }

  @Override
  public Map<String, String> initParams() {
    initParams.put(disabledParam, null);
    initParams.put(valueParam, null);
    return initParams;
  }

  @Override
  public void parseParams() {
    String disabled = initParams.get(disabledParam);
    setDisabled(!ServletFilterUtil.isEmpty(disabled) && Boolean.parseBoolean(disabled));
    if (!isDisabled()) {
      String value = initParams.get(valueParam);
      setValue(ServletFilterUtil.isEmpty(value) ? defaultValue : value);
    }
  }

  @Override
  public String getHeaderName() {
    return headerName;
  }
}
