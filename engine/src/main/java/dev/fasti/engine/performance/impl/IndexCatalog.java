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
package dev.fasti.engine.performance.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the indexes of some engine tables from the catalog of the database. Reads only, with a
 * query timeout: a catalog that waits for a lock (uncommitted DDL on SQL Server, a metadata lock
 * on MySQL/MariaDB) ends the read instead of holding up the engine start. A table of which no
 * index can be seen is left out of the result (every engine table has a primary key), so that
 * the caller can tell "no such index" from "cannot tell".
 */
public class IndexCatalog {

  public static final String H2 = "h2";
  public static final String MYSQL = "mysql";
  public static final String POSTGRES = "postgres";
  public static final String ORACLE = "oracle";
  public static final String MSSQL = "mssql";
  public static final String DB2 = "db2";

  /** How long one catalog query may take. */
  public static final int QUERY_TIMEOUT_SECONDS = 10;

  protected final String databaseType;
  protected final String schema;
  protected final String namePrefix;
  /** The catalog (database) of a {@code CATALOG.SCHEMA.} table prefix, as SQL Server allows, or {@code null}. */
  protected final String catalog;
  protected int queryTimeoutSeconds = QUERY_TIMEOUT_SECONDS;

  /**
   * @param databaseType the database type of the engine (h2, mysql, postgres, oracle, mssql, db2)
   * @param databaseSchema the configured schema of the engine tables, or {@code null}
   * @param tablePrefix the configured table prefix ({@code SCHEMA.} or a name prefix), or {@code null}
   */
  public IndexCatalog(String databaseType, String databaseSchema, String tablePrefix) {
    this.databaseType = databaseType;
    String prefix = tablePrefix == null ? "" : tablePrefix.trim();
    String prefixSchema = null;
    String prefixCatalog = null;
    int dot = prefix.lastIndexOf('.');
    if (dot >= 0) {
      prefixSchema = prefix.substring(0, dot);
      prefix = prefix.substring(dot + 1);
      // CATALOG.SCHEMA. as on SQL Server
      int schemaDot = prefixSchema.lastIndexOf('.');
      if (schemaDot >= 0) {
        prefixCatalog = prefixSchema.substring(0, schemaDot);
        prefixSchema = prefixSchema.substring(schemaDot + 1);
      }
    }
    this.catalog = prefixCatalog == null || prefixCatalog.isEmpty() ? null : prefixCatalog;
    this.namePrefix = prefix.toUpperCase(Locale.ROOT);
    String configured = databaseSchema != null && !databaseSchema.trim().isEmpty() ? databaseSchema.trim() : prefixSchema;
    this.schema = configured == null || configured.isEmpty() ? null : configured;
  }

  public String getSchema() {
    return schema;
  }

  public String getNamePrefix() {
    return namePrefix;
  }

  public String getCatalog() {
    return catalog;
  }

