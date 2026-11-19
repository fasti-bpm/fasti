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

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.camunda.connect.spi.CloseableConnectorResponse;

/**
 * This class is a base class for implementing closeable connector responses
 * based on {@link Closeable}.
 *
 * @author Daniel Meyer
 *
 */
public abstract class AbstractCloseableConnectorResponse extends AbstractConnectorResponse implements CloseableConnectorResponse {

  private static final ConnectCoreLogger LOG = ConnectLogger.CORE_LOGGER;

  /**
   * Implements the default close behavior
   */
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /**
   * Closes the underlying resource. The resource is closed at most once:
   * further invocations have no effect, also if the first attempt failed.
   */
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    Closeable closable = getClosable();
    try {
      LOG.closingResponse(this);
      if (closable != null) {
        closable.close();
      }
      LOG.successfullyClosedResponse(this);
    } catch (IOException e) {
      throw LOG.exceptionWhileClosingResponse(e);
    }
  }

  /**
   * @return true if {@link #close()} has been invoked
   */
  public boolean isClosed() {
    return closed.get();
  }

  /**
   * Allows subclasses to provide the closeable resource.
   * @return the {@link Closeable} resource
   */
  protected abstract Closeable getClosable();

}
