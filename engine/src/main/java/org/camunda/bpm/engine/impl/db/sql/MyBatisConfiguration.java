/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.db.sql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.util.Date;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.DateTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.SqlTimestampTypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;

/**
 * <p>The MyBatis configuration of the process engine. It keeps the way MyBatis 3.5 chose
 * the type handler of a statement parameter without an explicit <code>jdbcType</code>:
 * by the class of the value, the same on every database.</p>
 *
 * <p>MyBatis 3.6 asks the JDBC driver for the parameter metadata of every statement that
 * has such a parameter and picks the type handler from the reported JDBC type. With
 * PostgreSQL this costs an extra round trip to the server for each execution, also for
 * every row of a JDBC batch, and a failed lookup aborts the running transaction. The
 * reported type may also not fit the value (a numeric column compared with an
 * <code>Integer</code> from a parameter map). And 3.6 no longer registers a type handler for
 * <code>Object</code>: a parameter declared as <code>Object</code>
 * (<code>ListQueryParameterObject.parameter</code>, map values) gets no type handler with
 * drivers without parameter metadata, such as MySQL Connector/J by default, and the handler
 * of its JDBC type when one is given (a <code>String</code> with
 * <code>jdbcType=BINARY</code> fails in the byte array handler). This configuration therefore
 * hides the parameter metadata from the parameter handlers and registers the
 * {@link UnknownTypeHandler} for <code>Object</code> again, for every JDBC type, as MyBatis 3.5
 * resolved it; that handler picks the type handler from the value when the statement runs.</p>
 *
 * <p>MyBatis plans a setting to turn the metadata lookup off and a fallback to the class of
 * the value; once released, they can replace this class.</p>
 *
 * <p>On MySQL and MariaDB, {@link #useDatabaseType(String)} binds dates truncated to whole
 * seconds, see {@link WholeSecondDateTypeHandler}; the expiration times of locks are rounded up
 * before, see {@link #lockExpirationTime(String, long)}.</p>
 */
public class MyBatisConfiguration extends Configuration {

  public MyBatisConfiguration() {
    TypeHandlerRegistry registry = getTypeHandlerRegistry();
    UnknownTypeHandler unknownTypeHandler = new UnknownTypeHandler(this);
    registry.register(Object.class, null, unknownTypeHandler);
    for (JdbcType jdbcType : JdbcType.values()) {
      // MyBatis 3.5 used the array handler for Object and ARRAY; 3.6 still does without an entry
      if (jdbcType != JdbcType.ARRAY) {
        registry.register(Object.class, jdbcType, unknownTypeHandler);
      }
    }
  }

  /**
   * Adapts the type handlers to the database type. Call it before the mappings are parsed:
   * the parameter mappings of the statements resolve their type handler while parsing.
   *
   * @param databaseType the database type of the process engine, such as <code>mysql</code>
   */
  public void useDatabaseType(String databaseType) {
    if (DbSqlSessionFactory.MYSQL.equals(databaseType)) {
      TypeHandlerRegistry registry = getTypeHandlerRegistry();
      registry.register(Date.class, null, new WholeSecondDateTypeHandler());
      registry.register(Timestamp.class, null, new WholeSecondTimestampTypeHandler());
    }
  }

  /**
   * Truncates a point in time to the whole second before it (also before 1970).
   */
  public static long truncateToSeconds(long epochMillis) {
    return Math.floorDiv(epochMillis, 1000L) * 1000L;
  }

  /**
   * The time a lock (of a job or an external task) that starts now expires, as it is stored: on
   * MySQL and MariaDB rounded up to the next whole second, because the stored value has no
   * fraction and {@link WholeSecondDateTypeHandler} would cut it off, ending the lock up to a
   * second early (a lock of 500 ms taken at 12:00:00.100 would end at 12:00:00). Rounded up, a
   * lock lasts at least as long as asked for, and less than a second longer. Other databases
   * keep the milliseconds. Not for times that are no locks: the retry time of a failed external
   * task stays cut off, so that a retry timeout of 0 lets the task be fetched at once.
   *
   * @param databaseType the database type of the process engine, such as <code>mysql</code>
   * @param epochMillis the time the lock was asked to expire at
   */
  public static Date lockExpirationTime(String databaseType, long epochMillis) {
    if (DbSqlSessionFactory.MYSQL.equals(databaseType)) {
      long truncated = truncateToSeconds(epochMillis);
      return new Date(truncated == epochMillis ? epochMillis : truncated + 1000L);
    }
    return new Date(epochMillis);
  }

  /** {@link #lockExpirationTime(String, long)} for the database of the current command. */
  public static Date lockExpirationTime(long epochMillis) {
    ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
    return lockExpirationTime(configuration == null ? null : configuration.getDatabaseType(), epochMillis);
  }

