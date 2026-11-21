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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;

/**
 * Tells whether the transaction of a command context ends with it, which decides when its audit
 * records are written (see {@link AuditSession}).
 *
 * <p>The engine {@linkplain #knowsBoundaries knows} where its transactions end when it manages
 * them itself (a standalone engine) or when a probe ({@code transactionActive}, for example
 * whether Spring has an actual transaction on the thread) tells whether a command joins a
 * transaction. Then:</p>
 * <ul>
 * <li>a "requires new" command: always (the engine begins a transaction for it and ends it);</li>
 * <li>with a probe: if no transaction was active when the command was called, before the engine
 * began one. The {@linkplain #probeInterceptor() probe interceptor} asks, as a custom "pre"
 * interceptor that runs before the transaction interceptor of the engine;</li>
 * <li>a standalone engine: always.</li>
 * </ul>
 * <p>Otherwise (transactions managed outside the engine, such as JTA, and no probe) the answer is
 * always no: every command writes its records first, on the usual chains, as up to 1.1. Writing
 * some records last and others first on the same chains could let two transactions wait for each
 * other.</p>
 */
public class AuditTransactionScope {

  protected final BooleanSupplier transactionActive;

  /** For each command running on the thread that passed the probe interceptor: whether a transaction was active. */
  protected final ThreadLocal<Deque<Boolean>> joined = ThreadLocal.withInitial(ArrayDeque::new);

  /** @param transactionActive whether a transaction is active on the calling thread; {@code null} if unknown */
  public AuditTransactionScope(BooleanSupplier transactionActive) {
    this.transactionActive = transactionActive;
  }

  public boolean hasProbe() {
    return transactionActive != null;
  }

  /** Whether the engine can tell which of its command contexts end their transaction (see the class comment). */
  public boolean knowsBoundaries(ProcessEngineConfigurationImpl configuration) {
    return transactionActive != null || (configuration != null && !configuration.isTransactionsExternallyManaged());
  }

  /** Whether the transaction of the command context is known to end when it closes. */
  public boolean endsWith(CommandContext commandContext, boolean requiresNew) {
    if (!knowsBoundaries(commandContext.getProcessEngineConfiguration())) {
      return false;
    }
    if (requiresNew || transactionActive == null) {
      return true;
    }
    Boolean wasActive = joined.get().peek();
    if (wasActive == null) {
      // the command did not pass the probe interceptor
      joined.remove();
      return false;
    }
    return !wasActive;
  }

  /** The interceptor that asks the probe before the engine begins or joins a transaction for a command. */
  public CommandInterceptor probeInterceptor() {
    return new CommandInterceptor() {
      @Override
      public <T> T execute(Command<T> command) {
        Deque<Boolean> stack = joined.get();
        stack.push(transactionActive.getAsBoolean());
        try {
          return next.execute(command);
        } finally {
          stack.pop();
          if (stack.isEmpty()) {
            joined.remove();
          }
        }
      }
    };
  }
}
