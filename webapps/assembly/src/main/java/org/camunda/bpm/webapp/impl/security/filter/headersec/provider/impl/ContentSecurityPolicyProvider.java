/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl;

import org.camunda.bpm.webapp.impl.security.filter.headersec.provider.HeaderSecurityProvider;
import org.camunda.bpm.webapp.impl.util.ServletFilterUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public class ContentSecurityPolicyProvider extends HeaderSecurityProvider {

  public static final String HEADER_NAME = "Content-Security-Policy";
  public static final String HEADER_NONCE_PLACEHOLDER = "$NONCE";
  public static final String HEADER_DEFAULT_VALUE = ""
    + "base-uri 'self';"
    + "script-src " + HEADER_NONCE_PLACEHOLDER + " 'strict-dynamic' 'unsafe-eval' https: 'self' 'unsafe-inline';"
    + "style-src 'unsafe-inline' 'self';"
    + "default-src 'self';"
    + "img-src 'self' data:;"
    + "block-all-mixed-content;"
    + "form-action 'self';"
    + "frame-ancestors 'none';"
    + "object-src 'none';"
    + "sandbox allow-forms allow-scripts allow-same-origin allow-popups allow-downloads";

  public static final String DISABLED_PARAM = "contentSecurityPolicyDisabled";
  public static final String VALUE_PARAM = "contentSecurityPolicyValue";
  public static final String ATTR_CSP_FILTER_NONCE = "org.camunda.bpm.csp.nonce";
  public static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  protected static final SecureRandom RANDOM = new SecureRandom();

  @Override
  public Map<String, String> initParams() {
    initParams.put(VALUE_PARAM, null);
    initParams.put(DISABLED_PARAM, null);

    return initParams;
  }

  @Override
  public void parseParams() {
    String disabled = initParams.get(DISABLED_PARAM);

    if (ServletFilterUtil.isEmpty(disabled)) {
      setDisabled(false);

    } else {
      setDisabled(Boolean.parseBoolean(disabled));

    }

    String value = initParams.get(VALUE_PARAM);
    if (!ServletFilterUtil.isEmpty(value)) {
      value = normalizeString(value);
      setValue(value);

    } else {
      setValue(HEADER_DEFAULT_VALUE);

    }
  }

  protected String normalizeString(String value) {
    return value
      .trim()
      .replaceAll("\\s+", " "); // replaces [\t\n\x0B\f\r]
  }

  @Override
  public String getHeaderName() {
    return HEADER_NAME;
  }

  /**
   * Creates the header value with a new nonce. The nonce is not stored
   * anywhere: use {@link #getHeaderValue(ServletRequest)} if the nonce is
   * needed to render the response.
   */
  @Override
  public String getHeaderValue(final ServletContext servletContext) {
    return createHeaderValue(generateNonce());
  }

  /**
   * Creates the header value with a new nonce. The nonce is only valid for
   * the response of the given request and is provided as request attribute
   * {@link #ATTR_CSP_FILTER_NONCE}. It must not be shared between requests
   * (e.g. in the servlet context): concurrent requests would overwrite each
   * other's nonce, so that a page is rendered with a nonce that does not
   * match its own header, or with a nonce that was issued for another client.
   */
  @Override
  public String getHeaderValue(final ServletRequest request) {
    final String nonce = generateNonce();
    request.setAttribute(ATTR_CSP_FILTER_NONCE, nonce);
    return createHeaderValue(nonce);
  }

  protected String createHeaderValue(final String nonce) {
    return value.replace(HEADER_NONCE_PLACEHOLDER, String.format("'nonce-%s'", nonce));
  }

  /**
   * A nonce must not be guessable, it is created by a cryptographically
   * strong random number generator.
   */
  protected String generateNonce() {
    final byte[] bytes = new byte[20];
    RANDOM.nextBytes(bytes);
    return ENCODER.encodeToString(bytes);
  }
}
