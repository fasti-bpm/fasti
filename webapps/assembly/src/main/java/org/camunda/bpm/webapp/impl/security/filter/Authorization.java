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
package org.camunda.bpm.webapp.impl.security.filter;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.webapp.impl.security.auth.Authentication;
import org.camunda.bpm.webapp.impl.security.auth.UserAuthentication;

/**
 *
 * @author nico.rehwaldt
 */
public class Authorization {

  private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

  private final Authentication authentication;
  private final boolean granted;

  private String application;

  public Authorization(Authentication authentication, boolean granted) {
    this.authentication = authentication;
    this.granted = granted;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public boolean isGranted() {
    return granted;
  }

  public Authorization forApplication(String application) {
    this.application = application;

    return this;
  }

  public void attachHeaders(HttpServletResponse response) {

    if (authentication != null) {
      // header != null checks required for websphere compatibility
      if (authentication.getIdentityId() != null) {
        response.addHeader("X-Authorized-User", encodeHeaderValue(authentication.getIdentityId()));
      }

      if (authentication.getProcessEngineName() != null) {
        response.addHeader("X-Authorized-Engine", encodeHeaderValue(authentication.getProcessEngineName()));
      }

      if (authentication instanceof UserAuthentication) {
        response.addHeader("X-Authorized-Apps", join(",", ((UserAuthentication) authentication).getAuthorizedApps()));
      }
    }

    // response.addHeader("X-Authorized", Boolean.toString(granted));
  }

  public boolean isAuthenticated() {
    return authentication != null && authentication != Authentication.ANONYMOUS;
  }

  public String getApplication() {
    return application;
  }

  ////// static helpers //////////////////////////////

  public static Authorization granted(Authentication authentication) {
    return new Authorization(authentication, true);
  }

  public static Authorization denied(Authentication authentication) {
    return new Authorization(authentication, false);
  }

  public static Authorization grantedUnlessNull(Authentication authentication) {
    return authentication != null ? granted(authentication) : denied(authentication);
  }

  /**
   * <p>Makes a value safe to be used as the value of a response header.</p>
   *
   * <p>A header value may only consist of printable US-ASCII characters.
   * Identifiers (e.g. the id of a user that is managed in an external
   * directory) are not restricted that way: characters outside of this range
   * are garbled or rejected by the container, and line breaks would allow to
   * add further headers to the response. All characters that are not
   * printable US-ASCII are therefore percent-encoded with their UTF-8
   * representation, as well as the percent sign itself, so that the original
   * value can be restored by percent-decoding. Values that consist of
   * printable US-ASCII characters only (except <code>%</code>) are not changed.</p>
   *
   * @return the encoded value
   */
  protected static String encodeHeaderValue(String value) {
    StringBuilder encoded = null;

    int i = 0;
    while (i < value.length()) {
      int codePoint = value.codePointAt(i);
      int charCount = Character.charCount(codePoint);

      if (codePoint >= 0x20 && codePoint <= 0x7E && codePoint != '%') {
        if (encoded != null) {
          encoded.append((char) codePoint);
        }
      } else {
        if (encoded == null) {
          encoded = new StringBuilder(value.length() + 16).append(value, 0, i);
        }
        byte[] bytes = value.substring(i, i + charCount).getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
          encoded.append('%')
            .append(HEX_DIGITS[(b >> 4) & 0xF])
            .append(HEX_DIGITS[b & 0xF]);
        }
      }

      i += charCount;
    }

    return encoded == null ? value : encoded.toString();
  }

  private static String join(String delimiter, Collection<?> collection) {

    StringBuilder builder = new StringBuilder();

    for (Object o: collection) {

      if (builder.length() > 0) {
        builder.append(delimiter);
      }

      builder.append(o);
    }

    return builder.toString();
  }
}
