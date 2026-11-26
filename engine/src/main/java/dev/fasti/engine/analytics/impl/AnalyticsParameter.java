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
package dev.fasti.engine.analytics.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The parameter object of the analytics statements. */
public class AnalyticsParameter {

  /** An activity and the start time before which a run counts as slower than the P90. */
  public static class ActivitySince {
    protected final String activityId;
    protected final Date since;

    public ActivitySince(String activityId, Date since) {
      this.activityId = activityId;
      this.since = since;
    }

    public String getActivityId() {
      return activityId;
    }

    public Date getSince() {
      return since;
    }
  }

  protected String processDefinitionKey;
  protected List<String> definitionIds = new ArrayList<>();
  protected List<String> tenantIds = new ArrayList<>();
  protected boolean tenantCheck;
  protected List<String> authenticatedTenantIds = new ArrayList<>();
  protected Date after;
  protected Date before;
  protected Long target;
  protected String activityId;
  protected List<ActivitySince> activitySince = new ArrayList<>();
  protected List<Date> periodEnds = new ArrayList<>();
  protected Date periodStart;
  protected Date periodEnd;
  protected String periodOfEndSql;
  protected String periodOfStartSql;
  protected String databaseType;
  protected int maxResults;
  protected int statementTimeoutSeconds;

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public List<String> getDefinitionIds() {
    return definitionIds;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public boolean isTenantCheck() {
    return tenantCheck;
  }

  public List<String> getAuthenticatedTenantIds() {
    return authenticatedTenantIds;
  }

  public Date getAfter() {
    return after;
  }

  public Date getBefore() {
    return before;
  }

  public Long getTarget() {
    return target;
  }

  public String getActivityId() {
    return activityId;
  }

  public List<ActivitySince> getActivitySince() {
    return activitySince;
  }

  public List<Date> getPeriodEnds() {
    return periodEnds;
  }

  /** The database type of the engine ({@code postgres}, {@code mysql}, {@code oracle}, {@code mssql}, {@code db2}, {@code h2}). */
  public String getDatabaseType() {
    return databaseType;
  }

  /** The row limit of the limited statements. */
  public int getMaxResults() {
    return maxResults;
  }

  /** The query timeout of each statement in seconds ({@link AnalyticsStatementTimeout}); 0: none of its own. */
  public int getStatementTimeoutSeconds() {
    return statementTimeoutSeconds;
  }

  public Date getPeriodStart() {
    return periodStart;
  }

  public Date getPeriodEnd() {
    return periodEnd;
  }

  /** The bucket expression of an activity duration; constant numbers only. */
  public String getActivityBucketSql() {
    return DurationBuckets.sql("A.DURATION_");
  }

  /** The bucket expression of a process instance duration; constant numbers only. */
  public String getInstanceBucketSql() {
    return DurationBuckets.sql("P.DURATION_");
  }

  /** The period of an end time: compares with the bound period ends, numbers and parameters only. */
  public String getPeriodOfEndSql() {
    return periodOfEndSql;
  }

  /** The period of a start time, like {@link #getPeriodOfEndSql()}. */
  public String getPeriodOfStartSql() {
    return periodOfStartSql;
  }

  static String periodSql(String column, int periods) {
    StringBuilder sql = new StringBuilder("case");
    for (int period = 0; period < periods; period++) {
      sql.append(" when ").append(column).append(" < #{periodEnds[").append(period).append("]} then ").append(period);
    }
    return sql.append(" end").toString();
  }
}
