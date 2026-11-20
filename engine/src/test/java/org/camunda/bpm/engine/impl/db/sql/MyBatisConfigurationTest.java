/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.db.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.DateOnlyTypeHandler;
import org.apache.ibatis.type.DateTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;
import org.apache.ibatis.type.TimeOnlyTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;
import org.camunda.bpm.engine.impl.HistoricVariableInstanceQueryImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class MyBatisConfigurationTest {

  @Test
  public void shouldResolveObjectParametersByTheirValue() {
    TypeHandlerRegistry registry = new MyBatisConfiguration().getTypeHandlerRegistry();

    assertThat(registry.getTypeHandler(Object.class, (JdbcType) null)).isInstanceOf(UnknownTypeHandler.class);
    assertThat(registry.getTypeHandler(String.class, (JdbcType) null)).isInstanceOf(StringTypeHandler.class);
  }

  @Test
  public void shouldResolveObjectParametersWithJdbcTypeByTheirValue() throws SQLException {
    // e.g. selectCommentsByProcessInstanceId_postgres: #{parameter,jdbcType=BINARY} with a String
    TypeHandlerRegistry registry = new MyBatisConfiguration().getTypeHandlerRegistry();
    PreparedStatement statement = mock(PreparedStatement.class);

    TypeHandler<Object> handler = (TypeHandler<Object>) registry.getTypeHandler(Object.class, JdbcType.BINARY);
    handler.setParameter(statement, 1, "processInstanceId", JdbcType.BINARY);

    assertThat(handler).isInstanceOf(UnknownTypeHandler.class);
    verify(statement).setString(1, "processInstanceId");
  }

  @Test
  public void shouldHideParameterMetadataFromParameterHandler() throws SQLException {
    PreparedStatement statement = mock(PreparedStatement.class);
    List<PreparedStatement> received = new ArrayList<>();

    ParameterHandler handler = new MyBatisConfiguration.WithoutParameterMetadata(new ParameterHandler() {
      @Override
      public Object getParameterObject() {
        return "parameter";
      }

      @Override
      public void setParameters(PreparedStatement ps) throws SQLException {
        received.add(ps);
        assertThatThrownBy(ps::getParameterMetaData).isInstanceOf(SQLFeatureNotSupportedException.class);
        ps.setString(1, "value");
      }
    });

    handler.setParameters(statement);

    assertThat(handler.getParameterObject()).isEqualTo("parameter");
    assertThat(received).hasSize(1);
    assertThat(received.get(0)).isNotSameAs(statement);
    verify(statement).setString(1, "value");
    verify(statement, never()).getParameterMetaData();
  }

  @Test
  public void shouldKeepCallableStatementType() throws SQLException {
    CallableStatement statement = mock(CallableStatement.class);
    List<PreparedStatement> received = new ArrayList<>();

    new MyBatisConfiguration.WithoutParameterMetadata(new ParameterHandler() {
      @Override
      public Object getParameterObject() {
        return null;
      }

      @Override
      public void setParameters(PreparedStatement ps) {
        received.add(ps);
      }
    }).setParameters(statement);

    assertThat(received.get(0)).isInstanceOf(CallableStatement.class);
  }

  @Test
  public void shouldPassSqlExceptionsOfTheStatement() throws SQLException {
    PreparedStatement statement = mock(PreparedStatement.class);
    SQLException failure = new SQLException("failure");
    doThrow(failure).when(statement).setInt(1, 1);

    ParameterHandler handler = new MyBatisConfiguration.WithoutParameterMetadata(new ParameterHandler() {
      @Override
      public Object getParameterObject() {
        return null;
      }

      @Override
      public void setParameters(PreparedStatement ps) throws SQLException {
        ps.setInt(1, 1);
      }
    });

    assertThatThrownBy(() -> handler.setParameters(statement)).isSameAs(failure);
  }

  // Dates on MySQL and MariaDB (database type mysql): bound truncated to whole seconds

  private static final long MILLIS = 1_700_000_000_789L;
  private static final Timestamp TRUNCATED = new Timestamp(1_700_000_000_000L);

  @Test
  public void shouldTruncateToTheSecondBefore() {
    assertThat(MyBatisConfiguration.truncateToSeconds(MILLIS)).isEqualTo(1_700_000_000_000L);
    assertThat(MyBatisConfiguration.truncateToSeconds(1_700_000_000_000L)).isEqualTo(1_700_000_000_000L);
    assertThat(MyBatisConfiguration.truncateToSeconds(999L)).isZero();
    // before 1970: the second before, as MySQL and MariaDB cut off the fraction
    assertThat(MyBatisConfiguration.truncateToSeconds(-1L)).isEqualTo(-1000L);
    assertThat(MyBatisConfiguration.truncateToSeconds(-1000L)).isEqualTo(-1000L);
  }

  @Test
  public void shouldBindDatesTruncatedToSecondsOnMySql() throws SQLException {
    TypeHandlerRegistry registry = configuration(DbSqlSessionFactory.MYSQL).getTypeHandlerRegistry();
    PreparedStatement statement = mock(PreparedStatement.class);
    Timestamp timestamp = new Timestamp(MILLIS);
    timestamp.setNanos(789_123_456);

    // a property of type Date, with and without jdbcType=TIMESTAMP
    bind(registry.getTypeHandler(Date.class, JdbcType.TIMESTAMP), statement, 1, new Date(MILLIS), JdbcType.TIMESTAMP);
    bind(registry.getTypeHandler(Date.class, (JdbcType) null), statement, 2, new Date(MILLIS), null);
    // a property of type Date holding a Timestamp
    bind(registry.getTypeHandler(Date.class, JdbcType.TIMESTAMP), statement, 3, timestamp, JdbcType.TIMESTAMP);
    // a property of type Timestamp
    bind(registry.getTypeHandler(Timestamp.class, (JdbcType) null), statement, 4, timestamp, null);
    // a parameter declared as Object (ListQueryParameterObject, parameter maps, native queries)
    bind(registry.getTypeHandler(Object.class, JdbcType.TIMESTAMP), statement, 5, new Date(MILLIS), JdbcType.TIMESTAMP);
    bind(registry.getTypeHandler(Object.class, JdbcType.TIMESTAMP), statement, 6, timestamp, JdbcType.TIMESTAMP);

    for (int i = 1; i <= 6; i++) {
      verify(statement).setTimestamp(i, TRUNCATED);
    }
  }

  @Test
  public void shouldKeepDateOnlyAndTimeOnlyHandlersOnMySql() {
    TypeHandlerRegistry registry = configuration(DbSqlSessionFactory.MYSQL).getTypeHandlerRegistry();

    assertThat(registry.getTypeHandler(Date.class, JdbcType.DATE)).isInstanceOf(DateOnlyTypeHandler.class);
    assertThat(registry.getTypeHandler(Date.class, JdbcType.TIME)).isInstanceOf(TimeOnlyTypeHandler.class);
  }

  @Test
  public void shouldReadDatesUnchangedOnMySql() throws SQLException {
    TypeHandlerRegistry registry = configuration(DbSqlSessionFactory.MYSQL).getTypeHandlerRegistry();
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getTimestamp("TIME_")).thenReturn(new Timestamp(MILLIS));

    Object value = registry.getTypeHandler(Date.class, JdbcType.TIMESTAMP).getResult(resultSet, "TIME_");

    assertThat(value).isEqualTo(new Date(MILLIS));
  }

  @Test
  public void shouldBindDatesWithMillisecondsOnOtherDatabases() throws SQLException {
    for (String databaseType : new String[] { DbSqlSessionFactory.H2, DbSqlSessionFactory.POSTGRES,
        DbSqlSessionFactory.ORACLE, DbSqlSessionFactory.MSSQL, DbSqlSessionFactory.DB2, null }) {
      TypeHandlerRegistry registry = configuration(databaseType).getTypeHandlerRegistry();
      PreparedStatement statement = mock(PreparedStatement.class);

      bind(registry.getTypeHandler(Date.class, JdbcType.TIMESTAMP), statement, 1, new Date(MILLIS), JdbcType.TIMESTAMP);
      bind(registry.getTypeHandler(Object.class, JdbcType.TIMESTAMP), statement, 2, new Date(MILLIS), JdbcType.TIMESTAMP);

      assertThat(registry.getTypeHandler(Date.class, JdbcType.TIMESTAMP)).isExactlyInstanceOf(DateTypeHandler.class);
      verify(statement).setTimestamp(1, new Timestamp(MILLIS));
      verify(statement).setTimestamp(2, new Timestamp(MILLIS));
    }
  }

  @Test
  public void shouldBindTruncatedDatesInTheEngineStatementsOnMySql() throws SQLException {
    Configuration configuration = engineMappings(DbSqlSessionFactory.MYSQL);

    // stored: insert of a historic variable instance
    HistoricVariableInstanceEntity variable = new HistoricVariableInstanceEntity();
    variable.setCreateTime(new Date(MILLIS));
    verify(setParameters(configuration, "insertHistoricVariableInstance", variable))
        .setTimestamp(anyInt(), eq(TRUNCATED));

    // compared: CREATE_TIME_ >= #{createdAfter}
    HistoricVariableInstanceQueryImpl query = new HistoricVariableInstanceQueryImpl();
    query.createdAfter(new Date(MILLIS));
    verify(setParameters(configuration, "selectHistoricVariableInstanceCountByQueryCriteria", query))
        .setTimestamp(anyInt(), eq(TRUNCATED));
  }

  @Test
  public void shouldBindDatesWithMillisecondsInTheEngineStatementsOnOtherDatabases() throws SQLException {
    Configuration configuration = engineMappings(DbSqlSessionFactory.H2);

    HistoricVariableInstanceQueryImpl query = new HistoricVariableInstanceQueryImpl();
    query.createdAfter(new Date(MILLIS));
    verify(setParameters(configuration, "selectHistoricVariableInstanceCountByQueryCriteria", query))
        .setTimestamp(anyInt(), eq(new Timestamp(MILLIS)));
  }

  protected static MyBatisConfiguration configuration(String databaseType) {
    MyBatisConfiguration configuration = new MyBatisConfiguration();
    configuration.useDatabaseType(databaseType);
    return configuration;
  }

  protected static void bind(TypeHandler<?> handler, PreparedStatement statement, int index, Object value,
      JdbcType jdbcType) throws SQLException {
    ((TypeHandler<Object>) handler).setParameter(statement, index, value, jdbcType);
  }

  /**
   * Builds the MyBatis configuration of the process engine as it does on start-up.
   */
  protected static Configuration engineMappings(String type) {
    StandaloneProcessEngineConfiguration engineConfiguration = new StandaloneProcessEngineConfiguration() {
      {
        setDatabaseType(type);
        setDataSource(mock(DataSource.class));
        initTransactionFactory();
        initSqlSessionFactory();
      }
    };
    return engineConfiguration.getSqlSessionFactory().getConfiguration();
  }

  /**
   * Sets the parameters of an engine statement as MyBatis does when it runs it; the type
   * handlers of the parameters are resolved at that point.
   */
  protected static PreparedStatement setParameters(Configuration configuration, String statement, Object parameter)
      throws SQLException {
    MappedStatement mappedStatement = configuration.getMappedStatement(statement);
    BoundSql boundSql = mappedStatement.getBoundSql(parameter);
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    configuration.newParameterHandler(mappedStatement, parameter, boundSql).setParameters(preparedStatement);
    return preparedStatement;
  }

}
