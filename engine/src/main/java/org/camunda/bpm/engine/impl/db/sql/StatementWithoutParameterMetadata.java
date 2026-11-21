/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.db.sql;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * A prepared statement that passes every call to another one, except
 * {@link #getParameterMetaData()}, which reports that the metadata is not available (see
 * {@link MyBatisConfiguration}). Written out method by method, default methods of the JDBC
 * interfaces included, so that binding a parameter is a plain call: MyBatis binds through this
 * statement for every parameter of every execution and every row of a JDBC batch.
 */
@SuppressWarnings("deprecation")
public class StatementWithoutParameterMetadata implements PreparedStatement {

  protected final PreparedStatement statement;

  public StatementWithoutParameterMetadata(PreparedStatement statement) {
    this.statement = statement;
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    throw MyBatisConfiguration.PARAMETER_METADATA_NOT_USED;
  }

  @Override
  public String toString() {
    return statement.toString();
  }

  // java.sql.PreparedStatement

  @Override
  public void addBatch() throws SQLException {
    statement.addBatch();
  }

  @Override
  public void clearParameters() throws SQLException {
    statement.clearParameters();
  }

  @Override
  public boolean execute() throws SQLException {
    return statement.execute();
  }

  @Override
  public long executeLargeUpdate() throws SQLException {
    return statement.executeLargeUpdate();
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    return statement.executeQuery();
  }

  @Override
  public int executeUpdate() throws SQLException {
    return statement.executeUpdate();
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    return statement.getMetaData();
  }

  @Override
  public void setArray(int a, Array b) throws SQLException {
    statement.setArray(a, b);
  }

  @Override
  public void setAsciiStream(int a, InputStream b, int c) throws SQLException {
    statement.setAsciiStream(a, b, c);
  }

  @Override
  public void setAsciiStream(int a, InputStream b, long c) throws SQLException {
    statement.setAsciiStream(a, b, c);
  }

  @Override
  public void setAsciiStream(int a, InputStream b) throws SQLException {
    statement.setAsciiStream(a, b);
  }

  @Override
  public void setBigDecimal(int a, BigDecimal b) throws SQLException {
    statement.setBigDecimal(a, b);
  }

  @Override
  public void setBinaryStream(int a, InputStream b, int c) throws SQLException {
    statement.setBinaryStream(a, b, c);
  }

  @Override
  public void setBinaryStream(int a, InputStream b, long c) throws SQLException {
    statement.setBinaryStream(a, b, c);
  }

  @Override
  public void setBinaryStream(int a, InputStream b) throws SQLException {
    statement.setBinaryStream(a, b);
  }

  @Override
  public void setBlob(int a, InputStream b, long c) throws SQLException {
    statement.setBlob(a, b, c);
  }

  @Override
  public void setBlob(int a, InputStream b) throws SQLException {
    statement.setBlob(a, b);
  }

  @Override
  public void setBlob(int a, Blob b) throws SQLException {
    statement.setBlob(a, b);
  }

  @Override
  public void setBoolean(int a, boolean b) throws SQLException {
    statement.setBoolean(a, b);
  }

  @Override
  public void setByte(int a, byte b) throws SQLException {
    statement.setByte(a, b);
  }

  @Override
  public void setBytes(int a, byte[] b) throws SQLException {
    statement.setBytes(a, b);
  }

  @Override
  public void setCharacterStream(int a, Reader b, int c) throws SQLException {
    statement.setCharacterStream(a, b, c);
  }

  @Override
  public void setCharacterStream(int a, Reader b, long c) throws SQLException {
    statement.setCharacterStream(a, b, c);
  }

  @Override
  public void setCharacterStream(int a, Reader b) throws SQLException {
    statement.setCharacterStream(a, b);
  }

  @Override
  public void setClob(int a, Reader b, long c) throws SQLException {
    statement.setClob(a, b, c);
  }

  @Override
  public void setClob(int a, Reader b) throws SQLException {
    statement.setClob(a, b);
  }

  @Override
  public void setClob(int a, Clob b) throws SQLException {
    statement.setClob(a, b);
  }

  @Override
  public void setDate(int a, Date b, Calendar c) throws SQLException {
    statement.setDate(a, b, c);
  }

  @Override
  public void setDate(int a, Date b) throws SQLException {
    statement.setDate(a, b);
  }

  @Override
  public void setDouble(int a, double b) throws SQLException {
    statement.setDouble(a, b);
  }

  @Override
  public void setFloat(int a, float b) throws SQLException {
    statement.setFloat(a, b);
  }

  @Override
  public void setInt(int a, int b) throws SQLException {
    statement.setInt(a, b);
  }

