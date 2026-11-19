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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * The name of a file (e.g. of a file variable or of a deployment resource) is
 * written to the <code>Content-Disposition</code> header of a download. It is
 * arbitrary text and must not be able to corrupt the header.
 */
public class URLEncodingUtilTest {

  @Test
  public void shouldKeepPlainFileName() {
    assertThat(URLEncodingUtil.buildAttachmentValue("invoice-2024_final (1).pdf"))
      .isEqualTo("attachment; filename=\"invoice-2024_final (1).pdf\"; filename*=UTF-8''invoice-2024_final%20%281%29.pdf");
  }

  @Test
  public void shouldKeepMissingFileName() {
    assertThat(URLEncodingUtil.buildAttachmentValue(null))
      .isEqualTo("attachment; filename=\"null\"; filename*=UTF-8''null");
  }

  @Test
  public void shouldNotAllowToLeaveTheQuotedFileName() {
    // when
    String value = URLEncodingUtil.buildAttachmentValue("a\"; filename=\"evil.html");

    // then
    assertThat(value)
      .isEqualTo("attachment; filename=\"a_; filename=_evil.html\"; filename*=UTF-8''a%22%3B%20filename%3D%22evil.html");
  }

  @Test
  public void shouldNotAllowToInjectHeaders() {
    // when
    String value = URLEncodingUtil.buildAttachmentValue("a.txt\r\nSet-Cookie: JSESSIONID=fixed");

    // then
    assertThat(value).doesNotContain("\r").doesNotContain("\n");
    assertThat(value)
      .isEqualTo("attachment; filename=\"a.txt__Set-Cookie: JSESSIONID=fixed\"; "
          + "filename*=UTF-8''a.txt%0D%0ASet-Cookie%3A%20JSESSIONID%3Dfixed");
  }

  @Test
  public void shouldReplaceCharactersThatAreNotPrintableAscii() {
    // when
    String value = URLEncodingUtil.buildAttachmentValue("b\u00E1o c\u00E1o\\\u0000\u007F.pdf");

    // then
    assertThat(value).matches("[\\x20-\\x7E]+");
    assertThat(value)
      .isEqualTo("attachment; filename=\"b_o c_o___.pdf\"; filename*=UTF-8''b%C3%A1o%20c%C3%A1o%5C%00%7F.pdf");
  }

}
