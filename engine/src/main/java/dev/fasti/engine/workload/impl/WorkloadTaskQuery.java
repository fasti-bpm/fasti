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
package dev.fasti.engine.workload.impl;

import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;

/**
 * The task query the grouped statements are built around: its criteria, authorization check and
 * tenant check select the tasks, the statements only group them.
 */
public class WorkloadTaskQuery extends TaskQueryImpl {

  private static final long serialVersionUID = 1L;

  protected Date loadOverdueBefore;
  protected List<String> loadGroupIds;

  public WorkloadTaskQuery(Date loadOverdueBefore) {
    this.loadOverdueBefore = loadOverdueBefore;
  }

  /**
   * What a task query does before it runs: without CMMN the authorization join is an inner join,
   * so that tasks of cases, which no process definition permission covers, are not selected.
   */
  public void prepare(CommandContext commandContext) {
    decideAuthorizationJoinType(commandContext);
  }

  public Date getLoadOverdueBefore() {
    return loadOverdueBefore;
  }

  public List<String> getLoadGroupIds() {
    return loadGroupIds;
  }

  public void setLoadGroupIds(List<String> loadGroupIds) {
    this.loadGroupIds = loadGroupIds;
  }
}
