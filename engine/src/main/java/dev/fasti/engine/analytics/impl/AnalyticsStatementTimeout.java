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
package dev.fasti.engine.analytics.impl;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;

/**
 * How long one analytics statement may run. Without a limit, two slow scans of a long history
 * hold both turns of the {@link AnalyticsLimiter} and their connections for as long as they take,
 * since the engine sets no statement timeout by default ({@code jdbcStatementTimeout}).
 *
 * <p>A MyBatis plugin that the engine registers for its statements: it gives each statement whose
 * parameter is an {@link AnalyticsParameter} the query timeout of that parameter through JDBC
 * ({@link Statement#setQueryTimeout}), which every supported driver implements, while it runs and
 * reads its rows. A lower timeout from {@code jdbcStatementTimeout} stays. Afterwards the previous
 * timeout is set again: H2 keeps the timeout per connection, not per statement, and the next
 * statement on the pooled connection must not inherit it. Other statements are not wrapped and
 * cost nothing.</p>
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "query", args = { Statement.class, ResultHandler.class }))
public class AnalyticsStatementTimeout implements Interceptor {

  /** {@code fasti.analytics.statementTimeoutSeconds}: the timeout in seconds; 0 or less: none of its own. */
  public static final String PROPERTY = "fasti.analytics.statementTimeoutSeconds";
  static final Duration DEFAULT = Duration.ofSeconds(120);

  private static volatile Duration timeout = initial();

  private static Duration initial() {
    String value = System.getProperty(PROPERTY);
    if (value == null || value.trim().isEmpty()) {
      return DEFAULT;
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds <= 0 ? Duration.ZERO : limited(Duration.ofSeconds(seconds));
    } catch (NumberFormatException e) {
      return DEFAULT;
    }
  }

  public static Duration getTimeout() {
    return timeout;
  }

  /** At most a day; zero or negative: no timeout of its own. Statements started before keep theirs. */
  public static void setTimeout(Duration value) {
    timeout = value == null || value.isNegative() || value.isZero() ? Duration.ZERO : limited(value);
  }

  private static Duration limited(Duration value) {
    return value.compareTo(AnalyticsLimiter.MAX) > 0 ? AnalyticsLimiter.MAX : value;
  }

  /** The timeout in whole seconds as JDBC takes it, a part of a second rounded up; 0: none. */
  static int seconds() {
    Duration value = timeout;
    if (value.isZero()) {
      return 0;
    }
    long seconds = value.getSeconds() + (value.getNano() > 0 ? 1 : 0);
    return (int) Math.min(Integer.MAX_VALUE, seconds);
  }

  @Override
  public Object plugin(Object target) {
    if (target instanceof StatementHandler && timeoutOf((StatementHandler) target) > 0) {
      return Plugin.wrap(target, this);
    }
    return target;
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    int seconds = timeoutOf((StatementHandler) invocation.getTarget());
    Statement statement = (Statement) invocation.getArgs()[0];
    int previous = seconds > 0 ? statement.getQueryTimeout() : 0;
    boolean changed = seconds > 0 && (previous <= 0 || previous > seconds);
    if (changed) {
      statement.setQueryTimeout(seconds);
    }
    try {
      return invocation.proceed();
    } finally {
      if (changed) {
        restore(statement, previous);
      }
    }
  }

  protected static void restore(Statement statement, int previous) {
    try {
      statement.setQueryTimeout(Math.max(0, previous));
    } catch (SQLException | RuntimeException e) {
      // a statement that failed may be unusable: it is closed next, and the connection with it if broken
    }
  }

  protected static int timeoutOf(StatementHandler handler) {
    Object parameter = handler.getBoundSql() == null ? null : handler.getBoundSql().getParameterObject();
    return parameter instanceof AnalyticsParameter ? ((AnalyticsParameter) parameter).getStatementTimeoutSeconds() : 0;
  }
}
