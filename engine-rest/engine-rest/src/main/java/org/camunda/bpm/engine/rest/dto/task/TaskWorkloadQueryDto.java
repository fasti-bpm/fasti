/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package org.camunda.bpm.engine.rest.dto.task;

import java.util.Date;
import java.util.List;

/** Fasti: which groups and users the workload is asked for. */
public class TaskWorkloadQueryDto {

  protected List<String> groupIdIn;
  protected List<String> userIdIn;
  protected Date overdueBefore;
  protected List<String> tenantIdIn;

  public List<String> getGroupIdIn() {
    return groupIdIn;
  }

  public void setGroupIdIn(List<String> groupIdIn) {
    this.groupIdIn = groupIdIn;
  }

  public List<String> getUserIdIn() {
    return userIdIn;
  }

  public void setUserIdIn(List<String> userIdIn) {
    this.userIdIn = userIdIn;
  }

  public Date getOverdueBefore() {
    return overdueBefore;
  }

  public void setOverdueBefore(Date overdueBefore) {
    this.overdueBefore = overdueBefore;
  }

  public List<String> getTenantIdIn() {
    return tenantIdIn;
  }

  public void setTenantIdIn(List<String> tenantIdIn) {
    this.tenantIdIn = tenantIdIn;
  }
}
