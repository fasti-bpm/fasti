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
package org.camunda.connect.plugin.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.connect.impl.AbstractConnector;
import org.camunda.connect.impl.AbstractConnectorRequest;
import org.camunda.connect.spi.CloseableConnectorResponse;

/**
 * A connector whose responses must be closed and that records all responses
 * it handed out.
 */
public class CloseableTestConnector extends AbstractConnector<CloseableTestConnector.Request, CloseableTestConnector.Response> {

  public static final String ID = "closeableTestConnector";

  public static final List<Response> responses = new ArrayList<Response>();
  public static final Map<String, Object> responseParameters = new HashMap<String, Object>();
  public static RuntimeException closeFailure;

  public CloseableTestConnector(String connectorId) {
    super(connectorId);
  }

  public static void reset() {
    responses.clear();
    responseParameters.clear();
    closeFailure = null;
  }

  public Request createRequest() {
    return new Request(this);
  }

  public Response execute(Request request) {
    Response response = new Response(new HashMap<String, Object>(responseParameters), closeFailure);
    responses.add(response);
    return response;
  }

  public static class Request extends AbstractConnectorRequest<Response> {

    public Request(CloseableTestConnector connector) {
      super(connector);
    }
  }

  public static class Response implements CloseableConnectorResponse {

    protected Map<String, Object> parameters;
    protected RuntimeException closeFailure;

    public int closeCount = 0;
    public int readCountAfterClose = 0;

    public Response(Map<String, Object> parameters, RuntimeException closeFailure) {
      this.parameters = parameters;
      this.closeFailure = closeFailure;
    }

    public Map<String, Object> getResponseParameters() {
      if (closeCount > 0) {
        readCountAfterClose++;
      }
      return parameters;
    }

    @SuppressWarnings("unchecked")
    public <V> V getResponseParameter(String name) {
      return (V) getResponseParameters().get(name);
    }

    public void close() {
      closeCount++;
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }

}