  @Override
  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject,
      BoundSql boundSql) {
    ParameterHandler parameterHandler = mappedStatement.getLang()
        .createParameterHandler(mappedStatement, parameterObject, boundSql);
    return (ParameterHandler) interceptorChain.pluginAll(new WithoutParameterMetadata(parameterHandler));
  }

  /**
   * Passes the statement to the wrapped handler with
   * {@link PreparedStatement#getParameterMetaData()} reporting that the metadata is not
   * available; all other calls reach the statement unchanged.
   */
  protected static class WithoutParameterMetadata implements ParameterHandler {

    protected final ParameterHandler delegate;

    public WithoutParameterMetadata(ParameterHandler delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object getParameterObject() {
      return delegate.getParameterObject();
    }

    @Override
    public void setParameters(PreparedStatement statement) throws SQLException {
      delegate.setParameters(hideParameterMetadata(statement));
    }

    protected static PreparedStatement hideParameterMetadata(PreparedStatement statement) {
      if (statement instanceof CallableStatement) {
        // a stored procedure call: rare, and its output parameters are read through the same
        // object, so it keeps its type by a dynamic proxy
        return (PreparedStatement) Proxy.newProxyInstance(MyBatisConfiguration.class.getClassLoader(),
            new Class<?>[] { CallableStatement.class }, new StatementHandler(statement));
      }
      // every other statement: a plain delegate, so that binding a parameter costs no reflective call
      return new StatementWithoutParameterMetadata(statement);
    }
  }

  /**
   * Answers every request for the parameter metadata. MyBatis asks once per statement execution
   * and swallows the exception, so it is created once and carries no stack trace.
   */
  protected static final SQLFeatureNotSupportedException PARAMETER_METADATA_NOT_USED = new ParameterMetadataNotUsedException();

  protected static class ParameterMetadataNotUsedException extends SQLFeatureNotSupportedException {

    private static final long serialVersionUID = 1L;

    protected ParameterMetadataNotUsedException() {
      super("Parameter metadata is not used by the process engine");
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      // shared by all statements: no stack trace
      return this;
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
      // shared by all statements: stays without stack trace
    }

    @Override
    public void setNextException(SQLException next) {
      // shared by all statements: nothing is chained to it
    }
  }

  protected static class StatementHandler implements InvocationHandler {

    protected final PreparedStatement statement;

    protected StatementHandler(PreparedStatement statement) {
      this.statement = statement;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("getParameterMetaData".equals(method.getName()) && method.getParameterCount() == 0) {
        throw PARAMETER_METADATA_NOT_USED;
      }
      if (method.getDeclaringClass() == Object.class) {
        switch (method.getName()) {
        case "equals":
          return proxy == args[0];
        case "hashCode":
          return System.identityHashCode(proxy);
        case "toString":
          return statement.toString();
        default:
          break;
        }
      }
      try {
        return method.invoke(statement, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }

  /**
   * <p>Binds a {@link Date} as a timestamp truncated to whole seconds; reads it as
   * {@link DateTypeHandler} does. Used for the database type <code>mysql</code> (MySQL and
   * MariaDB).</p>
   *
   * <p>The MySQL schema of Camunda 7.24 declares the time columns as <code>DATETIME</code> and
   * <code>TIMESTAMP</code> without fractional seconds. The server cuts off the milliseconds of
   * a stored value (MariaDB truncates, MySQL rounds unless <code>TIME_TRUNCATE_FRACTIONAL</code>
   * is set), but compares a query parameter with its milliseconds. MySQL Connector/J with
   * <code>sendFractionalSeconds=false</code>, the setting Camunda tests MySQL with, truncates
   * every value in the driver; MariaDB Connector/J has no such option. Without it, a row
   * stored in the same second as the parameter is missed by <code>&gt;=</code> and
   * <code>=</code> (<code>createdAfter</code>, <code>startedAfter</code>,
   * <code>dueDate</code>, ...).</p>
   *
   * <p>Truncating every value the engine binds, stored or compared, does what
   * <code>sendFractionalSeconds=false</code> does, with any driver. A column value
   * <code>c</code> is a whole second, so comparing it with the truncated parameter
   * <code>p</code>:</p>
   * <ul>
   *   <li><code>c &gt; p</code> and <code>c &lt;= p</code> give the same result as before;</li>
   *   <li><code>c &gt;= p</code> and <code>c = p</code> now also match a row stored in the second
   *   of <code>p</code> (the fix);</li>
   *   <li><code>c &lt; p</code> no longer matches a row stored in the second of <code>p</code>
   *   (<code>dueBefore</code>, <code>createdBefore</code> of tasks, an expired lock in the job
   *   and external task acquisition, ...): it is complementary to <code>&gt;=</code>, and a
   *   half-open interval <code>[from, to)</code> still divides the rows without gap or
   *   overlap.</li>
   * </ul>
   * <p>All comparisons therefore work on whole seconds, as the stored values do. A stored
   * value is never later than the instant the engine wrote (MySQL no longer rounds it up).</p>
   */
  public static class WholeSecondDateTypeHandler extends DateTypeHandler {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
        throws SQLException {
      ps.setTimestamp(i, new Timestamp(truncateToSeconds(parameter.getTime())));
    }
  }

  /**
   * Binds a {@link Timestamp} truncated to whole seconds, see
   * {@link WholeSecondDateTypeHandler}; reads it as {@link SqlTimestampTypeHandler} does.
   */
  public static class WholeSecondTimestampTypeHandler extends SqlTimestampTypeHandler {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Timestamp parameter, JdbcType jdbcType)
        throws SQLException {
      ps.setTimestamp(i, new Timestamp(truncateToSeconds(parameter.getTime())));
    }
  }

}
