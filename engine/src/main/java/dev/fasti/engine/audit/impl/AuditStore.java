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

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

import dev.fasti.engine.audit.AuditService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the tables of the audit trail with plain JDBC on the connection of the
 * command, so that records are written in the transaction of what they record: if that rolls
 * back, so do they, and if they cannot be written, the command fails.
 *
 * <p>Appending to a chain first locks the row of the chain in {@code FASTI_AUDIT_HEAD} with an
 * update (which every database supports the same way), reads the last position and hash, inserts
 * the records and moves the head. The lock is held until the transaction ends, so appends to one
 * chain are serialized while other chains (other slots, other nodes) are not affected.</p>
 *
 * <p>Queries page in the database and exports read one forward-only cursor, so that neither holds
 * more than a page of records in memory. Messages of the database never reach the caller: they are
 * logged, and the exception names only what could not be done.</p>
 */
public final class AuditStore {

  private static final Logger LOG = LoggerFactory.getLogger(AuditStore.class);

  public static final String EVENT_TABLE = "FASTI_AUDIT_EVENT";
  public static final String HEAD_TABLE = "FASTI_AUDIT_HEAD";

  static final String COLUMNS = "ID_, CHAIN_ID_, SEQ_, TIME_, TYPE_, ACTION_, ACTOR_, ENGINE_, TENANT_ID_, PROC_DEF_KEY_, "
      + "PROC_DEF_ID_, PROC_INST_ID_, BUSINESS_KEY_, ENTITY_TYPE_, ENTITY_ID_, ENTITY_NAME_, DETAILS_, PREV_HASH_, HASH_";

  private AuditStore() {
  }

  /** The position and hash of the last record of a chain. */
  public static final class Head {
    public final String chainId;
    public final long sequence;
    public final String hash;

    Head(String chainId, long sequence, String hash) {
      this.chainId = chainId;
      this.sequence = sequence;
      this.hash = hash;
    }
  }

  static Connection connection(CommandContext commandContext) {
    return commandContext.getDbSqlSession().getSqlSession().getConnection();
  }

  static String events(AuditSettings settings) {
    return settings.getTablePrefix() + EVENT_TABLE;
  }

  static String heads(AuditSettings settings) {
    return settings.getTablePrefix() + HEAD_TABLE;
  }

  /** Rows a cursor fetches at a time. */
  static final int FETCH_SIZE = 500;

  /**
   * A failure of the database: logged with what the driver says, reported without it, as driver
   * messages name tables, columns and constraints and the message of the exception reaches clients.
   */
  static ProcessEngineException failure(String what, SQLException e) {
    return failure(what, null, e);
  }

  static ProcessEngineException failure(String what, String detail, SQLException e) {
    LOG.error("The audit trail could not {}{}: {} (SQL state {})", what, detail == null ? "" : " (" + detail + ")",
        e.getMessage(), e.getSQLState(), e);
    return new ProcessEngineException("The audit trail could not " + what + ". The cause is in the log of the server.", e);
  }

  static void timeout(PreparedStatement statement, int seconds) throws SQLException {
    if (seconds > 0) {
      statement.setQueryTimeout(seconds);
    }
  }

  // writing //////////////////////////////////////////////////////////////////////////////////

