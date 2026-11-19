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
package org.camunda.bpm.spring.boot.starter.property;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import static org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties.joinOn;

public class RestApiProperty {

  @NestedConfigurationProperty
  protected FetchAndLockProperties fetchAndLock = new FetchAndLockProperties();

  /**
   * The REST API does not authenticate requests unless an authentication
   * filter is registered. A warning is logged on startup if no such filter is
   * found. Set to <code>false</code> to switch the warning off, e.g. if the
   * REST API is protected by other means.
   */
  protected boolean authenticationWarningEnabled = true;

  public FetchAndLockProperties getFetchAndLock() {
    return fetchAndLock;
  }

  public void setFetchAndLock(FetchAndLockProperties fetchAndLock) {
    this.fetchAndLock = fetchAndLock;
  }

  public boolean isAuthenticationWarningEnabled() {
    return authenticationWarningEnabled;
  }

  public void setAuthenticationWarningEnabled(boolean authenticationWarningEnabled) {
    this.authenticationWarningEnabled = authenticationWarningEnabled;
  }

  @Override
  public String toString() {
    return joinOn(this.getClass())
      .add("fetchAndLock='" + fetchAndLock + '\'')
      .add("authenticationWarningEnabled='" + authenticationWarningEnabled + '\'')
      .toString();
  }

}