  @Override
  public void setLong(int a, long b) throws SQLException {
    statement.setLong(a, b);
  }

  @Override
  public void setNCharacterStream(int a, Reader b, long c) throws SQLException {
    statement.setNCharacterStream(a, b, c);
  }

  @Override
  public void setNCharacterStream(int a, Reader b) throws SQLException {
    statement.setNCharacterStream(a, b);
  }

  @Override
  public void setNClob(int a, Reader b, long c) throws SQLException {
    statement.setNClob(a, b, c);
  }

  @Override
  public void setNClob(int a, Reader b) throws SQLException {
    statement.setNClob(a, b);
  }

  @Override
  public void setNClob(int a, NClob b) throws SQLException {
    statement.setNClob(a, b);
  }

  @Override
  public void setNString(int a, String b) throws SQLException {
    statement.setNString(a, b);
  }

  @Override
  public void setNull(int a, int b, String c) throws SQLException {
    statement.setNull(a, b, c);
  }

  @Override
  public void setNull(int a, int b) throws SQLException {
    statement.setNull(a, b);
  }

  @Override
  public void setObject(int a, Object b, int c, int d) throws SQLException {
    statement.setObject(a, b, c, d);
  }

  @Override
  public void setObject(int a, Object b, int c) throws SQLException {
    statement.setObject(a, b, c);
  }

  @Override
  public void setObject(int a, Object b, SQLType c, int d) throws SQLException {
    statement.setObject(a, b, c, d);
  }

  @Override
  public void setObject(int a, Object b, SQLType c) throws SQLException {
    statement.setObject(a, b, c);
  }

  @Override
  public void setObject(int a, Object b) throws SQLException {
    statement.setObject(a, b);
  }

  @Override
  public void setRef(int a, Ref b) throws SQLException {
    statement.setRef(a, b);
  }

  @Override
  public void setRowId(int a, RowId b) throws SQLException {
    statement.setRowId(a, b);
  }

  @Override
  public void setSQLXML(int a, SQLXML b) throws SQLException {
    statement.setSQLXML(a, b);
  }

  @Override
  public void setShort(int a, short b) throws SQLException {
    statement.setShort(a, b);
  }

  @Override
  public void setString(int a, String b) throws SQLException {
    statement.setString(a, b);
  }

  @Override
  public void setTime(int a, Time b, Calendar c) throws SQLException {
    statement.setTime(a, b, c);
  }

  @Override
  public void setTime(int a, Time b) throws SQLException {
    statement.setTime(a, b);
  }

  @Override
  public void setTimestamp(int a, Timestamp b, Calendar c) throws SQLException {
    statement.setTimestamp(a, b, c);
  }

  @Override
  public void setTimestamp(int a, Timestamp b) throws SQLException {
    statement.setTimestamp(a, b);
  }

  @Override
  public void setURL(int a, URL b) throws SQLException {
    statement.setURL(a, b);
  }

  @Override
  public void setUnicodeStream(int a, InputStream b, int c) throws SQLException {
    statement.setUnicodeStream(a, b, c);
  }

  // java.sql.Statement

  @Override
  public void addBatch(String a) throws SQLException {
    statement.addBatch(a);
  }

  @Override
  public void cancel() throws SQLException {
    statement.cancel();
  }

  @Override
  public void clearBatch() throws SQLException {
    statement.clearBatch();
  }

  @Override
  public void clearWarnings() throws SQLException {
    statement.clearWarnings();
  }

  @Override
  public void close() throws SQLException {
    statement.close();
  }

  @Override
  public void closeOnCompletion() throws SQLException {
    statement.closeOnCompletion();
  }

  @Override
  public String enquoteIdentifier(String a, boolean b) throws SQLException {
    return statement.enquoteIdentifier(a, b);
  }

  @Override
  public String enquoteLiteral(String a) throws SQLException {
    return statement.enquoteLiteral(a);
  }

  @Override
  public String enquoteNCharLiteral(String a) throws SQLException {
    return statement.enquoteNCharLiteral(a);
  }

  @Override
  public boolean execute(String a, int[] b) throws SQLException {
    return statement.execute(a, b);
  }

  @Override
  public boolean execute(String a, String[] b) throws SQLException {
    return statement.execute(a, b);
  }

  @Override
  public boolean execute(String a, int b) throws SQLException {
    return statement.execute(a, b);
  }

  @Override
  public boolean execute(String a) throws SQLException {
    return statement.execute(a);
  }

  @Override
  public int[] executeBatch() throws SQLException {
    return statement.executeBatch();
  }

