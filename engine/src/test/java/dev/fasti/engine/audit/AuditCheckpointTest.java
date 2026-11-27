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

import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.impl.AuditCheckpointer;
import dev.fasti.engine.audit.impl.AuditServiceImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.After;
import org.junit.Test;

/**
 * A chain that has not reached the checkpoint interval carries no signature, so it is not
 * authenticated; the engine signs the ends of the chains of its node when it closes and every
 * {@code checkpointIntervalSeconds}.
 */
public class AuditCheckpointTest {

  static final String KEY = "a-checkpoint-test-key-that-is-long-enough-0123";
  static int engines;

  ProcessEngine engine;
  ProcessEngine other;
  String name;

  ProcessEngine build(String name, Consumer<AuditTrailPlugin> more) {
    return AuditTestSupport.engine(name, plugin -> {
      plugin.setChains(1);
      plugin.setHmacKey(KEY);
      plugin.setCheckpointIntervalSeconds(0);
      more.accept(plugin);
    });
  }

  void start(Consumer<AuditTrailPlugin> more) {
    name = "checkpoint" + (++engines);
    engine = build(name, more);
  }

  void restart(Consumer<AuditTrailPlugin> more) {
    engine.close();
    engine = build(name, more);
  }

  void work(ProcessEngine engine, String key, int instances) {
    engine.getRepositoryService().createDeployment().addModelInstance(key + ".bpmn", userTaskProcess(key)).deploy();
    for (int i = 0; i < instances; i++) {
      engine.getRuntimeService().startProcessInstanceByKey(key, "case-" + i);
    }
  }

  @After
  public void closeEngines() {
    if (other != null) {
      other.close();
    }
    AuditTestSupport.cleanUp(engine, true);
  }

  static List<AuditEvent> chain(ProcessEngine engine, String chainId) {
    return AuditTrail.of(engine).createQuery().chainId(chainId).list().stream()
        .sorted(Comparator.comparingLong(AuditEvent::getSequence)).collect(Collectors.toList());
  }

  static long checkpoints(ProcessEngine engine, String chainId) {
    return AuditTrail.of(engine).createQuery().chainId(chainId).types(AuditService.TYPE_AUDIT)
        .action(AuditService.ACTION_CHECKPOINT).count();
  }

  static AuditEvent last(ProcessEngine engine, String chainId) {
    List<AuditEvent> events = chain(engine, chainId);
    return events.get(events.size() - 1);
  }

  static boolean isCheckpoint(AuditEvent event) {
    return AuditService.TYPE_AUDIT.equals(event.getType()) && AuditService.ACTION_CHECKPOINT.equals(event.getAction());
  }

  String chainId() {
    return "node-" + name + "/end-0";
  }