  /**
   * Appends the records to the chain, setting their id, position and hashes, and adds a signed
   * checkpoint after every {@code checkpointInterval} records. Returns the checkpoints written.
   */
  public static List<AuditRecord> append(CommandContext commandContext, AuditSettings settings, String chainId,
      List<AuditRecord> records) {
    List<AuditRecord> checkpoints = new ArrayList<>();
    if (records.isEmpty()) {
      return checkpoints;
    }
    Connection connection = connection(commandContext);
    try {
      Head head = lockHead(connection, settings, chainId);
      long sequence = head.sequence;
      String previous = head.hash;
      List<AuditRecord> written = new ArrayList<>();
      for (AuditRecord record : records) {
        sequence++;
        if (record.getPurgedThroughHash() != null) {
          attestPurge(settings, record, chainId, sequence);
        }
        seal(record, chainId, sequence, previous);
        previous = record.getHash();
        written.add(record);
        int interval = settings.getCheckpointInterval();
        if (interval > 0 && sequence % interval == 0) {
          sequence++;
          AuditRecord checkpoint = checkpoint(settings, chainId, sequence, previous, record.getTime());
          previous = checkpoint.getHash();
          written.add(checkpoint);
          checkpoints.add(checkpoint);
        }
      }
      insert(connection, settings, written);
      try (PreparedStatement update = connection.prepareStatement(
          "update " + heads(settings) + " set SEQ_ = ?, HASH_ = ?, TIME_ = ? where CHAIN_ID_ = ?")) {
        update.setLong(1, sequence);
        update.setString(2, previous);
        update.setLong(3, written.get(written.size() - 1).getTime());
        update.setString(4, chainId);
        update.executeUpdate();
      }
    } catch (SQLException e) {
      throw failure("write its records", "chain " + chainId, e);
    }
    return checkpoints;
  }

  /**
   * Completes the details of a purge record once its position is known: what was removed and, with
   * a key, the signature over it, the chain and the id and position of the record.
   */
  static void attestPurge(AuditSettings settings, AuditRecord purge, String chainId, long sequence) {
    purge.setId(UUID.randomUUID().toString());
    AuditJson details = purge.getPurgeDetails()
        .put("purgedThroughSeq", purge.getPurgedThroughSequence())
        .put("purgedThroughHash", purge.getPurgedThroughHash());
    if (settings.isSigned()) {
      details.put("keyId", settings.getKeyId())
          .put("hmac", AuditChain.hmacHex(settings.getHmacKey(), AuditChain.purgeText(chainId,
              purge.getPurgedThroughSequence(), purge.getPurgedThroughHash(), purge.getId(), sequence)));
    }
    purge.setDetails(details.toJson());
  }

  static void seal(AuditRecord record, String chainId, long sequence, String previous) {
    if (record.getId() == null) {
      record.setId(UUID.randomUUID().toString());
    }
    record.setChainId(chainId);
    record.setSequence(sequence);
    record.setPreviousHash(previous);
    record.setHash(AuditChain.hash(record));
  }

  static AuditRecord checkpoint(AuditSettings settings, String chainId, long sequence, String previous, long time) {
    AuditRecord checkpoint = new AuditRecord(AuditService.TYPE_AUDIT, AuditService.ACTION_CHECKPOINT);
    checkpoint.setTime(time);
    checkpoint.setEngineName(settings.getEngineName());
    checkpoint.setEntityType("chain");
    checkpoint.setEntityId(null);
    checkpoint.setEntityName(chainId);
    AuditJson details = AuditJson.object()
        .put("signedSequence", sequence - 1)
        .put("signedHash", previous)
        .put("interval", settings.getCheckpointInterval());
    if (settings.isSigned()) {
      details.put("keyId", settings.getKeyId())
          .put("hmac", AuditChain.hmacHex(settings.getHmacKey(),
              AuditChain.checkpointText(chainId, sequence - 1, previous, settings.getCheckpointInterval())));
    }
    checkpoint.setDetails(details.toJson());
    seal(checkpoint, chainId, sequence, previous);
    return checkpoint;
  }

