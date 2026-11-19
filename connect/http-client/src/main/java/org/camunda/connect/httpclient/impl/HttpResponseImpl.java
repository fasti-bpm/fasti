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
package org.camunda.connect.httpclient.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.camunda.commons.utils.IoUtil;
import org.camunda.connect.httpclient.HttpResponse;
import org.camunda.connect.impl.AbstractCloseableConnectorResponse;

public class HttpResponseImpl extends AbstractCloseableConnectorResponse implements HttpResponse {

  private static final HttpConnectorLogger LOG = HttpLogger.HTTP_LOGGER;

  protected ClassicHttpResponse httpResponse;

  public HttpResponseImpl(ClassicHttpResponse httpResponse) {
    this.httpResponse = httpResponse;
  }

  public Integer getStatusCode() {
    return getResponseParameter(PARAM_NAME_STATUS_CODE);
  }

  public String getResponse() {
    return getResponseParameter(PARAM_NAME_RESPONSE);
  }

  public Map<String, String> getHeaders() {
    return getResponseParameter(PARAM_NAME_RESPONSE_HEADERS);
  }

  public String getHeader(String field) {
    Map<String, String> headers = getHeaders();
    if (headers != null) {
      return headers.get(field);
    } else {
      return null;
    }
  }

  protected void collectResponseParameters(Map<String, Object> responseParameters) {
    try {
      responseParameters.put(PARAM_NAME_STATUS_CODE, httpResponse.getCode());
      collectResponseHeaders();

      HttpEntity entity = httpResponse.getEntity();
      if (entity != null) {
        try {
          String response = IoUtil.inputStreamAsString(entity.getContent());
          responseParameters.put(PARAM_NAME_RESPONSE, response);
        } catch (IOException e) {
          throw LOG.unableToReadResponse(e);
        }
      }
    } finally {
      // the response is either completely buffered or cannot be read at all,
      // in both cases the underlying HTTP response is not needed anymore
      closeQuietly();
    }
  }

  /**
   * Releases the underlying HTTP response, if that did not happen yet. A
   * failure is logged and not propagated.
   */
  protected void closeQuietly() {
    try {
      close();
    } catch (RuntimeException e) {
      LOG.unableToCloseResponse(e);
    }
  }

  protected void collectResponseHeaders() {
    Map<String, String> headers = new HashMap<>();
    for (Header header : httpResponse.getHeaders()) {
      headers.put(header.getName(), header.getValue());
    }
    responseParameters.put(PARAM_NAME_RESPONSE_HEADERS, headers);
  }

  protected Closeable getClosable() {
    return httpResponse;
  }

}
