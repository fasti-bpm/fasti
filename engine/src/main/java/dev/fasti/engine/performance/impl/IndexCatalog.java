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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the indexes of some engine tables, per database from its catalog and otherwise through
 * JDBC metadata. Reads only. A table of which no index can be seen is left out of the result
 * (every engine table has a primary key), so that the caller can tell "no such index" from
 * "cannot tell".
 */
public class IndexCatalog {

  public static final String H2 = "h2";
  public static final String MYSQL = "mysql";
  public static final String POSTGRES = "postgres";
  public static final String ORACLE = "oracle";
  public static final String MSSQL = "mssql";
  public static final String DB2 = "db2";

  protected final String databaseType;
  protected final String schema;
  protected final String namePrefix;
  /** The catalog (database) of a {@code CATALOG.SCHEMA.} table prefix, as SQL Server allows, or {@code null}. */
  protected final String catalog;

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

  /**
   * The indexes by table (name in upper case without the table prefix), from the catalog query of
   * the database.
   *
   * @throws SQLException if the catalog cannot be read; on PostgreSQL the transaction of the
   *         connection is then aborted, so {@link #readFromMetaData} needs another one
   */
  public Map<String, List<ExistingIndex>> readFromCatalog(Connection connection, Collection<String> tables) throws SQLException {
    List<String> queries = catalogQueries(tables.size());
    if (queries.isEmpty()) {
      throw new SQLException("no catalog query for database type " + databaseType
          + (catalog != null ? " with the catalog " + catalog + " in the table prefix" : ""));
    }
    SQLException failure = null;
    // MySQL and MariaDB name the column that hides an index differently: the first query that
    // the server accepts is used (a refused query does not abort the transaction there)
    for (String sql : queries) {
      try {
        return readFromCatalog(connection, tables, sql);
      } catch (SQLException e) {
        if (failure != null) {
          e.addSuppressed(failure);
        }
        failure = e;
      }
    }
    throw failure;
  }