  /** Locks the head row of the chain until the end of the transaction and reads it; creates it if missing. */
  static Head lockHead(Connection connection, AuditSettings settings, String chainId) throws SQLException {
    try (PreparedStatement lock = connection.prepareStatement(
        "update " + heads(settings) + " set SEQ_ = SEQ_ where CHAIN_ID_ = ?")) {
      lock.setString(1, chainId);
      lock.executeUpdate();
    }
    Head head = readHead(connection, settings, chainId);
    if (head == null) {
      // a chain this node did not prepare at startup, or a head removed from outside: continue after
      // the last record there is (verifying reported the missing head until now). A concurrent insert
      // of the same head fails one of the two transactions, like any other conflict.
      head = lastRecord(connection, settings, chainId);
      if (head.sequence > 0) {
        LOG.warn("The head of audit trail chain {} was missing; it is recreated from record {}", chainId, head.sequence);
      }
      try (PreparedStatement insert = connection.prepareStatement(
          "insert into " + heads(settings) + " (CHAIN_ID_, SEQ_, HASH_, TIME_) values (?, ?, ?, 0)")) {
        insert.setString(1, chainId);
        insert.setLong(2, head.sequence);
        insert.setString(3, head.hash);
        insert.executeUpdate();
      }
    }
    return head;
  }

  static Head lastRecord(Connection connection, AuditSettings settings, String chainId) throws SQLException {
    try (PreparedStatement select = connection.prepareStatement("select SEQ_, HASH_ from " + events(settings)
        + " where CHAIN_ID_ = ? and SEQ_ = (select max(SEQ_) from " + events(settings) + " where CHAIN_ID_ = ?)")) {
      select.setString(1, chainId);
      select.setString(2, chainId);
      try (ResultSet result = select.executeQuery()) {
        if (result.next()) {
          return new Head(chainId, result.getLong(1), result.getString(2));
        }
      }
    }
    return new Head(chainId, 0, AuditChain.GENESIS);
  }

  static Head readHead(Connection connection, AuditSettings settings, String chainId) throws SQLException {
    try (PreparedStatement select = connection.prepareStatement(
        "select SEQ_, HASH_ from " + heads(settings) + " where CHAIN_ID_ = ?")) {
      select.setString(1, chainId);
      try (ResultSet result = select.executeQuery()) {
        if (result.next()) {
          return new Head(chainId, result.getLong(1), result.getString(2));
        }
        return null;
      }
    }
  }

  static void insertHead(Connection connection, AuditSettings settings, String chainId) throws SQLException {
    try (PreparedStatement insert = connection.prepareStatement(
        "insert into " + heads(settings) + " (CHAIN_ID_, SEQ_, HASH_, TIME_) values (?, 0, ?, 0)")) {
      insert.setString(1, chainId);
      insert.setString(2, AuditChain.GENESIS);
      insert.executeUpdate();
    }
  }

  /** Creates the head of a chain unless it exists; whether it was created. */
  public static boolean ensureHead(CommandContext commandContext, AuditSettings settings, String chainId) {
    Connection connection = connection(commandContext);
    try {
      if (readHead(connection, settings, chainId) != null) {
        return false;
      }
      insertHead(connection, settings, chainId);
      return true;
    } catch (SQLException e) {
      throw failure("create the head of a chain", "chain " + chainId, e);
    }
  }

  static void insert(Connection connection, AuditSettings settings, List<AuditRecord> records) throws SQLException {
    boolean national = settings.isNationalCharacterColumns();
    try (PreparedStatement insert = connection.prepareStatement(
        "insert into " + events(settings) + " (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      for (AuditRecord record : records) {
        int i = 1;
        insert.setString(i++, record.getId());
        insert.setString(i++, record.getChainId());
        insert.setLong(i++, record.getSequence());
        insert.setLong(i++, record.getTime());
        insert.setString(i++, record.getType());
        text(insert, i++, record.getAction(), national);
        text(insert, i++, record.getActor(), national);
        text(insert, i++, record.getEngineName(), national);
        text(insert, i++, record.getTenantId(), national);
        text(insert, i++, record.getProcessDefinitionKey(), national);
        text(insert, i++, record.getProcessDefinitionId(), national);
        text(insert, i++, record.getProcessInstanceId(), national);
        text(insert, i++, record.getBusinessKey(), national);
        text(insert, i++, record.getEntityType(), national);
        text(insert, i++, record.getEntityId(), national);
        text(insert, i++, record.getEntityName(), national);
        if (record.getDetails() == null) {
          insert.setNull(i++, national ? Types.NCLOB : Types.CLOB);
        } else if (national) {
          insert.setNCharacterStream(i++, new StringReader(record.getDetails()), record.getDetails().length());
        } else {
          insert.setString(i++, record.getDetails());
        }
        insert.setString(i++, record.getPreviousHash());
        insert.setString(i, record.getHash());
        if (records.size() == 1) {
          insert.executeUpdate();
        } else {
          insert.addBatch();
        }
      }
      if (records.size() > 1) {
        insert.executeBatch();
      }
    }
  }

