/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.connect.plugin.impl;

import org.camunda.commons.logging.BaseLogger;

public class ConnectPluginLogger extends BaseLogger {

  public static final String PROJECT_CODE = "CNCT-PLUGIN";

  public static final ConnectPluginLogger LOGGER = BaseLogger.createLogger(ConnectPluginLogger.class, PROJECT_CODE, "org.camunda.connect.plugin", "01");

  public void unableToCloseConnectorResponse(String connectorId, Exception cause) {
    logWarn(
        "001", "Unable to close the response of connector '{}': {}", connectorId, cause.getMessage(), cause);
  }

}
