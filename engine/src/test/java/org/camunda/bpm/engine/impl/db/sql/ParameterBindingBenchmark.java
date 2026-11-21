/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.db.sql;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;

/**
 * Not a test: measures what binding the parameters of one statement costs through the statement
 * wrapper of {@link MyBatisConfiguration}, as a dynamic proxy (how it was) and as
 * {@link StatementWithoutParameterMetadata} (how it is), next to binding on the statement itself.
 * Binds the 24 parameters of an insert, the size of a history insert, on an H2 statement that is
 * never executed. Run the main method; it prints the median of the rounds in nanoseconds per
 * statement.
 */
public final class ParameterBindingBenchmark {

  static final int PARAMETERS = 24;
  static final int ITERATIONS = 1_000_000;
  static final int ROUNDS = 9;

  private ParameterBindingBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:bind", "sa", "")) {
      StringBuilder columns = new StringBuilder();
      StringBuilder marks = new StringBuilder();
      for (int i = 1; i <= PARAMETERS; i++) {
        String type = i <= 14 ? "varchar(255)" : i <= 18 ? "bigint" : i <= 21 ? "timestamp" : i <= 23 ? "integer" : "varchar(64)";
        columns.append(i == 1 ? "" : ", ").append("C").append(i).append(' ').append(type);
        marks.append(i == 1 ? "?" : ", ?");
      }
      try (Statement create = connection.createStatement()) {
        create.execute("create table BIND (" + columns + ")");
      }
      try (PreparedStatement target = connection.prepareStatement("insert into BIND values (" + marks + ")")) {
        PreparedStatement proxy = (PreparedStatement) Proxy.newProxyInstance(ParameterBindingBenchmark.class.getClassLoader(),
            new Class<?>[] { PreparedStatement.class }, new MyBatisConfiguration.StatementHandler(target));
        PreparedStatement plain = new StatementWithoutParameterMetadata(target);
        // warm up all three
        for (int i = 0; i < 3; i++) {
          run(target);
          run(proxy);
          run(plain);
        }
        double[] direct = new double[ROUNDS];
        double[] proxied = new double[ROUNDS];
        double[] delegated = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
          direct[round] = run(target);
          proxied[round] = run(proxy);
          delegated[round] = run(plain);
        }
        System.out.printf("{\"parameters\": %d, \"statement_ns\": %.0f, \"dynamic_proxy_ns\": %.0f, \"plain_wrapper_ns\": %.0f}%n",
            PARAMETERS, median(direct), median(proxied), median(delegated));
      }
    }
  }

  /** Nanoseconds per statement. */
  static double run(PreparedStatement statement) throws SQLException {
    Timestamp time = new Timestamp(1_800_000_000_000L);
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      for (int p = 1; p <= 14; p++) {
        statement.setString(p, "0f8fad5b-d9cb-469f-a165-70867728950e");
      }
      for (int p = 15; p <= 18; p++) {
        statement.setLong(p, i);
      }
      for (int p = 19; p <= 21; p++) {
        statement.setTimestamp(p, time);
      }
      statement.setInt(22, i);
      statement.setInt(23, 1);
      statement.setNull(24, Types.VARCHAR);
    }
    return (System.nanoTime() - start) / (double) ITERATIONS;
  }

  static double median(double[] values) {
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    return sorted[sorted.length / 2];
  }
}
