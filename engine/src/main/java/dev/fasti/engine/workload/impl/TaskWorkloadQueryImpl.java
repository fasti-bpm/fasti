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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.fasti.engine.workload.TaskLoad;
import dev.fasti.engine.workload.TaskWorkload;
import dev.fasti.engine.workload.TaskWorkloadQuery;
import dev.fasti.engine.workload.WorkloadNotAvailableException;
import org.apache.ibatis.session.Configuration;
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

  private static final Pattern MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern TRAILING_SPACES = Pattern.compile(" +$");

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
    if (tenantIds == null || tenantIds.length == 0) {
      this.tenantIds = null;
    } else {
      // every tenant id is bound in each of the statements: bounded like the other lists
      this.tenantIds = distinct("tenantIds", Arrays.asList(tenantIds)).toArray(new String[0]);
    }
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
      if (id.length() > MAX_ID_LENGTH) {
        throw new BadUserRequestException(name + " contains an id longer than " + MAX_ID_LENGTH + " characters");
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
    ensureStatementsMapped();
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

  /**
   * The statements live in a mapping file of their own; an application that replaced the
   * default {@code mappings.xml} with a copy that lacks it would otherwise get a MyBatis error
   * that does not say what to do.
   */
  protected void ensureStatementsMapped() {
    Configuration mybatis = configuration.getSqlSessionFactory().getConfiguration();
    for (String statement : new String[] { SELECT_TOTALS, SELECT_BY_ASSIGNEE, SELECT_BY_GROUP }) {
      String mapped = configuration.getDbSqlSessionFactory().mapStatement(statement);
      if (!mybatis.hasStatement(mapped)) {
        throw new WorkloadNotAvailableException("The workload statements are not mapped: the MyBatis mappings of "
            + "the engine lack dev/fasti/engine/workload/TaskWorkload.xml. Add it to the custom mappings.xml.");
      }
    }
  }

  /**
   * One entry per id asked for, in that order, each with the id that was asked for.
   *
   * <p>The database compares the ids with its collation: on a case- or accent-insensitive one
   * (the defaults of SQL Server, MySQL and MariaDB) a request for {@code sales} also selects the
   * tasks of {@code Sales}, and the row carries the stored {@code Sales}. A row whose key is no
   * requested id can only come from such a comparison, so an id without a row of its own takes
   * the row with the same key ignoring case, accents and trailing spaces. Under a binary or
   * case-sensitive collation every row carries a requested id and this never applies.</p>
   */
  protected static List<TaskLoad> inOrder(List<String> ids, List<TaskLoadEntity> rows) {
    Set<String> requested = new HashSet<>(ids);
    Map<String, TaskLoadEntity> exact = new HashMap<>();
    Map<String, TaskLoadEntity> loose = new HashMap<>();
    for (TaskLoadEntity row : rows) {
      if (row.getId() == null) {
        continue;
      }
      if (requested.contains(row.getId())) {
        exact.put(row.getId(), row);
      } else {
        loose.merge(looseKey(row.getId()), row, TaskLoadEntity::sum);
      }
    }
    List<TaskLoad> result = new ArrayList<>(ids.size());
    for (String id : ids) {
      TaskLoadEntity row = exact.get(id);
      if (row == null) {
        row = loose.get(looseKey(id));
      }
      result.add(row != null ? row.withId(id) : new TaskLoadEntity(id));
    }
    return result;
  }

  /** The key two ids share when a case- and accent-insensitive collation takes them as equal. */
  protected static String looseKey(String id) {
    String withoutMarks = MARKS.matcher(Normalizer.normalize(id, Normalizer.Form.NFD)).replaceAll("");
    return TRAILING_SPACES.matcher(withoutMarks).replaceAll("").toLowerCase(Locale.ROOT);
  }
}