  public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
    this.queryTimeoutSeconds = queryTimeoutSeconds;
  }

  /**
   * The indexes by table (name in upper case without the table prefix), from the catalog query of
   * the database.
   *
   * @throws SQLException if the catalog cannot be read, among them a {@link SQLTimeoutException}
   *         (see {@link #isTimeout}) when it took longer than the query timeout; on PostgreSQL the
   *         transaction of the connection is then aborted
   */
  public Map<String, List<ExistingIndex>> readFromCatalog(Connection connection, Collection<String> tables) throws SQLException {
    List<String> queries = catalogQueries(tables.size());
    if (queries.isEmpty()) {
      throw new SQLException("no catalog query for database type " + databaseType);
    }
    SQLException failure = null;
    // MySQL and MariaDB name the column that hides an index differently: the first query that
    // the server accepts is used (a refused query does not abort the transaction there)
    for (String sql : queries) {
      try {
        return readFromCatalog(connection, tables, sql);
      } catch (SQLException e) {
        if (failure != null && failure != e) {
          e.addSuppressed(failure);
        }
        failure = e;
        if (isTimeout(e)) {
          // the next query would wait for the same lock
          break;
        }
      }
    }
    throw failure;
  }

  /**
   * Whether the exception says that the statement ran into its query timeout (or was cancelled
   * for it), which JDBC drivers report as {@link SQLTimeoutException} or with an SQL state of
   * their own.
   */
  public static boolean isTimeout(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLTimeoutException) {
        return true;
      }
      if (cause instanceof SQLException) {
        String state = ((SQLException) cause).getSQLState();
        // HYT00/HYT01: timeout, HY008: operation cancelled (SQL Server, MySQL, H2); 57014: query
        // cancelled (PostgreSQL, Db2); 70100: query interrupted (MySQL/MariaDB)
        if ("HYT00".equals(state) || "HYT01".equals(state) || "HY008".equals(state) || "57014".equals(state)
            || "70100".equals(state)) {
          return true;
        }
      }
    }
    return false;
  }

  protected Map<String, List<ExistingIndex>> readFromCatalog(Connection connection, Collection<String> tables, String sql)
      throws SQLException {
    Map<String, Map<String, ExistingIndex>> found = new LinkedHashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      if (queryTimeoutSeconds > 0) {
        statement.setQueryTimeout(queryTimeoutSeconds);
      }
      int parameter = 1;
      for (String value : parameters(tables)) {
        statement.setString(parameter++, value);
      }
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          String table = unprefixed(rows.getString(1));
          String indexName = rows.getString(2);
          if (table == null || indexName == null) {
            continue;
          }
          ExistingIndex index = found.computeIfAbsent(table, t -> new LinkedHashMap<>())
              .computeIfAbsent(indexName.toUpperCase(Locale.ROOT), n -> new ExistingIndex(table, indexName));
          int nullsFirst = rows.getInt(8);
          index.addColumn(rows.getString(3), rows.wasNull() ? null : nullsFirst == 1);
          if (rows.getInt(5) == 0) {
            index.usable(false);
          }
          index.predicate(rows.getString(6));
          index.accessMethod(rows.getString(7));
        }
      }
    }
    return toLists(found);
  }

  /** The parameters of the catalog query, in order (see {@link #catalogQueries}). */
  protected List<String> parameters(Collection<String> tables) {
    List<String> parameters = new ArrayList<>();
    if (MYSQL.equals(databaseType)) {
      // compared without a function on the information_schema columns, so that the server reads
      // only the one database: the schema as configured, each table name as the engine writes it
      // and in lower case (lower_case_table_names); the column collations decide the rest
      if (schema != null) {
        parameters.add(schema);
      }
      for (String table : tables) {
        parameters.add(namePrefix + table.toUpperCase(Locale.ROOT));
        parameters.add((namePrefix + table).toLowerCase(Locale.ROOT));
      }
      return parameters;
    }
    if (schema != null) {
      parameters.add(schema.toUpperCase(Locale.ROOT));
    }
    for (String table : tables) {
      parameters.add(namePrefix + table.toUpperCase(Locale.ROOT));
    }
    return parameters;
  }

  protected String unprefixed(String tableName) {
    if (tableName == null) {
      return null;
    }
    String upper = tableName.toUpperCase(Locale.ROOT);
    return upper.startsWith(namePrefix) ? upper.substring(namePrefix.length()) : upper;
  }

  protected static Map<String, List<ExistingIndex>> toLists(Map<String, Map<String, ExistingIndex>> found) {
    Map<String, List<ExistingIndex>> result = new LinkedHashMap<>();
    found.forEach((table, indexes) -> result.put(table, new ArrayList<>(indexes.values())));
    return result;
  }

  /**
   * The statements for the database, tried in this order, each with the columns: table, index,
   * key column (null for an expression or a column prefix), position, usable (1/0), predicate of
   * a partial (filtered) index, access method (where the database has several that matter, else
   * null), whether NULL comes first when the index is read in ascending order of that key (1/0,
   * where it can differ, else null). Parameters: see {@link #parameters}. Ordered by table, index
   * and position. Empty when the database has none.
   */
  protected List<String> catalogQueries(int tableCount) {
    if (catalog != null && !MSSQL.equals(databaseType)) {
      return Collections.emptyList();
    }
    StringBuilder in = new StringBuilder("(");
    StringBuilder lowerIn = new StringBuilder("(");
    for (int i = 0; i < tableCount; i++) {
      in.append(i == 0 ? "?" : ", ?");
      lowerIn.append(i == 0 ? "lower(?)" : ", lower(?)");
    }
    in.append(")");
    lowerIn.append(")");
    switch (databaseType == null ? "" : databaseType) {
      case POSTGRES:
        // unquoted names, as the engine writes them, are stored in lower case; comparing exactly
        // keeps a schema "BPM" apart from bpm. indnkeyatts (PostgreSQL 11) separates the key
        // columns from the INCLUDE columns; an index left behind by a failed CREATE INDEX
        // CONCURRENTLY has indisvalid = false. indoption holds per key 1 (DESC) and 2 (NULLS
        // FIRST): NULL comes first in ascending order when exactly one of them is set
        return Collections.singletonList("select t.relname, i.relname, a.attname, k.ord, "
            + "case when x.indisvalid and x.indisready then 1 else 0 end, "
            + "pg_catalog.pg_get_expr(x.indpred, x.indrelid), "
            + "am.amname, "
            + "case when ((x.indoption[(k.ord - 1)::int] & 1) <> 0) <> ((x.indoption[(k.ord - 1)::int] & 2) <> 0) then 1 else 0 end "
            + "from pg_catalog.pg_index x "
            + "join pg_catalog.pg_class t on t.oid = x.indrelid "
            + "join pg_catalog.pg_class i on i.oid = x.indexrelid "
            + "join pg_catalog.pg_am am on am.oid = i.relam "
            + "join pg_catalog.pg_namespace n on n.oid = t.relnamespace "
            + "cross join lateral unnest(x.indkey) with ordinality as k (attnum, ord) "
            + "left join pg_catalog.pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum and k.attnum > 0 "
            + "where n.nspname = " + (schema != null ? "lower(?)" : "current_schema()")
            + " and t.relname in " + lowerIn + " and k.ord <= x.indnkeyatts "
            + "order by t.relname, i.relname, k.ord");
      case MYSQL:
        // a column prefix (SUB_PART) cannot serve the order of the columns after it; MariaDB
        // (IGNORED, 10.6) and MySQL (IS_VISIBLE, 8.0) can hide an index from the optimizer. No
        // function on TABLE_SCHEMA or TABLE_NAME: with one, the server opens the tables of every
        // database of the server to evaluate it
        StringBuilder names = new StringBuilder("(");
        for (int i = 0; i < tableCount; i++) {
          names.append(i == 0 ? "?, ?" : ", ?, ?");
        }
        names.append(")");
        String select = "select TABLE_NAME, INDEX_NAME, case when SUB_PART is null then COLUMN_NAME end, SEQ_IN_INDEX, ";
        String from = ", null, null, null from information_schema.STATISTICS "
            + "where TABLE_SCHEMA = " + (schema != null ? "?" : "database()")
            + " and TABLE_NAME in " + names + " "
            + "order by TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX";
        return Arrays.asList(
            select + "case when IGNORED = 'YES' then 0 else 1 end" + from,
            select + "case when IS_VISIBLE = 'NO' then 0 else 1 end" + from,
            select + "1" + from);
      case H2:
        return Collections.singletonList("select TABLE_NAME, INDEX_NAME, COLUMN_NAME, ORDINAL_POSITION, 1, null, null, null "
            + "from INFORMATION_SCHEMA.INDEX_COLUMNS "
            + "where upper(TABLE_SCHEMA) = " + (schema != null ? "?" : "upper(current_schema)")
            + " and upper(TABLE_NAME) in " + in + " "
            + "order by TABLE_NAME, INDEX_NAME, ORDINAL_POSITION");
      case MSSQL:
        // key_ordinal = 0 marks included columns. A filtered index is reported with a predicate,
        // which no recommendation accepts; filter_definition is null without permission to see
        // it, so has_filter decides. With a CATALOG.SCHEMA. table prefix the views of that
        // database are read (the schema then comes from the prefix)
        String db = catalog == null ? "" : "[" + catalog.replace("]", "]]") + "].";
        return Collections.singletonList("select t.name, i.name, c.name, ic.key_ordinal, "
            + "case when i.is_disabled = 0 and i.is_hypothetical = 0 then 1 else 0 end, "
            + "case when i.has_filter = 1 then coalesce(i.filter_definition, 'filtered') end, null, null "
            + "from " + db + "sys.indexes i "
            + "join " + db + "sys.tables t on t.object_id = i.object_id "
            + "join " + db + "sys.schemas s on s.schema_id = t.schema_id "
            + "join " + db + "sys.index_columns ic on ic.object_id = i.object_id and ic.index_id = i.index_id "
            + "join " + db + "sys.columns c on c.object_id = ic.object_id and c.column_id = ic.column_id "
            + "where upper(s.name) = " + (schema != null ? "?" : "upper(schema_name())")
            + " and upper(t.name) in " + in + " and ic.key_ordinal > 0 and i.type in (1, 2) "
            + "order by t.name, i.name, ic.key_ordinal");
      case ORACLE:
        // a function-based or descending key part has a generated column name (SYS_NC...$),
        // which matches no recommended column but counts as a key column
        return Collections.singletonList("select c.TABLE_NAME, c.INDEX_NAME, c.COLUMN_NAME, c.COLUMN_POSITION, "
            + "case when i.STATUS = 'UNUSABLE' or i.VISIBILITY = 'INVISIBLE' then 0 else 1 end, null, null, null "
            + "from ALL_IND_COLUMNS c "
            + "join ALL_INDEXES i on i.OWNER = c.INDEX_OWNER and i.INDEX_NAME = c.INDEX_NAME "
            + "where c.TABLE_OWNER = " + (schema != null ? "?" : "sys_context('USERENV', 'CURRENT_SCHEMA')")
            + " and c.TABLE_NAME in " + in + " "
            + "order by c.TABLE_NAME, c.INDEX_NAME, c.COLUMN_POSITION");
      case DB2:
        // COLORDER = 'I' marks included columns
        return Collections.singletonList("select i.TABNAME, i.INDNAME, c.COLNAME, c.COLSEQ, 1, "
            + "cast(null as varchar(1)), cast(null as varchar(1)), cast(null as integer) "
            + "from SYSCAT.INDEXES i "
            + "join SYSCAT.INDEXCOLUSE c on c.INDSCHEMA = i.INDSCHEMA and c.INDNAME = i.INDNAME "
            + "where i.TABSCHEMA = " + (schema != null ? "?" : "current schema")
            + " and i.TABNAME in " + in + " and c.COLORDER <> 'I' "
            + "order by i.TABNAME, i.INDNAME, c.COLSEQ");
      default:
        return Collections.emptyList();
    }
  }
}
