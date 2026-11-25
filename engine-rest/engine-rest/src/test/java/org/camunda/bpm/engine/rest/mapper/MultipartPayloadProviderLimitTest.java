/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.rest.mapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import org.camunda.bpm.engine.rest.exception.RestException;
import org.junit.After;
import org.junit.Test;

public class MultipartPayloadProviderLimitTest {

  private static final String BOUNDARY = "fasti-boundary";

  @After
  public void tearDown() {
    System.clearProperty(MultipartPayloadProvider.MAX_SIZE_PROPERTY);
    System.clearProperty(MultipartPayloadProvider.MAX_PARTS_PROPERTY);
  }

  @Test
  public void shouldReadRequestWithinTheLimits() throws IOException {
    // given
    System.setProperty(MultipartPayloadProvider.MAX_SIZE_PROPERTY, "4KB");

    // when
    MultipartFormData data = read(body(2, 1000));

    // then
    assertThat(data.getNamedPart("part0").getBinaryContent()).hasSize(1000);
    assertThat(data.getNamedPart("part1").getBinaryContent()).hasSize(1000);
  }

  @Test
  public void shouldAnswer413WhenTheRequestIsTooLarge() {
    // given
    System.setProperty(MultipartPayloadProvider.MAX_SIZE_PROPERTY, "1KB");

    // when
    RestException e = catchThrowableOfType(() -> read(body(1, 5000)), RestException.class);

    // then
    assertThat(e).isNotNull();
    assertThat(e.getStatus()).isEqualTo(Status.REQUEST_ENTITY_TOO_LARGE);
  }

  @Test
  public void shouldAnswer413WhenThereAreTooManyParts() {
    // given
    System.setProperty(MultipartPayloadProvider.MAX_PARTS_PROPERTY, "3");

    // when
    RestException e = catchThrowableOfType(() -> read(body(5, 10)), RestException.class);

    // then
    assertThat(e).isNotNull();
    assertThat(e.getStatus()).isEqualTo(Status.REQUEST_ENTITY_TOO_LARGE);
  }

  @Test
  public void shouldUseDefaultsWithoutConfiguration() {
    // when
    MultipartPayloadProvider provider = new MultipartPayloadProvider();

    // then
    assertThat(provider.createFileUploadInstance().getSizeMax()).isEqualTo(100L * 1024 * 1024);
    assertThat(provider.createFileUploadInstance().getFileCountMax()).isEqualTo(1000);
  }

  @Test
  public void shouldParseSizes() {
    assertThat(MultipartPayloadProvider.parseSize("2048")).isEqualTo(2048);
    assertThat(MultipartPayloadProvider.parseSize("4 kb")).isEqualTo(4096);
    assertThat(MultipartPayloadProvider.parseSize("10MB")).isEqualTo(10L * 1024 * 1024);
    assertThat(MultipartPayloadProvider.parseSize("2GB")).isEqualTo(2L * 1024 * 1024 * 1024);
    assertThat(MultipartPayloadProvider.parseSize("-1")).isEqualTo(-1);
  }

  @Test
  public void shouldIgnoreInvalidConfiguration() {
    // given
    System.setProperty(MultipartPayloadProvider.MAX_SIZE_PROPERTY, "lots");
    assertThat(new MultipartPayloadProvider().createFileUploadInstance().getSizeMax()).isEqualTo(100L * 1024 * 1024);
    System.setProperty(MultipartPayloadProvider.MAX_SIZE_PROPERTY, "9223372036854775807GB");

    // then
    assertThat(new MultipartPayloadProvider().createFileUploadInstance().getSizeMax()).isEqualTo(100L * 1024 * 1024);
  }

  private MultipartFormData read(byte[] body) throws IOException {
    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    headers.add("content-type", "multipart/form-data; boundary=" + BOUNDARY);
    return new MultipartPayloadProvider().readFrom(MultipartFormData.class, MultipartFormData.class, null,
        MediaType.MULTIPART_FORM_DATA_TYPE, headers, new ByteArrayInputStream(body));
  }

  private static byte[] body(int parts, int partSize) {
    StringBuilder body = new StringBuilder();
    String content = "x".repeat(partSize);
    for (int i = 0; i < parts; i++) {
      body.append("--").append(BOUNDARY).append("\r\n")
          .append("Content-Disposition: form-data; name=\"part").append(i).append("\"; filename=\"f").append(i).append(".txt\"\r\n")
          .append("Content-Type: text/plain\r\n\r\n")
          .append(content).append("\r\n");
    }
    body.append("--").append(BOUNDARY).append("--\r\n");
    return body.toString().getBytes(UTF_8);
  }
}
