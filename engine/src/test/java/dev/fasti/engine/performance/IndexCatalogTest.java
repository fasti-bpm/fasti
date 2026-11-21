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
package dev.fasti.engine.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.fasti.engine.performance.impl.IndexCatalog;
import org.junit.Test;

/**
 * The catalog queries without a database: what they bind, that each has a query timeout and that
 * a timeout ends the read (no further query, no metadata fallback).
 */
public class IndexCatalogTest {

  protected final List<String> prepared = new ArrayList<>();
  protected final List<Object> bound = new ArrayList<>();
  protected final List<Integer> timeouts = new ArrayList<>();

  @Test
  public void shouldStopAtTheFirstTimeoutWithoutTryingTheNextQuery() {
    IndexCatalog catalog = new IndexCatalog(IndexCatalog.MYSQL, null, null);

    assertThatThrownBy(() -> catalog.readFromCatalog(connection(new SQLTimeoutException("timeout")), Arrays.asList("ACT_RU_JOB")))
        .isInstanceOf(SQLTimeoutException.class)
        .satisfies(e -> assertThat(IndexCatalog.isTimeout(e)).isTrue());

    // MySQL and MariaDB have three variants; after a timeout the next one would wait as well
    assertThat(prepared).hasSize(1);
    assertThat(timeouts).containsExactly(IndexCatalog.QUERY_TIMEOUT_SECONDS);
  }

  @Test
  public void shouldTryTheNextMySqlVariantWhenAQueryIsRefused() {
    IndexCatalog catalog = new IndexCatalog(IndexCatalog.MYSQL, null, null);

    assertThatThrownBy(() -> catalog.readFromCatalog(connection(new SQLException("Unknown column 'IGNORED'", "42S22", 1054)),
        Arrays.asList("ACT_RU_JOB"))).isInstanceOf(SQLException.class).satisfies(e -> assertThat(IndexCatalog.isTimeout(e)).isFalse());

    assertThat(prepared).hasSize(3);
    assertThat(timeouts).containsOnly(IndexCatalog.QUERY_TIMEOUT_SECONDS);
  }

  @Test
  public void shouldRecognizeTheTimeoutsOfTheDrivers() {
    // PostgreSQL and Db2: query cancelled
    assertThat(IndexCatalog.isTimeout(new SQLException("canceling statement due to statement timeout", "57014"))).isTrue();
    // SQL Server, H2
    assertThat(IndexCatalog.isTimeout(new SQLException("timeout", "HYT00"))).isTrue();
    // MariaDB Connector/J
    assertThat(IndexCatalog.isTimeout(new SQLException("Query execution was interrupted", "70100"))).isTrue();
    assertThat(IndexCatalog.isTimeout(new RuntimeException(new SQLTimeoutException()))).isTrue();
    assertThat(IndexCatalog.isTimeout(new SQLException("permission denied", "42501"))).isFalse();
  }

  @Test
  public void shouldCompareMySqlCatalogColumnsWithoutFunctions() throws SQLException {
    IndexCatalog catalog = new IndexCatalog(IndexCatalog.MYSQL, "Engine", null);

    catalog.readFromCatalog(connection(null), Arrays.asList("ACT_RU_JOB", "ACT_ID_INFO"));

    String sql = prepared.get(0);
    // a function around TABLE_SCHEMA or TABLE_NAME makes the server open the tables of every database
    assertThat(sql).contains("where TABLE_SCHEMA = ? and TABLE_NAME in (?, ?, ?, ?)").doesNotContainIgnoringCase("upper(");
    // the schema as configured, each table as the engine writes it and in lower case
    assertThat(bound).containsExactly("Engine", "ACT_RU_JOB", "act_ru_job", "ACT_ID_INFO", "act_id_info");

    prepared.clear();
    new IndexCatalog(IndexCatalog.MYSQL, null, null).readFromCatalog(connection(null), Arrays.asList("ACT_RU_JOB"));
    assertThat(prepared.get(0)).contains("where TABLE_SCHEMA = database() and TABLE_NAME in (?, ?)");
  }

  @Test
  public void shouldReadTheCatalogOfTheDatabaseInASqlServerTablePrefix() throws SQLException {
    new IndexCatalog(IndexCatalog.MSSQL, null, "other].db.engine.").readFromCatalog(connection(null), Arrays.asList("ACT_RU_JOB"));

    assertThat(prepared.get(0)).contains("from [other]].db].sys.indexes i").contains("join [other]].db].sys.index_columns ic");
    assertThat(bound).containsExactly("ENGINE", "ACT_RU_JOB");
  }

  @Test
  public void shouldReadAccessMethodAndNullOrderOnPostgres() throws SQLException {
    new IndexCatalog(IndexCatalog.POSTGRES, null, null).readFromCatalog(connection(null), Arrays.asList("ACT_RU_JOB"));

    assertThat(prepared.get(0)).contains("am.amname").contains("x.indoption[(k.ord - 1)::int]");
  }

  /** A connection whose statements fail with the given exception on execution, or return no rows. */
  protected Connection connection(SQLException failure) {
    return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
        (proxy, method, args) -> {
          if ("prepareStatement".equals(method.getName())) {
            prepared.add((String) args[0]);
            return statement(failure);
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  protected PreparedStatement statement(SQLException failure) {
    return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { PreparedStatement.class },
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "setQueryTimeout":
              timeouts.add((Integer) args[0]);
              return null;
            case "setString":
              bound.add(args[1]);
              return null;
            case "executeQuery":
              if (failure != null) {
                throw failure;
              }
              return emptyResultSet();
            case "close":
              return null;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        });
  }

  protected java.sql.ResultSet emptyResultSet() {
    return (java.sql.ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { java.sql.ResultSet.class },
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "next":
              return false;
            case "close":
              return null;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        });
  }
}
