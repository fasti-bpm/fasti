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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
      assertThat(sql).as(type).endsWith(" order by TIME_ desc, CHAIN_ID_ desc, SEQ_ desc limit ? offset ?");
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
        .endsWith(" order by TIME_ desc, CHAIN_ID_ desc, SEQ_ desc");
    assertThat(parameters).isEmpty();
  }

  @Test
  public void shouldStopCountingAtALimit() {
    for (String type : List.of("h2", "postgres", "mysql", "mariadb")) {
      List<Object> parameters = new ArrayList<>();
      AuditEventQueryImpl query = query();
      query.actor("alice");
      assertThat(AuditStore.countSql(settings(type), query, null, 10_000, parameters)).as(type)
          .isEqualTo("select count(*) from (select 1 as X_ from FASTI_AUDIT_EVENT where ACTOR_ = ? limit 10000) T_");
      assertThat(parameters).as(type).containsExactly("alice");
    }
    List<Object> parameters = new ArrayList<>();
    assertThat(AuditStore.countSql(settings("mssql"), query(), List.of("t1"), 500, parameters))
        .isEqualTo("select count(*) from (select top 500 1 as X_ from FASTI_AUDIT_EVENT"
            + " where (TENANT_ID_ is null or TENANT_ID_ in (?))) T_");
    assertThat(parameters).containsExactly("t1");
    for (String type : List.of("oracle", "db2")) {
      assertThat(AuditStore.countSql(settings(type), query(), null, 500, new ArrayList<>())).as(type)
          .isEqualTo("select count(*) from (select 1 as X_ from FASTI_AUDIT_EVENT fetch first 500 rows only) T_");
    }
  }

  @Test
  public void shouldCountEverythingWithoutALimit() {
    for (long limit : new long[] { 0, -1 }) {
      assertThat(AuditStore.countSql(settings("postgres"), query(), null, limit, new ArrayList<>()))
          .isEqualTo("select count(*) from FASTI_AUDIT_EVENT");
    }
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

  @Test
  public void shouldTellATimeoutFromOtherFailures() {
    assertThat(AuditStore.isTimeout(new SQLTimeoutException("timed out"))).isTrue();
    assertThat(AuditStore.isTimeout(new SQLException("canceling statement due to user request", "57014"))).isTrue();
    assertThat(AuditStore.isTimeout(new SQLException("Lock wait timeout exceeded", "HY000", 1205))).isTrue();
    assertThat(AuditStore.isTimeout(new SQLException("duplicate key", "23505"))).isFalse();
  }

  // locking the head

  static Connection connection(int updatedRows, boolean headThere, List<String> statements) throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      statements.add(sql);
      PreparedStatement statement = mock(PreparedStatement.class);
      when(statement.executeUpdate()).thenReturn(updatedRows);
      ResultSet result = mock(ResultSet.class);
      when(result.next()).thenReturn(headThere, false);
      when(result.getLong(1)).thenReturn(7L);
      when(result.getString(2)).thenReturn("h".repeat(64));
      when(statement.executeQuery()).thenReturn(result);
      return statement;
    });
    return connection;
  }

  @Test
  public void shouldLockAndReadTheHeadInOneStatementWhereTheDatabaseCan() throws SQLException {
    for (String type : List.of("postgres", "mysql", "mariadb", "oracle")) {
      List<String> statements = new ArrayList<>();
      AuditStore.Head head = AuditStore.lockAndReadHead(connection(0, true, statements), settings(type), "node/0", 60);
      assertThat(head.sequence).as(type).isEqualTo(7);
      assertThat(statements).as(type)
          .containsExactly("select SEQ_, HASH_ from FASTI_AUDIT_HEAD where CHAIN_ID_ = ? for update");
    }
  }

  @Test
  public void shouldLockTheHeadWithAnUpdateElsewhere() throws SQLException {
    for (String type : List.of("mssql", "db2")) {
      List<String> statements = new ArrayList<>();
      AuditStore.Head head = AuditStore.lockAndReadHead(connection(1, true, statements), settings(type), "node/0", 60);
      assertThat(head.sequence).as(type).isEqualTo(7);
      assertThat(statements).as(type).containsExactly("update FASTI_AUDIT_HEAD set SEQ_ = SEQ_ where CHAIN_ID_ = ?",
          "select SEQ_, HASH_ from FASTI_AUDIT_HEAD where CHAIN_ID_ = ?");
    }
  }

  @Test
  public void shouldReadTheHeadAlsoWhenTheUpdateCountsNoRow() throws SQLException {
    // MySQL Connector/J with useAffectedRows=true counts changed rows: none for set SEQ_ = SEQ_
    AuditStore.Head head = AuditStore.lockAndReadHead(connection(0, true, new ArrayList<>()), settings("mssql"), "node/0", 60);
    assertThat(head).isNotNull();
    assertThat(head.hash).isEqualTo("h".repeat(64));
    assertThat(AuditStore.lockAndReadHead(connection(0, false, new ArrayList<>()), settings("mssql"), "node/0", 60)).isNull();
  }

  // record ids

  @Test
  public void shouldMakeTimeOrderedIds() {
    long before = System.currentTimeMillis();
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 20_000; i++) {
      ids.add(AuditStore.newId());
    }
    long after = System.currentTimeMillis();
    List<String> sorted = new ArrayList<>(ids);
    sorted.sort(null);
    assertThat(ids).isEqualTo(sorted).doesNotHaveDuplicates();
    for (String id : List.of(ids.get(0), ids.get(ids.size() - 1))) {
      UUID uuid = UUID.fromString(id);
      assertThat(uuid.toString()).isEqualTo(id);
      assertThat(uuid.version()).isEqualTo(7);
      assertThat(uuid.variant()).isEqualTo(2);
      long millis = uuid.getMostSignificantBits() >>> 16;
      // 20,000 ids in one millisecond would run 4 milliseconds ahead of the clock at most
      assertThat(millis).isBetween(before, after + 5);
    }
  }
}