  /**
   * Binds text; for the NVARCHAR2 columns of Oracle as national characters, so that text the
   * database character set cannot hold is not changed after it was hashed.
   */
  static void text(PreparedStatement statement, int index, String value, boolean national) throws SQLException {
    if (value == null) {
      statement.setNull(index, national ? Types.NVARCHAR : Types.VARCHAR);
    } else if (national) {
      statement.setNString(index, value);
    } else {
      statement.setString(index, value);
    }
  }

  // reading //////////////////////////////////////////////////////////////////////////////////

  static AuditRecord read(ResultSet result) throws SQLException {
    AuditRecord record = new AuditRecord();
    record.id = result.getString("ID_");
    record.chainId = result.getString("CHAIN_ID_");
    record.sequence = result.getLong("SEQ_");
    record.time = result.getLong("TIME_");
    record.type = result.getString("TYPE_");
    record.action = result.getString("ACTION_");
    record.actor = result.getString("ACTOR_");
    record.engineName = result.getString("ENGINE_");
    record.tenantId = result.getString("TENANT_ID_");
    record.processDefinitionKey = result.getString("PROC_DEF_KEY_");
    record.processDefinitionId = result.getString("PROC_DEF_ID_");
    record.processInstanceId = result.getString("PROC_INST_ID_");
    record.businessKey = result.getString("BUSINESS_KEY_");
    record.entityType = result.getString("ENTITY_TYPE_");
    record.entityId = result.getString("ENTITY_ID_");
    record.entityName = result.getString("ENTITY_NAME_");
    record.details = result.getString("DETAILS_");
    record.previousHash = result.getString("PREV_HASH_");
    record.hash = result.getString("HASH_");
    return record;
  }

  /**
   * The statement that selects a page of the records of a query. The page is cut in the database
   * ({@code limit .. offset ..} on H2, PostgreSQL, MySQL and MariaDB; {@code offset .. fetch next ..}
   * on Oracle, SQL Server and DB2), so that the rows before and after it never reach the engine.
   * Binds the filters, then the page.
   */
  static String selectSql(AuditSettings settings, AuditEventQueryImpl query, List<String> tenants, long firstResult,
      long maxResults, List<Object> parameters) {
    String where = where(query, tenants, parameters);
    String order = query.isOldestFirst() ? "TIME_ asc, CHAIN_ID_ asc, SEQ_ asc" : "TIME_ desc, CHAIN_ID_ asc, SEQ_ desc";
    String sql = "select " + COLUMNS + " from " + events(settings) + where + " order by " + order;
    if (firstResult <= 0 && maxResults >= Integer.MAX_VALUE) {
      return sql;
    }
    if (isOffsetFetch(settings.getDatabaseType())) {
      parameters.add(firstResult);
      parameters.add(maxResults);
      return sql + " offset ? rows fetch next ? rows only";
    }
    parameters.add(maxResults);
    parameters.add(firstResult);
    return sql + " limit ? offset ?";
  }

  static boolean isOffsetFetch(String databaseType) {
    if (databaseType == null) {
      return false;
    }
    String type = databaseType.toLowerCase(Locale.ROOT);
    return type.equals("oracle") || type.equals("mssql") || type.equals("db2");
  }

