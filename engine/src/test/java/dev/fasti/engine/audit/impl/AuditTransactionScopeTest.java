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
package dev.fasti.engine.audit.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.junit.Test;

/** Which command contexts are known to end their transaction, so that their records are written last. */
public class AuditTransactionScopeTest {

  static CommandContext commandContext(boolean externallyManaged) {
    ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
    when(configuration.isTransactionsExternallyManaged()).thenReturn(externallyManaged);
    CommandContext commandContext = mock(CommandContext.class);
    when(commandContext.getProcessEngineConfiguration()).thenReturn(configuration);
    return commandContext;
  }

  /** Runs the check inside the probe interceptor, as the post interceptor of the plugin does. */
  static boolean endsWithInside(AuditTransactionScope scope, CommandContext commandContext, boolean requiresNew) {
    CommandInterceptor probe = scope.probeInterceptor();
    probe.setNext(new CommandInterceptor() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> T execute(Command<T> command) {
        return (T) Boolean.valueOf(scope.endsWith(commandContext, requiresNew));
      }
    });
    return probe.execute(context -> null) == Boolean.TRUE;
  }

  @Test
  public void shouldLetEveryCommandOfAStandaloneEngineWriteLast() {
    AuditTransactionScope scope = new AuditTransactionScope(null);
    CommandContext commandContext = commandContext(false);
    assertThat(scope.knowsBoundaries(commandContext.getProcessEngineConfiguration())).isTrue();
    assertThat(scope.endsWith(commandContext, false)).isTrue();
    assertThat(scope.endsWith(commandContext, true)).isTrue();
  }

  @Test
  public void shouldLetNoCommandWriteLastWithExternalTransactionsAndNoProbe() {
    // JTA without a probe: a command may join a transaction that goes on; "requires new" commands
    // write first as well, so that the records of one chain are all written the same way
    AuditTransactionScope scope = new AuditTransactionScope(null);
    CommandContext commandContext = commandContext(true);
    assertThat(scope.knowsBoundaries(commandContext.getProcessEngineConfiguration())).isFalse();
    assertThat(scope.endsWith(commandContext, false)).isFalse();
    assertThat(scope.endsWith(commandContext, true)).isFalse();
  }

  @Test
  public void shouldAskTheProbeBeforeTheEngineBeginsATransaction() {
    AtomicBoolean active = new AtomicBoolean();
    AuditTransactionScope scope = new AuditTransactionScope(active::get);
    CommandContext commandContext = commandContext(true);
    assertThat(scope.knowsBoundaries(commandContext.getProcessEngineConfiguration())).isTrue();
    // no transaction before the command: the engine begins one and ends it with the command
    assertThat(endsWithInside(scope, commandContext, false)).isTrue();
    // the command joins a transaction that goes on
    active.set(true);
    assertThat(endsWithInside(scope, commandContext, false)).isFalse();
    // a "requires new" command begins its own whatever is active
    assertThat(endsWithInside(scope, commandContext, true)).isTrue();
    // a command that did not pass the probe interceptor: not known, so not last
    assertThat(scope.endsWith(commandContext, false)).isFalse();
    assertThat(scope.joined.get()).isEmpty();
  }

  @Test
  public void shouldAnswerForTheInnermostCommand() {
    AtomicBoolean active = new AtomicBoolean();
    AuditTransactionScope scope = new AuditTransactionScope(active::get);
    CommandContext commandContext = commandContext(true);
    CommandInterceptor outer = scope.probeInterceptor();
    outer.setNext(new CommandInterceptor() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> T execute(Command<T> command) {
        boolean outerEnds = scope.endsWith(commandContext, false);
        // inside the command the transaction of the engine is active
        active.set(true);
        boolean innerEnds = endsWithInside(scope, commandContext, false);
        boolean outerStillEnds = scope.endsWith(commandContext, false);
        return (T) Boolean.valueOf(outerEnds && !innerEnds && outerStillEnds);
      }
    });
    Object result = outer.execute(context -> null);
    assertThat(result).isEqualTo(Boolean.TRUE);
    assertThat(scope.joined.get()).isEmpty();
  }
}
