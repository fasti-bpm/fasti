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
package org.camunda.connect.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.camunda.connect.ConnectorException;
import org.junit.Test;

public class CloseableConnectorResponseTest {

  @Test
  public void shouldCloseResource() {
    // given
    CountingCloseable resource = new CountingCloseable();
    TestResponse response = new TestResponse(resource);
    assertThat(response.isClosed()).isFalse();

    // when
    response.close();

    // then
    assertThat(resource.closeCount).isEqualTo(1);
    assertThat(response.isClosed()).isTrue();
  }

  @Test
  public void shouldCloseResourceOnlyOnce() {
    // given
    CountingCloseable resource = new CountingCloseable();
    TestResponse response = new TestResponse(resource);

    // when
    response.close();
    response.close();
    response.close();

    // then
    assertThat(resource.closeCount).isEqualTo(1);
  }

  @Test
  public void shouldNotRetryFailedClose() {
    // given
    CountingCloseable resource = new CountingCloseable();
    resource.failure = new IOException("expected");
    TestResponse response = new TestResponse(resource);

    // when
    assertThatThrownBy(response::close)
      .isInstanceOf(ConnectorException.class)
      .hasCauseInstanceOf(IOException.class);
    response.close();

    // then
    assertThat(resource.closeCount).isEqualTo(1);
    assertThat(response.isClosed()).isTrue();
  }

  @Test
  public void shouldIgnoreMissingResource() {
    // given
    TestResponse response = new TestResponse(null);

    // when
    response.close();

    // then
    assertThat(response.isClosed()).isTrue();
  }

  protected static class CountingCloseable implements Closeable {

    protected int closeCount = 0;
    protected IOException failure;

    @Override
    public void close() throws IOException {
      closeCount++;
      if (failure != null) {
        throw failure;
      }
    }
  }

  protected static class TestResponse extends AbstractCloseableConnectorResponse {

    protected Closeable resource;

    public TestResponse(Closeable resource) {
      this.resource = resource;
    }

    @Override
    protected Closeable getClosable() {
      return resource;
    }

    @Override
    protected void collectResponseParameters(Map<String, Object> responseParameters) {
      // no parameters
    }
  }

}
