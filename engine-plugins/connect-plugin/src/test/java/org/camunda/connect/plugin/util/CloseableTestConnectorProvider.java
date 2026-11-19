/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.connect.plugin.util;

import org.camunda.connect.spi.Connector;
import org.camunda.connect.spi.ConnectorProvider;

public class CloseableTestConnectorProvider implements ConnectorProvider {

  public String getConnectorId() {
    return CloseableTestConnector.ID;
  }

  public Connector<?> createConnectorInstance() {
    return new CloseableTestConnector(CloseableTestConnector.ID);
  }

}
