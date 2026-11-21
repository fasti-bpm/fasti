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
package dev.fasti.engine.audit;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

/**
 * Records what a data source sends to the database, in the order in which it is sent: the SQL of
 * each execution (a JDBC batch counts once, when it is executed) and each commit and rollback.
 * For tests that prove the number and the order of the statements of a command.
 */
public final class StatementTrace {

  public static final String COMMIT = "COMMIT";
  public static final String ROLLBACK = "ROLLBACK";

  private final List<String> executed = Collections.synchronizedList(new ArrayList<>());

  /** The statements executed since the last {@link #clear()}, lower case, with single spaces. */
  public List<String> executed() {
    synchronized (executed) {
      return new ArrayList<>(executed);
    }
  }

  public void clear() {
    executed.clear();
  }

  /** The position of the first statement that contains the text, or -1. */
  public static int first(List<String> statements, String text) {
    for (int i = 0; i < statements.size(); i++) {
      if (statements.get(i).contains(text)) {
        return i;
      }
    }
    return -1;
  }

  /** The position of the last statement that contains the text, or -1. */
  public static int last(List<String> statements, String text) {
    for (int i = statements.size() - 1; i >= 0; i--) {
      if (statements.get(i).contains(text)) {
        return i;
      }
    }
    return -1;
  }

  public DataSource wrap(DataSource target) {
    return proxy(DataSource.class, target, (method, args, result) -> result instanceof Connection
        ? connection((Connection) result) : result);
  }

  Connection connection(Connection target) {
    return proxy(Connection.class, target, (method, args, result) -> {
      String name = method.getName();
      if ("commit".equals(name)) {
        executed.add(COMMIT);
      } else if ("rollback".equals(name) && (args == null || args.length == 0)) {
        executed.add(ROLLBACK);
      } else if (result instanceof CallableStatement) {
        return statement(CallableStatement.class, (CallableStatement) result, (String) args[0]);
      } else if (result instanceof PreparedStatement) {
        return statement(PreparedStatement.class, (PreparedStatement) result, (String) args[0]);
      } else if (result instanceof Statement) {
        return statement(Statement.class, (Statement) result, null);
      }
      return result;
    });
  }

  <T extends Statement> T statement(Class<T> type, T target, String sql) {
    return proxy(type, target, (method, args, result) -> {
      if (method.getName().startsWith("execute")) {
        String text = sql != null ? sql : args != null && args.length > 0 && args[0] instanceof String ? (String) args[0] : null;
        if (text != null) {
          executed.add(text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT));
        }
      }
      return result;
    });
  }

  interface After {
    Object apply(Method method, Object[] args, Object result);
  }

  @SuppressWarnings("unchecked")
  static <T> T proxy(Class<T> type, T target, After after) {
    InvocationHandler handler = (proxy, method, args) -> {
      Object result;
      try {
        result = method.invoke(target, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
      return after.apply(method, args, result);
    };
    return (T) Proxy.newProxyInstance(StatementTrace.class.getClassLoader(), new Class<?>[] { type }, handler);
  }
}
