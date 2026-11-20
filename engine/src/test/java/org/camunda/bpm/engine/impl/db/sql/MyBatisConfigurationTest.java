/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.db.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;
import org.junit.Test;

public class MyBatisConfigurationTest {

  @Test
  public void shouldResolveObjectParametersByTheirValue() {
    TypeHandlerRegistry registry = new MyBatisConfiguration().getTypeHandlerRegistry();

    assertThat(registry.getTypeHandler(Object.class, (JdbcType) null)).isInstanceOf(UnknownTypeHandler.class);
    assertThat(registry.getTypeHandler(String.class, (JdbcType) null)).isInstanceOf(StringTypeHandler.class);
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

}
