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
package org.camunda.bpm.engine.rest.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static java.text.MessageFormat.format;

public class URLEncodingUtil {

  /**
   * Encode a string value using `UTF-8` encoding scheme
   */
  public static String encode(String value) {
    if (value != null) {
      try {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
      } catch (UnsupportedEncodingException ex) {
        // should not happen
        return value;
      }
    }

    return null;
  }

  /**
   * Creates the value of a <code>Content-Disposition</code> header for a
   * download. The file name is provided twice: percent-encoded
   * (<code>filename*</code>), which is the form clients prefer, and as quoted
   * string for clients that only understand the plain parameter.
   */
  public static String buildAttachmentValue(String attachmentFileName) {
    return format("attachment; filename=\"{0}\"; filename*=UTF-8''''{1}", toQuotedString(attachmentFileName), encode(attachmentFileName));
  }

  /**
   * A file name is arbitrary text (e.g. the name of a file variable). Within
   * the quoted string of a header it must neither end the string nor the
   * header: quotes, backslashes and every character that is not printable
   * US-ASCII (which includes line breaks) are replaced.
   */
  protected static String toQuotedString(String value) {
    if (value == null) {
      return null;
    }

    StringBuilder quotedString = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 || c > 0x7E || c == '"' || c == '\\') {
        quotedString.append('_');
      } else {
        quotedString.append(c);
      }
    }
    return quotedString.toString();
  }

}