  @Test
  public void shouldNotCallASignedChainWithoutCheckpointAuthenticated() {
    start(plugin -> plugin.setCheckpointInterval(1000));
    work(engine, "short", 3);

    AuditVerification verification = AuditTrail.of(engine).verify(chainId());
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.getRecords()).isPositive();
    assertThat(chain.getCheckpoints()).isZero();
    assertThat(verification.isIntact()).isTrue();
    // no signature covers any of its records: someone who can write to the database could have
    // rewritten all of them
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(chain.isAuthenticated()).isFalse();
    assertThat(chain.getAuthenticatedThrough()).isZero();
  }

  @Test
  public void shouldStillCallAnEmptySignedChainAuthenticated() {
    start(plugin -> plugin.setCheckpointInterval(1000));
    AuditVerification verification = AuditTrail.of(engine).verify(chainId());
    assertThat(verification.getChains().get(0).getRecords()).isZero();
    assertThat(verification.isAuthenticated()).isTrue();
  }

  @Test
  public void shouldSignTheEndsOfTheChainsWhenTheEngineCloses() {
    start(plugin -> plugin.setCheckpointInterval(1000));
    work(engine, "closing", 3);
    AuditEvent lastRecord = last(engine, chainId());
    assertThat(isCheckpoint(lastRecord)).isFalse();

    restart(plugin -> plugin.setCheckpointInterval(1000));
    AuditEvent checkpoint = last(engine, chainId());
    assertThat(isCheckpoint(checkpoint)).isTrue();
    assertThat(checkpoint.getSequence()).isEqualTo(lastRecord.getSequence() + 1);
    assertThat(checkpoint.getDetails()).contains("\"hmac\":\"").contains("\"signedSequence\":" + lastRecord.getSequence());

    AuditVerification verification = AuditTrail.of(engine).verify(chainId());
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(verification.isAuthenticated()).isTrue();
    assertThat(chain.getCheckpointsVerified()).isEqualTo(1);
    assertThat(chain.getAuthenticatedThrough()).isEqualTo(lastRecord.getSequence());
  }

  @Test
  public void shouldNotSignAnEndThatIsACheckpointAlready() {
    start(plugin -> plugin.setCheckpointInterval(1000));
    work(engine, "once", 2);
    restart(plugin -> plugin.setCheckpointInterval(1000));
    long signed = checkpoints(engine, chainId());
    assertThat(signed).isEqualTo(1);
    // nothing written since: closing again adds no checkpoint
    restart(plugin -> plugin.setCheckpointInterval(1000));
    restart(plugin -> plugin.setCheckpointInterval(1000));
    assertThat(checkpoints(engine, chainId())).isEqualTo(signed);
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldKeepTheIntervalOfCheckpointsAfterEarlyOnes() {
    // checkpoints written when the engine closes come early; the next ones keep their positions
    start(plugin -> plugin.setCheckpointInterval(4));
    work(engine, "interval", 1);
    restart(plugin -> plugin.setCheckpointInterval(4));
    for (int i = 0; i < 3; i++) {
      work(engine, "interval" + i, 2);
      restart(plugin -> plugin.setCheckpointInterval(4));
    }
    AuditVerification verification = AuditTrail.of(engine).verify();
    assertThat(verification.getFirstBreak()).isNull();
    assertThat(verification.isAuthenticated()).isTrue();
    assertThat(verification.getChains().get(0).getCheckpoints()).isGreaterThan(4);
  }

  @Test
  public void shouldNotSignWithoutAKey() {
    start(plugin -> {
      plugin.setHmacKey(null);
      plugin.setCheckpointInterval(1000);
    });
    work(engine, "unsigned", 2);
    restart(plugin -> {
      plugin.setHmacKey(null);
      plugin.setCheckpointInterval(1000);
    });
    assertThat(checkpoints(engine, chainId())).isZero();
  }

  @Test
  public void shouldNotSignWithoutCheckpoints() {
    start(plugin -> plugin.setCheckpointInterval(0));
    work(engine, "none", 2);
    restart(plugin -> plugin.setCheckpointInterval(0));
    assertThat(checkpoints(engine, chainId())).isZero();
    assertThat(AuditTrail.of(engine).verify().isAuthenticated()).isFalse();
  }

  @Test
  public void shouldSignOnlyTheChainsOfItsOwnNode() {
    start(plugin -> plugin.setCheckpointInterval(1000));
    work(engine, "mine", 2);
    // another node on the same database
    other = AuditTestSupport.engine(name, plugin -> {
      plugin.setNodeId("node-other-" + name);
      plugin.setChains(1);
      plugin.setHmacKey(KEY);
      plugin.setCheckpointIntervalSeconds(0);
    }, configuration -> configuration.setProcessEngineName("audit-other-" + name));
    String otherChain = "node-other-" + name + "/end-0";
    work(other, "theirs", 2);

    other.close();
    other = null;
    assertThat(isCheckpoint(last(engine, otherChain))).isTrue();
    assertThat(isCheckpoint(last(engine, chainId()))).isFalse();
  }

  @Test
  public void shouldSignTheEndsOfTheChainsEverySoManySeconds() throws InterruptedException {
    start(plugin -> {
      plugin.setCheckpointInterval(1000);
      plugin.setCheckpointIntervalSeconds(1);
    });
    work(engine, "timed", 2);
    long deadline = System.currentTimeMillis() + 30_000;
    while (!isCheckpoint(last(engine, chainId())) && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    AuditEvent checkpoint = last(engine, chainId());
    assertThat(isCheckpoint(checkpoint)).isTrue();
    // nothing written since: no further checkpoint while the chain does not move
    Thread.sleep(2_500);
    assertThat(last(engine, chainId()).getSequence()).isEqualTo(checkpoint.getSequence());
    assertThat(checkpoints(engine, chainId())).isEqualTo(1);
  }

  // chains that another node left behind

  /** A chain without checkpoint left behind by a node that is gone: written without checkpoints. */
  String orphan() {
    other = AuditTestSupport.engine(name, plugin -> {
      plugin.setNodeId("node-gone-" + name);
      plugin.setChains(1);
      plugin.setHmacKey(KEY);
      plugin.setCheckpointInterval(0);
      plugin.setCheckpointIntervalSeconds(0);
    }, configuration -> configuration.setProcessEngineName("audit-gone-" + name));
    work(other, "left", 2);
    other.close();
    other = null;
    return "node-gone-" + name + "/end-0";
  }

  AuditCheckpointer checkpointer(ProcessEngine engine) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    return new AuditCheckpointer(AuditServiceImpl.settingsOf(configuration), configuration.getCommandExecutorTxRequiresNew());
  }

  @Test
  public void shouldNeverSignAChainThatAnotherNodeLeftBehind() {
    start(plugin -> plugin.setCheckpointInterval(1000));
    String orphan = orphan();
    work(engine, "active", 1);
    AuditCheckpointer checkpointer = checkpointer(engine);
    try {
      // however long it has been idle: this node cannot tell it from records written into the database
      ClockUtil.setCurrentTime(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)));
      assertThat(checkpointer.sign()).isEqualTo(1);
      assertThat(checkpoints(engine, orphan)).isZero();
    } finally {
      ClockUtil.reset();
    }
    restart(plugin -> plugin.setCheckpointInterval(1000));
    assertThat(checkpoints(engine, orphan)).isZero();
    AuditVerification left = AuditTrail.of(engine).verify(orphan);
    assertThat(left.getFirstBreak()).isNull();
    assertThat(left.isAuthenticated()).isFalse();
    // the chains of this node are signed
    assertThat(AuditTrail.of(engine).verify(chainId()).isAuthenticated()).isTrue();
  }
}
