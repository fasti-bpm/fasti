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
package dev.fasti.spring.boot.audit;

import java.util.function.BooleanSupplier;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.spring.SpringTransactionsProcessEngineConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

/**
 * Tells the audit trail whether a command will join a transaction that goes on after it: whether
 * the transaction manager of the engine has a transaction on the calling thread before the engine
 * asks it for one. Only then does the engine join instead of beginning a transaction of its own
 * (propagation "required").
 *
 * <ul>
 * <li>A manager of one resource (a {@code DataSourceTransactionManager} on the DataSource of the
 * engine, a {@code JpaTransactionManager}): a transaction is active and that resource is bound to
 * the thread. A transaction of another manager (Kafka, JMS, a second DataSource) does not count:
 * the engine begins its own beside it.</li>
 * <li>A {@code JtaTransactionManager}: the status of the JTA transaction, also of one that Spring
 * did not begin (container-managed, {@code UserTransaction.begin()}).</li>
 * <li>Before the engine configuration is known, or with another manager: whether Spring has any
 * transaction on the thread, which treats a command as joining when in doubt.</li>
 * </ul>
 */
public class SpringTransactionProbe implements BooleanSupplier {

  static final boolean JTA_PRESENT = ClassUtils.isPresent("jakarta.transaction.UserTransaction",
      SpringTransactionProbe.class.getClassLoader());

  protected volatile BooleanSupplier delegate = TransactionSynchronizationManager::isActualTransactionActive;

  @Override
  public boolean getAsBoolean() {
    return delegate.getAsBoolean();
  }

  /** Asks the transaction manager of the engine from now on. */
  public void configure(ProcessEngineConfigurationImpl configuration) {
    if (configuration instanceof SpringTransactionsProcessEngineConfiguration) {
      PlatformTransactionManager transactionManager = ((SpringTransactionsProcessEngineConfiguration) configuration)
          .getTransactionManager();
      if (transactionManager != null) {
        delegate = forManager(transactionManager);
      }
    }
  }

  /**
   * Runs the callback when the transaction of the calling thread ends, with any status ("unknown"
   * included, after a commit or rollback that failed), even when an afterCommit callback before it
   * threw. Nothing without transaction synchronization on the thread.
   */
  public static void afterCompletion(Runnable callback) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          callback.run();
        }
      });
    }
  }

  /** The probe for a transaction manager. */
  static BooleanSupplier forManager(PlatformTransactionManager transactionManager) {
    if (JTA_PRESENT && transactionManager instanceof JtaTransactionManager) {
      return Jta.probe((JtaTransactionManager) transactionManager);
    }
    if (transactionManager instanceof ResourceTransactionManager) {
      Object resource = ((ResourceTransactionManager) transactionManager).getResourceFactory();
      if (resource != null) {
        return () -> TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.hasResource(resource);
      }
    }
    return TransactionSynchronizationManager::isActualTransactionActive;
  }

  /** Kept apart, so that the JTA classes are only loaded with a JTA transaction manager. */
  static final class Jta {

    private Jta() {
    }

    static BooleanSupplier probe(JtaTransactionManager transactionManager) {
      return () -> {
        try {
          UserTransaction userTransaction = transactionManager.getUserTransaction();
          if (userTransaction != null) {
            return userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION;
          }
          TransactionManager manager = transactionManager.getTransactionManager();
          if (manager != null) {
            return manager.getStatus() != Status.STATUS_NO_TRANSACTION;
          }
        } catch (SystemException | RuntimeException e) {
          // unknown: joining, which is the safe assumption
          return true;
        }
        return TransactionSynchronizationManager.isActualTransactionActive();
      };
    }
  }
}
