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
package dev.fasti.engine.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The statement timeout of the analytics: every analytics statement gets it through JDBC, the
 * other statements of the engine do not, and a statement that runs into it fails the query with a
 * clear message. The data source records the query timeout of each statement when it is executed,
 * and lets the statements that contain a given text time out.
 */
public class ProcessAnalyticsTimeoutTest {

  static ProcessEngine engine;
  static final List<String> EXECUTED = Collections.synchronizedList(new ArrayList<>());
  static volatile String timeOutOn;

  protected Duration timeout;

  @BeforeClass
  public static void buildEngine() {
    ProcessEngineConfigurationImpl configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-analytics-timeout;DB_CLOSE_DELAY=10");
    PooledDataSource dataSource = new PooledDataSource(ReflectUtil.getClassLoader(), configuration.getJdbcDriver(),
        configuration.getJdbcUrl(), configuration.getJdbcUsername(), configuration.getJdbcPassword());
    configuration.setDataSource(recording(dataSource));
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("analytics-timeout");
    configuration.setHistory("full");
    configuration.setJobExecutorActivate(false);
    configuration.setEnforceHistoryTimeToLive(false);
    engine = configuration.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addModelInstance("check.bpmn",
        Bpmn.createExecutableProcess("check").startEvent().userTask("review").endEvent().done()).deploy();
    for (int index = 0; index < 3; index++) {
      engine.getRuntimeService().startProcessInstanceByKey("check");
    }
    for (Task task : engine.getTaskService().createTaskQuery().listPage(0, 2)) {
      engine.getTaskService().complete(task.getId());
    }
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
    engine = null;
  }

  @Before
  public void remember() {
    timeout = ProcessAnalytics.getStatementTimeout();
    EXECUTED.clear();
  }

  @After
  public void restore() {
    ProcessAnalytics.setStatementTimeout(timeout);
    timeOutOn = null;
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).getSqlSessionFactory().getConfiguration()
        .setDefaultStatementTimeout(null);
  }

  /** A range that ends now: never answered from the cache, so every call reaches the database. */
  ProcessAnalyticsQuery query() {
    return ProcessAnalytics.query(engine).processDefinitionKey("check");
  }

  /** The query timeouts of the executed statements that contain the text, as "timeout sql". */
  static List<Integer> timeouts(String text) {
    List<Integer> timeouts = new ArrayList<>();
    synchronized (EXECUTED) {
      for (String entry : EXECUTED) {
        int space = entry.indexOf(' ');
        if (entry.substring(space + 1).contains(text)) {
          timeouts.add(Integer.valueOf(entry.substring(0, space)));
        }
      }
    }
    return timeouts;
  }

  @Test
  public void shouldGiveEveryAnalyticsStatementTheTimeoutAndNoOtherStatement() {
    assertThat(ProcessAnalytics.getStatementTimeout()).isEqualTo(Duration.ofSeconds(120));

    query().activities();
    query().userTasks(100);
    query().instances();
    engine.getTaskService().createTaskQuery().list();

    assertThat(timeouts("from act_hi_actinst a")).isNotEmpty().containsOnly(120);
    assertThat(timeouts("from act_hi_taskinst t")).isNotEmpty().containsOnly(120);
    assertThat(timeouts("from act_hi_incident i")).isNotEmpty().containsOnly(120);
    assertThat(timeouts("from act_hi_procinst p")).isNotEmpty().containsOnly(120);
    assertThat(timeouts("from act_ru_task res")).isNotEmpty().containsOnly(0);
  }

  @Test
  public void shouldFollowTheSettingInWholeSeconds() {
    ProcessAnalytics.setStatementTimeout(Duration.ofMillis(1500));
    query().instances();
    assertThat(timeouts("from act_hi_procinst p")).isNotEmpty().containsOnly(2);

    EXECUTED.clear();
    ProcessAnalytics.setStatementTimeout(Duration.ZERO);
    query().instances();
    assertThat(timeouts("from act_hi_procinst p")).isNotEmpty().containsOnly(0);
  }

  @Test
  public void shouldKeepALowerTimeoutOfTheEngine() {
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).getSqlSessionFactory().getConfiguration()
        .setDefaultStatementTimeout(30);
    query().instances();
    assertThat(timeouts("from act_hi_procinst p")).isNotEmpty().containsOnly(30);

    EXECUTED.clear();
    ProcessAnalytics.setStatementTimeout(Duration.ofSeconds(10));
    query().instances();
    assertThat(timeouts("from act_hi_procinst p")).isNotEmpty().containsOnly(10);
  }

  @Test
  public void shouldFailWithAClearMessageWhenAStatementTimesOut() {
    timeOutOn = "from act_hi_taskinst t";

    assertThatThrownBy(() -> query().userTasks(100))
        .isInstanceOf(AnalyticsTimeoutException.class)
        .isInstanceOf(AnalyticsBusyException.class)
        .hasMessageContaining("process check")
        .hasMessageContaining("120 s")
        .hasMessageContaining("shorter range")
        .hasRootCauseInstanceOf(SQLTimeoutException.class);

    // the next call reads again
    timeOutOn = null;
    assertThat(query().userTasks(100).getTasks()).isEqualTo(2);
  }

  // the recording data source //////////////////////////////////////////////////////////

  static DataSource recording(DataSource target) {
    return proxy(DataSource.class, target, (method, args, result) -> result instanceof Connection
        ? proxy(Connection.class, (Connection) result, (connectionMethod, connectionArgs, statement) ->
            statement instanceof PreparedStatement && connectionArgs != null && connectionArgs[0] instanceof String
                ? statement((PreparedStatement) statement, (String) connectionArgs[0]) : statement, null)
        : result, null);
  }

  static PreparedStatement statement(PreparedStatement target, String sql) {
    String text = sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    return proxy(PreparedStatement.class, target, (method, args, result) -> result, method -> {
      if (method.startsWith("execute")) {
        EXECUTED.add(target.getQueryTimeout() + " " + text);
        String failing = timeOutOn;
        if (failing != null && text.contains(failing)) {
          throw new SQLTimeoutException("Query timed out (test)", "HYT00");
        }
      }
    });
  }

  interface AfterCall {
    Object apply(java.lang.reflect.Method method, Object[] args, Object result) throws Exception;
  }

  interface BeforeCall {
    void apply(String method) throws Exception;
  }

  @SuppressWarnings("unchecked")
  static <T> T proxy(Class<T> type, T target, AfterCall after, BeforeCall before) {
    return (T) Proxy.newProxyInstance(ProcessAnalyticsTimeoutTest.class.getClassLoader(), new Class<?>[] { type },
        (proxy, method, args) -> {
          if (before != null) {
            before.apply(method.getName());
          }
          Object result;
          try {
            result = method.invoke(target, args);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
          return after.apply(method, args, result);
        });
  }
}