  @Override
  public long[] executeLargeBatch() throws SQLException {
    return statement.executeLargeBatch();
  }

  @Override
  public long executeLargeUpdate(String a, int[] b) throws SQLException {
    return statement.executeLargeUpdate(a, b);
  }

  @Override
  public long executeLargeUpdate(String a, String[] b) throws SQLException {
    return statement.executeLargeUpdate(a, b);
  }

  @Override
  public long executeLargeUpdate(String a, int b) throws SQLException {
    return statement.executeLargeUpdate(a, b);
  }

  @Override
  public long executeLargeUpdate(String a) throws SQLException {
    return statement.executeLargeUpdate(a);
  }

  @Override
  public ResultSet executeQuery(String a) throws SQLException {
    return statement.executeQuery(a);
  }

  @Override
  public int executeUpdate(String a, int[] b) throws SQLException {
    return statement.executeUpdate(a, b);
  }

  @Override
  public int executeUpdate(String a, String[] b) throws SQLException {
    return statement.executeUpdate(a, b);
  }

  @Override
  public int executeUpdate(String a, int b) throws SQLException {
    return statement.executeUpdate(a, b);
  }

  @Override
  public int executeUpdate(String a) throws SQLException {
    return statement.executeUpdate(a);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return statement.getConnection();
  }

  @Override
  public int getFetchDirection() throws SQLException {
    return statement.getFetchDirection();
  }

  @Override
  public int getFetchSize() throws SQLException {
    return statement.getFetchSize();
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    return statement.getGeneratedKeys();
  }

  @Override
  public long getLargeMaxRows() throws SQLException {
    return statement.getLargeMaxRows();
  }

  @Override
  public long getLargeUpdateCount() throws SQLException {
    return statement.getLargeUpdateCount();
  }

  @Override
  public int getMaxFieldSize() throws SQLException {
    return statement.getMaxFieldSize();
  }

  @Override
  public int getMaxRows() throws SQLException {
    return statement.getMaxRows();
  }

  @Override
  public boolean getMoreResults() throws SQLException {
    return statement.getMoreResults();
  }

  @Override
  public boolean getMoreResults(int a) throws SQLException {
    return statement.getMoreResults(a);
  }

  @Override
  public int getQueryTimeout() throws SQLException {
    return statement.getQueryTimeout();
  }

  @Override
  public ResultSet getResultSet() throws SQLException {
    return statement.getResultSet();
  }

  @Override
  public int getResultSetConcurrency() throws SQLException {
    return statement.getResultSetConcurrency();
  }

  @Override
  public int getResultSetHoldability() throws SQLException {
    return statement.getResultSetHoldability();
  }

  @Override
  public int getResultSetType() throws SQLException {
    return statement.getResultSetType();
  }

  @Override
  public int getUpdateCount() throws SQLException {
    return statement.getUpdateCount();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return statement.getWarnings();
  }

  @Override
  public boolean isCloseOnCompletion() throws SQLException {
    return statement.isCloseOnCompletion();
  }

  @Override
  public boolean isClosed() throws SQLException {
    return statement.isClosed();
  }

  @Override
  public boolean isPoolable() throws SQLException {
    return statement.isPoolable();
  }

  @Override
  public boolean isSimpleIdentifier(String a) throws SQLException {
    return statement.isSimpleIdentifier(a);
  }

  @Override
  public void setCursorName(String a) throws SQLException {
    statement.setCursorName(a);
  }

  @Override
  public void setEscapeProcessing(boolean a) throws SQLException {
    statement.setEscapeProcessing(a);
  }

  @Override
  public void setFetchDirection(int a) throws SQLException {
    statement.setFetchDirection(a);
  }

  @Override
  public void setFetchSize(int a) throws SQLException {
    statement.setFetchSize(a);
  }

  @Override
  public void setLargeMaxRows(long a) throws SQLException {
    statement.setLargeMaxRows(a);
  }

  @Override
  public void setMaxFieldSize(int a) throws SQLException {
    statement.setMaxFieldSize(a);
  }

  @Override
  public void setMaxRows(int a) throws SQLException {
    statement.setMaxRows(a);
  }

  @Override
  public void setPoolable(boolean a) throws SQLException {
    statement.setPoolable(a);
  }

  @Override
  public void setQueryTimeout(int a) throws SQLException {
    statement.setQueryTimeout(a);
  }

  // java.sql.Wrapper

  @Override
  public boolean isWrapperFor(Class<?> a) throws SQLException {
    return statement.isWrapperFor(a);
  }

  @Override
  public <T> T unwrap(Class<T> a) throws SQLException {
    return statement.unwrap(a);
  }
}
