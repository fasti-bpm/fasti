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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import dev.fasti.engine.workload.TaskLoad;
import dev.fasti.engine.workload.TaskWorkload;
import dev.fasti.engine.workload.TaskWorkloadQuery;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.util.ClockUtil;

/**
 * Reads the workload in one command: the totals, the unclaimed tasks without a group, and one
 * grouped statement each for the groups and the users asked for.
 */
public class TaskWorkloadQueryImpl implements TaskWorkloadQuery {

  static final String SELECT_TOTALS = "selectFastiTaskLoadTotals";
  static final String SELECT_BY_ASSIGNEE = "selectFastiTaskLoadByAssignee";
  static final String SELECT_BY_GROUP = "selectFastiTaskLoadByCandidateGroup";

  protected final ProcessEngineConfigurationImpl configuration;
  protected List<String> groupIds = new ArrayList<>();
  protected List<String> userIds = new ArrayList<>();
  protected Date overdueBefore;
  protected String[] tenantIds;

  public TaskWorkloadQueryImpl(ProcessEngineConfigurationImpl configuration) {
    this.configuration = configuration;
  }

  @Override
  public TaskWorkloadQuery groupIdIn(Collection<String> groupIds) {
    this.groupIds = distinct("groupIds", groupIds);
    return this;
  }

  @Override
  public TaskWorkloadQuery userIdIn(Collection<String> userIds) {
    this.userIds = distinct("userIds", userIds);
    return this;
  }

  @Override
  public TaskWorkloadQuery overdueBefore(Date overdueBefore) {
    this.overdueBefore = overdueBefore;
    return this;
  }

  @Override
  public TaskWorkloadQuery tenantIdIn(String... tenantIds) {
    if (tenantIds != null) {
      for (String tenantId : tenantIds) {
        if (tenantId == null) {
          throw new BadUserRequestException("tenantIds contains null");
        }
      }
    }
    this.tenantIds = tenantIds == null || tenantIds.length == 0 ? null : tenantIds.clone();
    return this;
  }

  protected static List<String> distinct(String name, Collection<String> ids) {
    if (ids == null) {
      return new ArrayList<>();
    }
    LinkedHashSet<String> distinct = new LinkedHashSet<>();
    for (String id : ids) {
      if (id == null || id.isEmpty()) {
        throw new BadUserRequestException(name + " contains an empty id");
      }
      distinct.add(id);
    }
    if (distinct.size() > MAX_IDS) {
      throw new BadUserRequestException(name + " has more than " + MAX_IDS + " entries");
    }
    return new ArrayList<>(distinct);
  }

  @Override
  public TaskWorkload read() {
    return configuration.getCommandExecutorTxRequired().execute(this::read);
  }

  protected TaskWorkload read(CommandContext commandContext) {
    Date before = overdueBefore != null ? overdueBefore : ClockUtil.getCurrentTime();

    List<TaskLoadEntity> totalRows = select(commandContext, SELECT_TOTALS, base(before));
    TaskLoadEntity totals = totalRows.isEmpty() || totalRows.get(0) == null ? new TaskLoadEntity() : totalRows.get(0);

    WorkloadTaskQuery orphans = base(before);
    orphans.taskUnassigned();
    orphans.withoutCandidateGroups();
    orphans.prepare(commandContext);
    long orphaned = commandContext.getTaskManager().findTaskCountByQueryCriteria(orphans);

    List<TaskLoad> groups = new ArrayList<>();
    if (!groupIds.isEmpty()) {
      WorkloadTaskQuery query = base(before);
      query.taskCandidateGroupIn(new ArrayList<>(groupIds));
      query.includeAssignedTasks();
      query.setLoadGroupIds(groupIds);
      groups = inOrder(groupIds, select(commandContext, SELECT_BY_GROUP, query));
    }

    List<TaskLoad> users = new ArrayList<>();
    if (!userIds.isEmpty()) {
      WorkloadTaskQuery query = base(before);
      query.taskAssigneeIn(userIds.toArray(new String[0]));
      users = inOrder(userIds, select(commandContext, SELECT_BY_ASSIGNEE, query));
    }
    return new TaskWorkloadImpl(totals, orphaned, groups, users);
  }

  protected WorkloadTaskQuery base(Date before) {
    WorkloadTaskQuery query = new WorkloadTaskQuery(before);
    if (tenantIds != null) {
      query.tenantIdIn(tenantIds);
    }
    return query;
  }

  /** Applies the checks of a task query, then runs the grouped statement around it. */
  @SuppressWarnings("unchecked")
  protected List<TaskLoadEntity> select(CommandContext commandContext, String statement, WorkloadTaskQuery query) {
    query.prepare(commandContext);
    commandContext.getAuthorizationManager().configureTaskQuery(query);
    commandContext.getTenantManager().configureQuery(query);
    return (List<TaskLoadEntity>) commandContext.getDbEntityManager()
        .selectListWithRawParameter(statement, query, 0, Integer.MAX_VALUE);
  }

  protected static List<TaskLoad> inOrder(List<String> ids, List<TaskLoadEntity> rows) {
    Map<String, TaskLoadEntity> byId = new HashMap<>();
    for (TaskLoadEntity row : rows) {
      byId.put(row.getId(), row);
    }
    List<TaskLoad> result = new ArrayList<>(ids.size());
    for (String id : ids) {
      TaskLoadEntity row = byId.get(id);
      result.add(row != null ? row : new TaskLoadEntity(id));
    }
    return result;
  }
}
