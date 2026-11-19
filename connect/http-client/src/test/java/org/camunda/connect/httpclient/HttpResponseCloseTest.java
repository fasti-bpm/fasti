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
package org.camunda.connect.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.camunda.connect.ConnectorRequestException;
import org.camunda.connect.ConnectorResponseException;
import org.camunda.connect.httpclient.impl.HttpConnectorImpl;
import org.camunda.connect.httpclient.impl.HttpResponseImpl;
import org.camunda.connect.impl.DebugRequestInterceptor;
import org.junit.Before;
import org.junit.Test;

/**
 * Asserts that the underlying HTTP response is released exactly once.
 */
public class HttpResponseCloseTest {

  protected HttpConnector connector;
  protected CountingResponse testResponse;

  @Before
  public void createConnector() {
    testResponse = new CountingResponse();
    connector = new HttpConnectorImpl();
    connector.addRequestInterceptor(new DebugRequestInterceptor(testResponse));
  }

  @Test
  public void shouldNotCloseResponseBeforeItIsRead() {
    // given
    testResponse.payload("test");

    // when
    execute();

    // then the caller is responsible for the response
    assertThat(testResponse.closeCount).isZero();
  }

  @Test
  public void shouldCloseResponseWithEntityAfterReading() {
    // given
    testResponse.payload("test");
    HttpResponse response = execute();

    // when
    String body = response.getResponse();

    // then
    assertThat(body).isEqualTo("test");
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseWithoutEntityAfterReading() {
    // given
    testResponse.code(204);
    HttpResponse response = execute();

    // when
    Integer statusCode = response.getStatusCode();

    // then
    assertThat(statusCode).isEqualTo(204);
    assertThat(response.getResponse()).isNull();
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseOnlyOnce() {
    // given
    testResponse.payload("test");
    HttpResponse response = execute();

    // when the response is read repeatedly and closed by the caller as well
    response.getResponse();
    response.getStatusCode();
    response.getResponseParameters();
    response.close();
    response.close();

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseUnreadResponseOnlyOnce() {
    // given
    testResponse.payload("test");
    HttpResponse response = execute();

    // when
    response.close();
    response.close();

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseIfReadingFails() {
    // given
    testResponse.setEntity(new InputStreamEntity(new FailingInputStream(), ContentType.TEXT_PLAIN));
    HttpResponse response = execute();

    // when
    assertThatThrownBy(response::getResponse)
      .isInstanceOf(RuntimeException.class);

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseIfContentIsNotAvailable() {
    // given
    testResponse.setEntity(new HttpEntityWrapper(new StringEntity("test", ContentType.TEXT_PLAIN)) {
      @Override
      public InputStream getContent() throws IOException {
        throw new IOException("expected");
      }
    });
    HttpResponse response = execute();

    // when
    assertThatThrownBy(response::getResponse)
      .isInstanceOf(ConnectorResponseException.class);

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseIfHeadersCannotBeRead() {
    // given
    testResponse.payload("test");
    testResponse.headerFailure = new IllegalStateException("expected");
    HttpResponse response = execute();

    // when
    assertThatThrownBy(response::getResponse)
      .isInstanceOf(IllegalStateException.class);

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldNotFailReadingIfCloseFails() {
    // given
    testResponse.payload("test");
    testResponse.closeFailure = new IOException("expected");
    HttpResponse response = execute();

    // when
    String body = response.getResponse();

    // then
    assertThat(body).isEqualTo("test");
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseOfHttpErrorWithEntity() {
    // given
    testResponse.code(500).payload("error");

    // when
    assertThatThrownBy(() -> executeThrowingHttpErrors())
      .isInstanceOf(ConnectorRequestException.class)
      .hasMessageContaining("500");

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseOfHttpErrorWithoutEntity() {
    // given
    testResponse.code(500);

    // when
    assertThatThrownBy(() -> executeThrowingHttpErrors())
      .isInstanceOf(ConnectorRequestException.class)
      .hasMessageContaining("500");

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseIfErrorHandlingFails() {
    // given
    testResponse.payload("test");
    testResponse.headerFailure = new IllegalStateException("expected");

    // when
    assertThatThrownBy(() -> executeThrowingHttpErrors())
      .isInstanceOf(IllegalStateException.class);

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldCloseResponseOfSubclass() {
    // given a response type that extends the HTTP response, like the one of the SOAP connector
    testResponse.code(204);
    HttpResponseImpl response = new HttpResponseImpl(testResponse) { };

    // when
    response.getResponseParameters();
    response.close();

    // then
    assertThat(testResponse.closeCount).isEqualTo(1);
  }

  protected HttpResponse execute() {
    return connector.createRequest().url("http://camunda.com").get().execute();
  }

  protected HttpResponse executeThrowingHttpErrors() {
    return connector.createRequest().configOption("throw-http-error", "TRUE").url("http://camunda.com").get().execute();
  }

  protected static class CountingResponse extends TestResponse {

    private static final long serialVersionUID = 1L;

    protected int closeCount = 0;
    protected IOException closeFailure;
    protected RuntimeException headerFailure;

    @Override
    public Header[] getHeaders() {
      if (headerFailure != null) {
        throw headerFailure;
      }
      return super.getHeaders();
    }

    @Override
    public void close() {
      closeCount++;
      if (closeFailure != null) {
        CountingResponse.<RuntimeException>sneakyThrow(closeFailure);
      }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
      throw (T) t;
    }
  }

  protected static class FailingInputStream extends InputStream {

    @Override
    public int read() throws IOException {
      throw new IOException("expected");
    }
  }

}
