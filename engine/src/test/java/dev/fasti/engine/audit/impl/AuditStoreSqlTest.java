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
package dev.fasti.engine.audit.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.Test;

/** The statements the audit trail issues: pages are cut by the database, in the dialect of each one. */
public class AuditStoreSqlTest {

  static AuditSettings settings(String databaseType) {
    AuditSettings settings = new AuditSettings("node", 2, null, null, null, null, 0, 0);
    settings.setDatabaseType(databaseType);
    return settings;
  }

  static AuditEventQueryImpl query() {
    return new AuditEventQueryImpl(null, null);
  }

  @Test
  public void shouldPageWithLimitAndOffset() {
    for (String type : List.of("h2", "postgres", "mysql", "mariadb")) {
      List<Object> parameters = new ArrayList<>();
      String sql = AuditStore.selectSql(settings(type), query(), null, 2_000_000_000L, 50, parameters);
      assertThat(sql).as(type).endsWith(" order by TIME_ desc, CHAIN_ID_ asc, SEQ_ desc limit ? offset ?");
      assertThat(parameters).as(type).containsExactly(50L, 2_000_000_000L);
    }
  }

  @Test
  public void shouldPageWithOffsetAndFetch() {
    for (String type : List.of("oracle", "mssql", "db2")) {
      List<Object> parameters = new ArrayList<>();
      AuditEventQueryImpl query = query();
      query.oldestFirst();
      query.actor("alice");
      String sql = AuditStore.selectSql(settings(type), query, null, 100, 25, parameters);
      assertThat(sql).as(type)
          .endsWith(" where ACTOR_ = ? order by TIME_ asc, CHAIN_ID_ asc, SEQ_ asc offset ? rows fetch next ? rows only");
      assertThat(parameters).as(type).containsExactly("alice", 100L, 25L);
    }
  }

  @Test
  public void shouldLimitAnExportInTheDatabaseToo() {
    List<Object> parameters = new ArrayList<>();
    assertThat(AuditStore.selectSql(settings("postgres"), query(), null, 0, 100_000, parameters)).endsWith(" limit ? offset ?");
    assertThat(parameters).containsExactly(100_000L, 0L);
  }

  @Test
  public void shouldNotPageAnUnlimitedList() {
    List<Object> parameters = new ArrayList<>();
    assertThat(AuditStore.selectSql(settings("h2"), query(), null, 0, Integer.MAX_VALUE, parameters))
        .endsWith(" order by TIME_ desc, CHAIN_ID_ asc, SEQ_ desc");
    assertThat(parameters).isEmpty();
  }

  @Test
  public void shouldRestrictToTenantsBeforeOtherFilters() {
    List<Object> parameters = new ArrayList<>();
    AuditEventQueryImpl query = query();
    query.tenantId("t2");
    String sql = AuditStore.selectSql(settings("h2"), query, List.of("t1", "t3"), 0, 10, parameters);
    assertThat(sql).contains(" where (TENANT_ID_ is null or TENANT_ID_ in (?, ?)) and TENANT_ID_ = ? order by ");
    assertThat(parameters).containsExactly("t1", "t3", "t2", 10L, 0L);

    parameters.clear();
    assertThat(AuditStore.where(query(), List.of(), parameters)).isEqualTo(" where TENANT_ID_ is null");
    assertThat(parameters).isEmpty();
    assertThat(AuditStore.where(query(), null, parameters)).isEmpty();
  }

  @Test
  public void shouldKeepWhatTheDatabaseSaysOutOfTheMessage() {
    SQLException cause = new SQLException("relation \"fasti_audit_event\" does not exist at character 15", "42P01");
    ProcessEngineException failure = AuditStore.failure("read records", "chain node/0", cause);
    assertThat(failure.getMessage()).isEqualTo("The audit trail could not read records. The cause is in the log of the server.");
    assertThat(failure.getCause()).isSameAs(cause);
  }
}
