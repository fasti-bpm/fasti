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

import static dev.fasti.engine.audit.AuditTestSupport.sql;
import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.impl.AuditServiceImpl;
import dev.fasti.engine.audit.impl.AuditSettings;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Reading the trail: pages are cut in the database, exports read one cursor, readers see the
 * tenants they belong to, one verification runs at a time, and database errors stay in the log.
 */
public class AuditQueryTest {

  static final int INSTANCES = 30;

  static ProcessEngine engine;
  static AuditService audit;

  @BeforeClass
  public static void buildEngine() {
    engine = AuditTestSupport.engine("query", plugin -> {
      plugin.setChains(2);
      plugin.setCheckpointInterval(10);
      plugin.setRetentionDays(0);
    });
    audit = AuditTrail.of(engine);
    engine.getRepositoryService().createDeployment().addModelInstance("q.bpmn", userTaskProcess("q")).deploy();
    for (String tenant : List.of("t1", "t2")) {
      engine.getRepositoryService().createDeployment().tenantId(tenant).addModelInstance("q.bpmn", userTaskProcess("q")).deploy();
    }
    for (int i = 0; i < INSTANCES; i++) {
      engine.getRuntimeService().createProcessInstanceByKey("q").processDefinitionWithoutTenantId().businessKey("shared-" + i)
          .execute();
    }
    for (String tenant : List.of("t1", "t2")) {
      for (int i = 0; i < 3; i++) {
        engine.getRuntimeService().createProcessInstanceByKey("q").processDefinitionTenantId(tenant)
            .businessKey(tenant + "-" + i).execute();
      }
    }
  }

  @AfterClass
  public static void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  @After
  public void clearAuthentication() {
    engine.getIdentityService().clearAuthentication();
  }

  static List<String> ids(List<AuditEvent> events) {
    return events.stream().map(AuditEvent::getId).collect(Collectors.toList());
  }

  // paging ///////////////////////////////////////////////////////////////////////////////////

  @Test
  public void shouldListNewestFirstInTheReverseOrderOfOldestFirst() {
    // the same order in both directions, also for records of the same millisecond in several chains
    List<String> oldestFirst = ids(audit.createQuery().oldestFirst().list());
    List<String> newestFirst = new ArrayList<>(ids(audit.createQuery().list()));
    java.util.Collections.reverse(newestFirst);
    assertThat(newestFirst).isEqualTo(oldestFirst);
    List<AuditEvent> events = audit.createQuery().list();
    for (int i = 1; i < events.size(); i++) {
      assertThat(events.get(i).getTimestamp()).isBeforeOrEqualTo(events.get(i - 1).getTimestamp());
    }
  }

