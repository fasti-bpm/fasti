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
import org.camunda.bpm.webapp.impl.util.FormsIsolation;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.camunda.bpm.webapp.impl.util.ServletFilterUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
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

  /**
   * The default policy of the new web UI (<code>/app/next/...</code>). It
   * differs from {@link #HEADER_DEFAULT_VALUE} in the scripts only: neither
   * <code>eval</code> nor inline scripts without the nonce of the response
   * are allowed. <code>'self'</code> is a fallback for browsers that do not
   * know <code>'strict-dynamic'</code>; the others ignore it.
   */
  public static final String HEADER_NEXT_APP_DEFAULT_VALUE = ""
    + "base-uri 'self';"
    + "script-src " + HEADER_NONCE_PLACEHOLDER + " 'strict-dynamic' 'self';"
    + "style-src 'unsafe-inline' 'self';"
    + "default-src 'self';"
    + "img-src 'self' data:;"
    + "block-all-mixed-content;"
    + "form-action 'self';"
    + "frame-ancestors 'none';"
    + "object-src 'none';"
    + "sandbox allow-forms allow-scripts allow-same-origin allow-popups allow-downloads";

  /** the path of the new web UI, below the context and application path */
  public static final String NEXT_APP_PATH = "/app/next";

  /**
   * The path of the page that renders embedded HTML forms for the new web UI
   * in an isolated frame, below the context and application path.
   */
  public static final String FORM_FRAME_PATH = "/app/forms";

  protected static final String FORM_FRAME_ANCESTORS = "frame-ancestors 'self'";

  /**
   * The default policy of the form frame (<code>/app/forms/...</code>): the
   * policy of the classic applications, framed by the same origin only.
   */
  public static final String HEADER_FORM_FRAME_DEFAULT_VALUE = formFramePolicy(HEADER_DEFAULT_VALUE);

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
      // a policy configured by the operator applies to every application
      nextAppValue = value;
      formFrameValue = formFramePolicy(value);

    } else {
      setValue(HEADER_DEFAULT_VALUE);
      nextAppValue = HEADER_NEXT_APP_DEFAULT_VALUE;
      formFrameValue = HEADER_FORM_FRAME_DEFAULT_VALUE;

    }
  }

  /** The policy for the form frame of the new web UI. */
  protected String formFrameValue = HEADER_FORM_FRAME_DEFAULT_VALUE;

  /** The policy for the pages and resources of the new web UI. */
  protected String nextAppValue = HEADER_NEXT_APP_DEFAULT_VALUE;

  /**
   * @return <code>true</code> if the request addresses the new web UI, i.e. a
   *         path below <code>{context path}{application path}/app/next</code>
   */
  protected boolean isNextAppRequest(ServletRequest request) {
    return isBelow(request, NEXT_APP_PATH);
  }

  /**
   * @return <code>true</code> if the request addresses the page that renders
   *         embedded forms for the new web UI, i.e. a path below
   *         <code>{context path}{application path}/app/forms</code>
   */
  protected boolean isFormFrameRequest(ServletRequest request) {
    return isBelow(request, FORM_FRAME_PATH);
  }

  protected boolean isBelow(ServletRequest request, String appPath) {
    if (!(request instanceof HttpServletRequest)) {
      return false;
    }
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    String path = httpRequest.getRequestURI();
    if (path == null) {
      return false;
    }
    String prefix = httpRequest.getContextPath() + ServletContextUtil.getAppPath(httpRequest.getServletContext());
    if (!path.startsWith(prefix)) {
      return false;
    }
    path = path.substring(prefix.length());
    return path.equals(appPath) || path.startsWith(appPath + "/");
  }

  /**
   * The policy of the form frame: the policy of the classic applications,
   * because embedded forms rely on what the classic Tasklist allowed them
   * (inline and evaluated scripts), except that the page may be framed by
   * pages of the same origin. Whatever the operator configured for
   * <code>frame-ancestors</code> is replaced; every other directive is kept.
   */
  protected static String formFramePolicy(String policy) {
    StringBuilder builder = new StringBuilder();
    for (String directive : policy.split(";")) {
      String trimmed = directive.trim();
      if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).matches("frame-ancestors(\\s.*)?")) {
        continue;
      }
      builder.append(trimmed).append(';');
    }
    return builder.append(FORM_FRAME_ANCESTORS).toString();
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
    String policy = value;
    if (isNextAppRequest(request)) {
      policy = nextAppPolicy(nextAppValue, ServletContextUtil.getFormsOrigin(request.getServletContext()));
    } else if (isFormFrameRequest(request)) {
      policy = formFramePolicy(formFrameValue, ServletContextUtil.getFormsIsolation(request.getServletContext()));
    }
    return createHeaderValue(policy, nonce);
  }

  /** The sandbox of the form frame in mode {@link FormsIsolation#SANDBOXED}. */
  protected static final String FORM_FRAME_SANDBOX = "sandbox allow-forms allow-scripts allow-popups allow-downloads";

  /**
   * Adapts the policy of the form frame to the isolation of embedded forms.
   * <ul>
   * <li>{@link FormsIsolation#SANDBOXED}: the <code>sandbox</code> directive
   * never contains <code>allow-same-origin</code>, so that the page has an
   * opaque origin even if it is framed without the sandbox attribute or
   * opened on its own.</li>
   * <li>{@link FormsIsolation#SEPARATE_ORIGIN}: the page is served from the
   * forms origin and framed by the web UI on another origin; any origin may
   * frame it. The page has no session of the web UI and only acts through
   * the page that embeds it, which checks the origin of the frame.</li>
   * <li>{@link FormsIsolation#SAME_ORIGIN}: unchanged.</li>
   * </ul>
   */
  protected static String formFramePolicy(String policy, FormsIsolation isolation) {
    if (isolation == FormsIsolation.SAME_ORIGIN) {
      return policy;
    }
    StringBuilder builder = new StringBuilder();
    boolean sandbox = false;
    for (String directive : policy.split(";")) {
      String trimmed = directive.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String name = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT);
      if (isolation == FormsIsolation.SEPARATE_ORIGIN && "frame-ancestors".equals(name)) {
        trimmed = "frame-ancestors *";
      } else if (isolation == FormsIsolation.SANDBOXED && "sandbox".equals(name)) {
        sandbox = true;
        StringBuilder tokens = new StringBuilder("sandbox");
        for (String token : trimmed.split("\\s+")) {
          String lower = token.toLowerCase(Locale.ROOT);
          if (!"sandbox".equals(lower) && !"allow-same-origin".equals(lower)) {
            tokens.append(' ').append(token);
          }
        }
        trimmed = tokens.toString();
      }
      builder.append(trimmed).append(';');
    }
    if (isolation == FormsIsolation.SANDBOXED && !sandbox) {
      builder.append(FORM_FRAME_SANDBOX).append(';');
    }
    String result = builder.toString();
    return result.endsWith(";") ? result.substring(0, result.length() - 1) : result;
  }

  /**
   * The policy of the new web UI: if the form frame is served from another
   * origin, the web UI may frame pages of that origin.
   */
  protected static String nextAppPolicy(String policy, String formsOrigin) {
    if (formsOrigin == null) {
      return policy;
    }
    StringBuilder builder = new StringBuilder();
    boolean frameSrc = false;
    for (String directive : policy.split(";")) {
      String trimmed = directive.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.toLowerCase(Locale.ROOT).matches("frame-src(\\s.*)?")) {
        frameSrc = true;
        trimmed = trimmed + " " + formsOrigin;
      }
      builder.append(trimmed).append(';');
    }
    if (!frameSrc) {
      builder.append("frame-src 'self' ").append(formsOrigin).append(';');
    }
    String result = builder.toString();
    return result.substring(0, result.length() - 1);
  }

  protected String createHeaderValue(final String nonce) {
    return createHeaderValue(value, nonce);
  }

  protected String createHeaderValue(final String policy, final String nonce) {
    return policy.replace(HEADER_NONCE_PLACEHOLDER, String.format("'nonce-%s'", nonce));
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