  protected Map<String, List<ExistingIndex>> readFromCatalog(Connection connection, Collection<String> tables, String sql)
      throws SQLException {
    Map<String, Map<String, ExistingIndex>> found = new LinkedHashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      int parameter = 1;
      if (schema != null) {
        statement.setString(parameter++, schema.toUpperCase(Locale.ROOT));
      }
      for (String table : tables) {
        statement.setString(parameter++, namePrefix + table.toUpperCase(Locale.ROOT));
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
          index.addColumn(rows.getString(3));
          if (rows.getInt(5) == 0) {
            index.usable(false);
          }
          index.predicate(rows.getString(6));
        }
      }
    }
    return toLists(found);
  }

  /**
   * The same through {@link DatabaseMetaData#getIndexInfo}, for a database without catalog query
   * or one that refused it. Less exact: included columns can look like key columns, and whether an
   * index is usable is not known.
   */
  public Map<String, List<ExistingIndex>> readFromMetaData(Connection connection, Collection<String> tables) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    String effectiveCatalog = catalog != null ? catalog : connection.getCatalog();
    String effectiveSchema = schema;
    if (effectiveSchema == null) {
      try {
        effectiveSchema = connection.getSchema();
      } catch (SQLException | AbstractMethodError e) {
        effectiveSchema = null;
      }
    }
    Map<String, Map<String, ExistingIndex>> found = new LinkedHashMap<>();
    for (String table : tables) {
      String key = table.toUpperCase(Locale.ROOT);
      String tableName = namePrefix + key;
      String schemaName = effectiveSchema;
      if (metaData.storesLowerCaseIdentifiers()) {
        tableName = tableName.toLowerCase(Locale.ROOT);
        schemaName = schemaName == null ? null : schemaName.toLowerCase(Locale.ROOT);
      } else if (metaData.storesUpperCaseIdentifiers()) {
        schemaName = schemaName == null ? null : schemaName.toUpperCase(Locale.ROOT);
      }
      // approximate = true: with false the Oracle driver analyzes the table
      try (ResultSet rows = metaData.getIndexInfo(effectiveCatalog, schemaName, tableName, false, true)) {
        while (rows.next()) {
          String indexName = rows.getString("INDEX_NAME");
          if (rows.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic || indexName == null) {
            continue;
          }
          ExistingIndex index = found.computeIfAbsent(key, t -> new LinkedHashMap<>())
              .computeIfAbsent(indexName.toUpperCase(Locale.ROOT), n -> new ExistingIndex(key, indexName));
          // the rows of an index come ordered by ORDINAL_POSITION
          index.addColumn(rows.getString("COLUMN_NAME"));
          index.predicate(rows.getString("FILTER_CONDITION"));
        }
      }
    }
    return toLists(found);
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
   * a partial (filtered) index. Parameters: the schema in upper case (only if one is configured),
   * then the table names in upper case. Ordered by table, index and position. Empty when the
   * database has none, or when the table prefix names another catalog (SQL Server), which the
   * catalog views of the connection do not show.
   */
  protected List<String> catalogQueries(int tableCount) {
    if (catalog != null) {
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
        // CONCURRENTLY has indisvalid = false
        return Collections.singletonList("select t.relname, i.relname, a.attname, k.ord, "
            + "case when x.indisvalid and x.indisready then 1 else 0 end, "
            + "pg_catalog.pg_get_expr(x.indpred, x.indrelid) "
            + "from pg_catalog.pg_index x "
            + "join pg_catalog.pg_class t on t.oid = x.indrelid "
            + "join pg_catalog.pg_class i on i.oid = x.indexrelid "
            + "join pg_catalog.pg_namespace n on n.oid = t.relnamespace "
            + "cross join lateral unnest(x.indkey) with ordinality as k (attnum, ord) "
            + "left join pg_catalog.pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum and k.attnum > 0 "
            + "where n.nspname = " + (schema != null ? "lower(?)" : "current_schema()")
            + " and t.relname in " + lowerIn + " and k.ord <= x.indnkeyatts "
            + "order by t.relname, i.relname, k.ord");
      case MYSQL:
        // a column prefix (SUB_PART) cannot serve the order of the columns after it; MariaDB
        // (IGNORED, 10.6) and MySQL (IS_VISIBLE, 8.0) can hide an index from the optimizer
        String select = "select TABLE_NAME, INDEX_NAME, case when SUB_PART is null then COLUMN_NAME end, SEQ_IN_INDEX, ";
        String from = " from information_schema.STATISTICS "
            + "where upper(TABLE_SCHEMA) = " + (schema != null ? "?" : "upper(database())")
            + " and upper(TABLE_NAME) in " + in + " "
            + "order by TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX";
        return Arrays.asList(
            select + "case when IGNORED = 'YES' then 0 else 1 end, null" + from,
            select + "case when IS_VISIBLE = 'NO' then 0 else 1 end, null" + from,
            select + "1, null" + from);
      case H2:
        return Collections.singletonList("select TABLE_NAME, INDEX_NAME, COLUMN_NAME, ORDINAL_POSITION, 1, null "
            + "from INFORMATION_SCHEMA.INDEX_COLUMNS "
            + "where upper(TABLE_SCHEMA) = " + (schema != null ? "?" : "upper(current_schema)")
            + " and upper(TABLE_NAME) in " + in + " "
            + "order by TABLE_NAME, INDEX_NAME, ORDINAL_POSITION");
      case MSSQL:
        // key_ordinal = 0 marks included columns. A filtered index is reported with a predicate,
        // which no recommendation accepts; filter_definition is null without permission to see
        // it, so has_filter decides
        return Collections.singletonList("select t.name, i.name, c.name, ic.key_ordinal, "
            + "case when i.is_disabled = 0 and i.is_hypothetical = 0 then 1 else 0 end, "
            + "case when i.has_filter = 1 then coalesce(i.filter_definition, 'filtered') end "
            + "from sys.indexes i "
            + "join sys.tables t on t.object_id = i.object_id "
            + "join sys.schemas s on s.schema_id = t.schema_id "
            + "join sys.index_columns ic on ic.object_id = i.object_id and ic.index_id = i.index_id "
            + "join sys.columns c on c.object_id = ic.object_id and c.column_id = ic.column_id "
            + "where upper(s.name) = " + (schema != null ? "?" : "upper(schema_name())")
            + " and upper(t.name) in " + in + " and ic.key_ordinal > 0 and i.type in (1, 2) "
            + "order by t.name, i.name, ic.key_ordinal");
      case ORACLE:
        // a function-based key part has a generated column name (SYS_NC...$), which matches no
        // recommended column but counts as a key column
        return Collections.singletonList("select c.TABLE_NAME, c.INDEX_NAME, c.COLUMN_NAME, c.COLUMN_POSITION, "
            + "case when i.STATUS = 'UNUSABLE' or i.VISIBILITY = 'INVISIBLE' then 0 else 1 end, null "
            + "from ALL_IND_COLUMNS c "
            + "join ALL_INDEXES i on i.OWNER = c.INDEX_OWNER and i.INDEX_NAME = c.INDEX_NAME "
            + "where c.TABLE_OWNER = " + (schema != null ? "?" : "sys_context('USERENV', 'CURRENT_SCHEMA')")
            + " and c.TABLE_NAME in " + in + " "
            + "order by c.TABLE_NAME, c.INDEX_NAME, c.COLUMN_POSITION");
      case DB2:
        // COLORDER = 'I' marks included columns
        return Collections.singletonList("select i.TABNAME, i.INDNAME, c.COLNAME, c.COLSEQ, 1, cast(null as varchar(1)) "
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
