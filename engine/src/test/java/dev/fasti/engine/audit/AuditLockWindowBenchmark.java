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
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;

/**
 * Not a test: measures how many writing transactions per second one engine completes with many
 * threads while the audit trail is on, to see how long a chain of the audit trail is held.
 * Each thread starts a process instance with five variables and completes its user task, as its
 * own user. Run the main method; the settings are system properties:
 *
 * <pre>
 * -Dbench.url=jdbc:postgresql://localhost:24051/fasti -Dbench.driver=org.postgresql.Driver
 * -Dbench.user=fasti -Dbench.password=fasti -Dbench.threads=16 -Dbench.chains=4
 * -Dbench.warmup=5 -Dbench.seconds=20 -Dbench.audit=true -Dbench.roundtripMicros=1000
 * </pre>
 *
 * {@code bench.roundtripMicros} makes every statement execution and every commit wait that long
 * first, as a round trip to a database on another host does (a database on the same host answers
 * in some ten microseconds, which hides how long locks are held between two statements).
 * It prints one line of JSON with the transactions per second.
 */
public final class AuditLockWindowBenchmark {

  private AuditLockWindowBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    String url = System.getProperty("bench.url", "jdbc:h2:mem:fasti-audit-bench;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000");
    int threads = Integer.getInteger("bench.threads", 16);
    int chains = Integer.getInteger("bench.chains", 4);
    int warmup = Integer.getInteger("bench.warmup", 5);
    int seconds = Integer.getInteger("bench.seconds", 20);
    boolean audit = Boolean.parseBoolean(System.getProperty("bench.audit", "true"));
    long roundtripNanos = Long.getLong("bench.roundtripMicros", 0L) * 1000L;

    ProcessEngineConfigurationImpl configuration = new StandaloneProcessEngineConfiguration();
    configuration.setJdbcUrl(url);
    configuration.setJdbcDriver(System.getProperty("bench.driver", "org.h2.Driver"));
    configuration.setJdbcUsername(System.getProperty("bench.user", "sa"));
    configuration.setJdbcPassword(System.getProperty("bench.password", ""));
    configuration.setProcessEngineName("audit-bench");
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
    configuration.setJobExecutorActivate(false);
    configuration.setDbMetricsReporterActivate(false);
    configuration.setMetricsEnabled(false);
    configuration.setTaskMetricsEnabled(false);
    configuration.setEnforceHistoryTimeToLive(false);
    configuration.setJdbcMaxActiveConnections(threads + 4);
    configuration.setJdbcMaxIdleConnections(threads + 4);
    configuration.setIdGenerator(new StrongUuidGenerator());
    if (roundtripNanos > 0) {
      PooledDataSource dataSource = new PooledDataSource(ReflectUtil.getClassLoader(), configuration.getJdbcDriver(),
          configuration.getJdbcUrl(), configuration.getJdbcUsername(), configuration.getJdbcPassword());
      dataSource.setPoolMaximumActiveConnections(threads + 4);
      dataSource.setPoolMaximumIdleConnections(threads + 4);
      configuration.setDataSource(delayed(DataSource.class, dataSource, roundtripNanos));
    }
    if (audit) {
      AuditTrailPlugin plugin = new AuditTrailPlugin();
      plugin.setNodeId("bench");
      plugin.setChains(chains);
      configuration.getProcessEnginePlugins().add(plugin);
    }
    ProcessEngine engine = configuration.buildProcessEngine();
    try {
      String key = "bench" + System.currentTimeMillis();
      engine.getRepositoryService().createDeployment().addModelInstance(key + ".bpmn",
          Bpmn.createExecutableProcess(key).camundaHistoryTimeToLive(180).startEvent().userTask("review").endEvent().done())
          .deploy();
      AtomicBoolean counting = new AtomicBoolean();
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicLong transactions = new AtomicLong();
      AtomicLong failures = new AtomicLong();
      CountDownLatch done = new CountDownLatch(threads);
      List<Thread> workers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        String user = "user" + t;
        Thread worker = new Thread(() -> {
          try {
            int i = 0;
            while (running.get()) {
              engine.getIdentityService().setAuthenticatedUserId(user);
              try {
                Map<String, Object> variables = new HashMap<>();
                variables.put("customer", "customer-" + i);
                variables.put("amount", 1000L + i);
                variables.put("approved", i % 2 == 0);
                variables.put("note", "a note of some length for instance " + i);
                variables.put("priority", i % 5);
                String instance = engine.getRuntimeService().startProcessInstanceByKey(key, user + "-" + i, variables).getId();
                if (counting.get()) {
                  transactions.incrementAndGet();
                }
                Task task = engine.getTaskService().createTaskQuery().processInstanceId(instance).singleResult();
                engine.getTaskService().complete(task.getId());
                if (counting.get()) {
                  transactions.incrementAndGet();
                }
              } catch (RuntimeException e) {
                failures.incrementAndGet();
              } finally {
                engine.getIdentityService().clearAuthentication();
              }
              i++;
            }
          } finally {
            done.countDown();
          }
        }, "bench-" + t);
        workers.add(worker);
        worker.start();
      }
      Thread.sleep(warmup * 1000L);
      long start = System.nanoTime();
      counting.set(true);
      Thread.sleep(seconds * 1000L);
      counting.set(false);
      double elapsed = (System.nanoTime() - start) / 1e9;
      running.set(false);
      done.await();
      System.out.printf("{\"database\": \"%s\", \"audit\": %s, \"threads\": %d, \"chains\": %d, \"roundtrip_micros\": %d, "
          + "\"tx_per_s\": %.1f, \"failures\": %d}%n", configuration.getDatabaseType(), audit, threads, chains,
          roundtripNanos / 1000, transactions.get() / elapsed, failures.get());
    } finally {
      engine.close();
    }
  }

  /**
   * The target with a wait before every statement execution and every commit, also of the
   * connections and statements it hands out.
   */
  @SuppressWarnings("unchecked")
  static <T> T delayed(Class<T> type, T target, long nanos) {
    InvocationHandler handler = (proxy, method, arguments) -> {
      String name = method.getName();
      if ((target instanceof Statement && name.startsWith("execute")) || (target instanceof Connection && name.equals("commit"))) {
        LockSupport.parkNanos(nanos);
      }
      Object result;
      try {
        result = method.invoke(target, arguments);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
      if (result instanceof CallableStatement) {
        return delayed(CallableStatement.class, (CallableStatement) result, nanos);
      } else if (result instanceof PreparedStatement) {
        return delayed(PreparedStatement.class, (PreparedStatement) result, nanos);
      } else if (result instanceof Statement) {
        return delayed(Statement.class, (Statement) result, nanos);
      } else if (result instanceof Connection && !(target instanceof Statement)) {
        return delayed(Connection.class, (Connection) result, nanos);
      }
      return result;
    };
    return (T) Proxy.newProxyInstance(AuditLockWindowBenchmark.class.getClassLoader(), new Class<?>[] { type }, handler);
  }
}