  /** A page of the records the query selects, limited to the tenants (all with {@code null}). */
  public static List<AuditRecord> select(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants, int firstResult, int maxResults) {
    List<AuditRecord> records = new ArrayList<>();
    if (maxResults <= 0) {
      return records;
    }
    List<Object> parameters = new ArrayList<>();
    String sql = selectSql(settings, query, tenants, firstResult, maxResults, parameters);
    try (PreparedStatement select = connection(commandContext).prepareStatement(sql)) {
      bind(select, parameters);
      timeout(select, settings.getQueryTimeoutSeconds());
      select.setFetchSize(Math.min(maxResults, FETCH_SIZE));
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          records.add(read(result));
        }
      }
    } catch (SQLException e) {
      throw failure("read records", e);
    }
    return records;
  }

  /**
   * Hands the first {@code maxResults} records the query selects to the consumer, one by one, from
   * one forward-only cursor that fetches {@value #FETCH_SIZE} rows at a time; how many.
   */
  public static long forEach(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants, int maxResults, Consumer<AuditRecord> consumer) {
    if (maxResults <= 0) {
      return 0;
    }
    List<Object> parameters = new ArrayList<>();
    String sql = selectSql(settings, query, tenants, 0, maxResults, parameters);
    Connection connection = connection(commandContext);
    long count = 0;
    try (PreparedStatement select = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
      bind(select, parameters);
      timeout(select, settings.getQueryTimeoutSeconds());
      select.setFetchSize(streamingFetchSize(connection));
      try (ResultSet result = select.executeQuery()) {
        while (count < maxResults && result.next()) {
          consumer.accept(read(result));
          count++;
        }
      }
    } catch (SQLException e) {
      throw failure("read records", e);
    }
    return count;
  }

  /**
   * The fetch size that makes the driver read a result as a cursor. MySQL Connector/J buffers the
   * whole result unless asked to stream row by row with {@code Integer.MIN_VALUE}; the other drivers
   * (MariaDB Connector/J included) fetch the given number of rows at a time.
   */
  static int streamingFetchSize(Connection connection) {
    try {
      String driver = connection.getMetaData().getDriverName();
      if (driver != null && driver.toLowerCase(Locale.ROOT).contains("mysql connector")) {
        return Integer.MIN_VALUE;
      }
    } catch (SQLException | RuntimeException e) {
      // the default
    }
    return FETCH_SIZE;
  }

  public static long count(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants) {
    List<Object> parameters = new ArrayList<>();
    String sql = "select count(*) from " + events(settings) + where(query, tenants, parameters);
    try (PreparedStatement select = connection(commandContext).prepareStatement(sql)) {
      bind(select, parameters);
      timeout(select, settings.getQueryTimeoutSeconds());
      try (ResultSet result = select.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    } catch (SQLException e) {
      throw failure("count records", e);
    }
  }

  /**
   * The conditions of a query. With {@code tenants}, only the records of those tenants and the
   * records without tenant, as the tenant check of the engine does for its own queries.
   */
  static String where(AuditEventQueryImpl query, List<String> tenants, List<Object> parameters) {
    List<String> conditions = new ArrayList<>();
    if (tenants != null) {
      if (tenants.isEmpty()) {
        conditions.add("TENANT_ID_ is null");
      } else {
        StringBuilder in = new StringBuilder("(TENANT_ID_ is null or TENANT_ID_ in (");
        for (int i = 0; i < tenants.size(); i++) {
          in.append(i == 0 ? "?" : ", ?");
          parameters.add(tenants.get(i));
        }
        conditions.add(in.append("))").toString());
      }
    }
    condition(conditions, parameters, "PROC_INST_ID_", query.processInstanceId);
    condition(conditions, parameters, "BUSINESS_KEY_", query.businessKey);
    condition(conditions, parameters, "ACTOR_", query.actor);
    if (query.systemActor) {
      conditions.add("ACTOR_ is null");
    }
    if (query.types != null && !query.types.isEmpty()) {
      StringBuilder in = new StringBuilder("TYPE_ in (");
      for (int i = 0; i < query.types.size(); i++) {
        in.append(i == 0 ? "?" : ", ?");
        parameters.add(query.types.get(i));
      }
      conditions.add(in.append(')').toString());
    }
    condition(conditions, parameters, "ACTION_", query.action);
    condition(conditions, parameters, "TENANT_ID_", query.tenantId);
    condition(conditions, parameters, "PROC_DEF_KEY_", query.processDefinitionKey);
    condition(conditions, parameters, "ENTITY_ID_", query.entityId);
    condition(conditions, parameters, "CHAIN_ID_", query.chainId);
    if (query.from != null) {
      conditions.add("TIME_ >= ?");
      parameters.add(query.from.getTime());
    }
    if (query.to != null) {
      conditions.add("TIME_ < ?");
      parameters.add(query.to.getTime());
    }
    return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
  }

  static void condition(List<String> conditions, List<Object> parameters, String column, String value) {
    if (value != null) {
      conditions.add(column + " = ?");
      parameters.add(value);
    }
  }

  static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
    for (int i = 0; i < parameters.size(); i++) {
      Object value = parameters.get(i);
      if (value instanceof Long) {
        statement.setLong(i + 1, (Long) value);
      } else if (value instanceof Integer) {
        statement.setInt(i + 1, (Integer) value);
      } else {
        statement.setString(i + 1, (String) value);
      }
    }
  }

  /**
   * The assignee of a task as last committed to the history ({@code ACT_HI_TASKINST}), or
   * {@code null}: what an update of the task changes it from.
   */
  public static String historicAssignee(CommandContext commandContext, AuditSettings settings, String taskId) {
    if (taskId == null) {
      return null;
    }
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select ASSIGNEE_ from " + settings.getTablePrefix() + "ACT_HI_TASKINST where ID_ = ?")) {
      select.setString(1, taskId);
      try (ResultSet result = select.executeQuery()) {
        return result.next() ? result.getString(1) : null;
      }
    } catch (SQLException e) {
      throw failure("read the assignee of a task", "task " + taskId, e);
    }
  }

  /** Every chain with a head or with records, sorted. */
  public static List<String> chainIds(CommandContext commandContext, AuditSettings settings) {
    TreeSet<String> ids = new TreeSet<>();
    Connection connection = connection(commandContext);
    try {
      try (PreparedStatement select = connection.prepareStatement("select CHAIN_ID_ from " + heads(settings));
          ResultSet result = select.executeQuery()) {
        while (result.next()) {
          ids.add(result.getString(1));
        }
      }
      try (PreparedStatement select = connection.prepareStatement("select distinct CHAIN_ID_ from " + events(settings));
          ResultSet result = select.executeQuery()) {
        while (result.next()) {
          ids.add(result.getString(1));
        }
      }
    } catch (SQLException e) {
      throw failure("list chains", e);
    }
    return new ArrayList<>(ids);
  }

  public static Head head(CommandContext commandContext, AuditSettings settings, String chainId) {
    try {
      return readHead(connection(commandContext), settings, chainId);
    } catch (SQLException e) {
      throw failure("read the head of a chain", "chain " + chainId, e);
    }
  }

  /** Up to {@code limit} records of the chain after the position, in order. */
  public static List<AuditRecord> readChain(CommandContext commandContext, AuditSettings settings, String chainId,
      long afterSequence, int limit) {
    List<AuditRecord> records = new ArrayList<>();
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select " + COLUMNS + " from " + events(settings) + " where CHAIN_ID_ = ? and SEQ_ > ? order by SEQ_ asc")) {
      select.setString(1, chainId);
      select.setLong(2, afterSequence);
      select.setMaxRows(limit);
      select.setFetchSize(Math.min(limit, 500));
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          records.add(read(result));
        }
      }
    } catch (SQLException e) {
      throw failure("read a chain", "chain " + chainId, e);
    }
    return records;
  }

  /** The purge records of a chain, newest first. */
  public static List<AuditRecord> purges(CommandContext commandContext, AuditSettings settings, String chainId) {
    List<AuditRecord> records = new ArrayList<>();
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select " + COLUMNS + " from " + events(settings) + " where CHAIN_ID_ = ? and TYPE_ = ? and ACTION_ = ? order by SEQ_ desc")) {
      select.setString(1, chainId);
      select.setString(2, AuditService.TYPE_AUDIT);
      select.setString(3, AuditService.ACTION_PURGE);
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          records.add(read(result));
        }
      }
    } catch (SQLException e) {
      throw failure("read the purges of a chain", "chain " + chainId, e);
    }
    return records;
  }

  /**
   * The position of the last record of the chain that a purge before the time removes: the records
   * from the start of the chain up to the first one at or after the time. {@code 0} if none.
   */
  public static long purgeBoundary(CommandContext commandContext, AuditSettings settings, String chainId, long before) {
    Connection connection = connection(commandContext);
    try {
      Long firstKept = null;
      try (PreparedStatement select = connection.prepareStatement(
          "select min(SEQ_) from " + events(settings) + " where CHAIN_ID_ = ? and TIME_ >= ?")) {
        select.setString(1, chainId);
        select.setLong(2, before);
        try (ResultSet result = select.executeQuery()) {
          if (result.next()) {
            long value = result.getLong(1);
            firstKept = result.wasNull() ? null : value;
          }
        }
      }
      if (firstKept != null) {
        return firstKept - 1;
      }
      try (PreparedStatement select = connection.prepareStatement(
          "select max(SEQ_) from " + events(settings) + " where CHAIN_ID_ = ?")) {
        select.setString(1, chainId);
        try (ResultSet result = select.executeQuery()) {
          return result.next() ? result.getLong(1) : 0;
        }
      }
    } catch (SQLException e) {
      throw failure("find what to purge", "chain " + chainId, e);
    }
  }

  /** The record at a position, or {@code null}. */
  public static AuditRecord find(CommandContext commandContext, AuditSettings settings, String chainId, long sequence) {
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select " + COLUMNS + " from " + events(settings) + " where CHAIN_ID_ = ? and SEQ_ = ?")) {
      select.setString(1, chainId);
      select.setLong(2, sequence);
      try (ResultSet result = select.executeQuery()) {
        return result.next() ? read(result) : null;
      }
    } catch (SQLException e) {
      throw failure("read a chain", "chain " + chainId, e);
    }
  }

  /** Removes the records of the chain up to the position; how many. */
  public static long deleteThrough(CommandContext commandContext, AuditSettings settings, String chainId, long sequence) {
    try (PreparedStatement delete = connection(commandContext).prepareStatement(
        "delete from " + events(settings) + " where CHAIN_ID_ = ? and SEQ_ <= ?")) {
      delete.setString(1, chainId);
      delete.setLong(2, sequence);
      return delete.executeUpdate();
    } catch (SQLException e) {
      throw failure("purge a chain", "chain " + chainId, e);
    }
  }

  // schema ///////////////////////////////////////////////////////////////////////////////////

  public static boolean isTablePresent(CommandContext commandContext) {
    return commandContext.getDbSqlSession().isTablePresent(EVENT_TABLE)
        && commandContext.getDbSqlSession().isTablePresent(HEAD_TABLE);
  }

  public static String createResource(String databaseType) {
    return "dev/fasti/engine/audit/db/create/fasti." + databaseType + ".create.audit.sql";
  }

  public static String dropResource(String databaseType) {
    return "dev/fasti/engine/audit/db/drop/fasti." + databaseType + ".drop.audit.sql";
  }

  public static void createTables(CommandContext commandContext) {
    String type = commandContext.getProcessEngineConfiguration().getDatabaseType();
    LOG.info("Creating the tables of the audit trail ({})", createResource(type));
    commandContext.getDbSqlSession().executeSchemaResource("create", "audit", createResource(type), false);
  }

  public static void dropTables(CommandContext commandContext) {
    String type = commandContext.getProcessEngineConfiguration().getDatabaseType();
    commandContext.getDbSqlSession().executeSchemaResource("drop", "audit", dropResource(type), false);
  }
}