  @Test
  public void shouldStopCountingAtALimit() {
    long all = audit.createQuery().count();
    assertThat(all).isGreaterThan(2 * INSTANCES);
    assertThat(audit.createQuery().count(10)).isEqualTo(10);
    assertThat(audit.createQuery().count(all)).isEqualTo(all);
    assertThat(audit.createQuery().count(all + 100)).isEqualTo(all);
    assertThat(audit.createQuery().count(0)).isEqualTo(all);
    long starts = audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count();
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count(5)).isEqualTo(5);
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count(1000)).isEqualTo(starts);
  }

  @Test
  public void shouldCutPagesInTheDatabase() {
    List<String> all = ids(audit.createQuery().oldestFirst().list());
    assertThat(all.size()).isGreaterThan(2 * INSTANCES);
    List<String> paged = new ArrayList<>();
    for (int first = 0; first < all.size(); first += 7) {
      List<AuditEvent> page = audit.createQuery().oldestFirst().listPage(first, 7);
      assertThat(page.size()).isEqualTo(Math.min(7, all.size() - first));
      paged.addAll(ids(page));
    }
    assertThat(paged).containsExactlyElementsOf(all);

    List<String> newestFirst = ids(audit.createQuery().list());
    assertThat(ids(audit.createQuery().listPage(3, 4))).containsExactlyElementsOf(newestFirst.subList(3, 7));
    assertThat(audit.createQuery().listPage(all.size() - 1, 50)).hasSize(1);
    assertThat(audit.createQuery().listPage(all.size(), 50)).isEmpty();
    assertThat(audit.createQuery().listPage(0, 0)).isEmpty();
  }

  @Test
  public void shouldNotReadTheRowsBeforeAFarPage() {
    // the offset is applied by the database: nothing is fetched, however large it is
    long start = System.nanoTime();
    assertThat(audit.createQuery().listPage(2_000_000_000, 1000)).isEmpty();
    assertThat(audit.createQuery().businessKey("shared-1").listPage(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEmpty();
    assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)).isLessThan(20);
  }

  @Test
  public void shouldPageWithFilters() {
    List<String> starts = ids(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").oldestFirst().list());
    assertThat(starts).hasSize(INSTANCES + 6);
    assertThat(ids(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").oldestFirst().listPage(10, 5)))
        .containsExactlyElementsOf(starts.subList(10, 15));
  }

  // export ///////////////////////////////////////////////////////////////////////////////////

  @Test
  public void shouldStreamRecordsFromOneCursor() {
    List<String> all = ids(audit.createQuery().oldestFirst().list());
    List<String> streamed = new ArrayList<>();
    long count = audit.createQuery().oldestFirst().forEach(Integer.MAX_VALUE, event -> streamed.add(event.getId()));
    assertThat(count).isEqualTo(all.size());
    assertThat(streamed).containsExactlyElementsOf(all);

    List<String> some = new ArrayList<>();
    assertThat(audit.createQuery().oldestFirst().forEach(5, event -> some.add(event.getId()))).isEqualTo(5);
    assertThat(some).containsExactlyElementsOf(all.subList(0, 5));
    assertThat(audit.createQuery().forEach(0, event -> some.add(event.getId()))).isZero();
    assertThat(some).hasSize(5);
  }

  @Test
  public void shouldStreamDetailsAsStored() {
    AuditEvent listed = audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").businessKey("t1-0").list().get(0);
    List<AuditEvent> streamed = new ArrayList<>();
    audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").businessKey("t1-0").forEach(10, streamed::add);
    assertThat(streamed).hasSize(1);
    assertThat(streamed.get(0).getDetails()).isEqualTo(listed.getDetails());
    assertThat(streamed.get(0).getHash()).isEqualTo(listed.getHash());
  }

  // tenants //////////////////////////////////////////////////////////////////////////////////

  @Test
  public void shouldShowReadersOnlyTheirTenantsAndRecordsWithoutTenant() {
    long all = audit.createQuery().count();
    long ofT2 = audit.createQuery().tenantId("t2").count();
    assertThat(ofT2).isPositive();

    engine.getIdentityService().setAuthentication("tina", List.of("staff"), List.of("t1"));
    List<AuditEvent> visible = audit.createQuery().list();
    assertThat(visible).isNotEmpty().allSatisfy(event -> assertThat(event.getTenantId()).isIn(null, "t1"));
    assertThat(visible).anySatisfy(event -> assertThat(event.getTenantId()).isEqualTo("t1"));
    assertThat(audit.createQuery().count()).isEqualTo(visible.size()).isEqualTo(all - ofT2);
    assertThat(audit.createQuery().tenantId("t2").count()).isZero();
    assertThat(audit.createQuery().tenantId("t2").list()).isEmpty();
    assertThat(audit.createQuery().businessKey("t2-0").listPage(0, 10)).isEmpty();
    List<AuditEvent> streamed = new ArrayList<>();
    audit.createQuery().forEach(Integer.MAX_VALUE, streamed::add);
    assertThat(streamed).hasSize(visible.size()).allSatisfy(event -> assertThat(event.getTenantId()).isIn(null, "t1"));
  }

  @Test
  public void shouldShowReadersWithoutTenantOnlyRecordsWithoutTenant() {
    engine.getIdentityService().setAuthentication("nora", List.of("staff"), List.of());
    assertThat(audit.createQuery().list()).isNotEmpty().allSatisfy(event -> assertThat(event.getTenantId()).isNull());
    assertThat(audit.createQuery().tenantId("t1").count()).isZero();
  }

  @Test
  public void shouldShowAdministratorsAndTheEngineItselfEveryTenant() {
    long all = audit.createQuery().count();
    engine.getIdentityService().setAuthentication("root", List.of(Groups.CAMUNDA_ADMIN), List.of("t1"));
    assertThat(audit.createQuery().count()).isEqualTo(all);
    assertThat(audit.createQuery().tenantId("t2").count()).isPositive();
  }

  @Test
  public void shouldShowEveryTenantWhenTheTenantCheckIsOff() {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    engine.getIdentityService().setAuthentication("tina", List.of("staff"), List.of("t1"));
    configuration.setTenantCheckEnabled(false);
    try {
      assertThat(audit.createQuery().tenantId("t2").count()).isPositive();
    } finally {
      configuration.setTenantCheckEnabled(true);
    }
  }

  // verification /////////////////////////////////////////////////////////////////////////////

  @Test
  public void shouldRunOneVerificationAtATime() {
    AuditSettings settings = AuditServiceImpl.settingsOf((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration());
    // as if another request were verifying
    assertThat(settings.startVerifying()).isTrue();
    try {
      long before = audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_VERIFY).count();
      assertThatThrownBy(audit::verify).isInstanceOf(AuditVerificationRunningException.class);
      assertThatThrownBy(() -> audit.verify(settings.chainId(0))).isInstanceOf(AuditVerificationRunningException.class);
      // a refused verification reads nothing and records nothing
      assertThat(audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_VERIFY).count()).isEqualTo(before);
    } finally {
      settings.endVerifying();
    }
    assertThat(audit.verify().isIntact()).isTrue();
    // and it lets go when it is done, also after a failure
    assertThatThrownBy(() -> audit.verify("no-such-chain")).isInstanceOf(ProcessEngineException.class);
    assertThat(audit.verify().isIntact()).isTrue();
  }

  @Test
  public void shouldRefuseVerificationsThatArriveWhileOneRuns() throws Exception {
    int threads = 6;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(executor.submit((Callable<String>) () -> {
          go.await();
          try {
            return audit.verify().isIntact() ? "intact" : "broken";
          } catch (AuditVerificationRunningException e) {
            return "busy";
          }
        }));
      }
      go.countDown();
      List<String> outcomes = new ArrayList<>();
      for (Future<String> future : futures) {
        outcomes.add(future.get(2, TimeUnit.MINUTES));
      }
      assertThat(outcomes).contains("intact").containsOnly("intact", "busy");
    } finally {
      executor.shutdownNow();
    }
    assertThat(audit.verify().isIntact()).isTrue();
  }

  // errors ///////////////////////////////////////////////////////////////////////////////////

  @Test
  public void shouldNotTellWhatTheDatabaseSaid() {
    sql(engine, "alter table FASTI_AUDIT_EVENT rename to FASTI_AUDIT_GONE");
    try {
      assertThatThrownBy(() -> audit.createQuery().count()).isInstanceOf(ProcessEngineException.class)
          .hasMessageContaining("could not count records")
          .satisfies(e -> assertThat(e.getMessage().toUpperCase()).doesNotContain("FASTI_AUDIT", "SELECT", "TABLE"));
      assertThatThrownBy(() -> audit.createQuery().listPage(0, 10)).isInstanceOf(ProcessEngineException.class)
          .hasMessageContaining("could not read records")
          .satisfies(e -> assertThat(e.getMessage().toUpperCase()).doesNotContain("FASTI_AUDIT", "SELECT", "TABLE"));
      // a command that must write a record fails, without the text of the driver
      assertThatThrownBy(() -> engine.getRuntimeService().createProcessInstanceByKey("q").processDefinitionWithoutTenantId().execute())
          .isInstanceOf(ProcessEngineException.class).hasMessageContaining("could not write its records")
          .satisfies(e -> assertThat(e.getMessage().toUpperCase()).doesNotContain("FASTI_AUDIT", "INSERT", "TABLE"));
    } finally {
      sql(engine, "alter table FASTI_AUDIT_GONE rename to FASTI_AUDIT_EVENT");
    }
    assertThat(audit.createQuery().count()).isPositive();
  }
}
