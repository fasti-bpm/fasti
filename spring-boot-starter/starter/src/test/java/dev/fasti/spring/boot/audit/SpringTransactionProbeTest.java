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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.BooleanSupplier;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;
import org.junit.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Whether a command will join a transaction of the transaction manager of the engine. */
public class SpringTransactionProbeTest {

  @Test
  public void shouldAskAJtaTransactionManagerForTheStatus() throws SystemException {
    // a JTA transaction that Spring did not begin (container-managed, UserTransaction.begin())
    UserTransaction userTransaction = mock(UserTransaction.class);
    BooleanSupplier probe = SpringTransactionProbe.forManager(new JtaTransactionManager(userTransaction));
    when(userTransaction.getStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);
    assertThat(probe.getAsBoolean()).isFalse();
    when(userTransaction.getStatus()).thenReturn(Status.STATUS_ACTIVE);
    assertThat(probe.getAsBoolean()).isTrue();
    when(userTransaction.getStatus()).thenReturn(Status.STATUS_MARKED_ROLLBACK);
    assertThat(probe.getAsBoolean()).isTrue();
    // unknown: joining, the safe assumption
    when(userTransaction.getStatus()).thenThrow(new SystemException("gone"));
    assertThat(probe.getAsBoolean()).isTrue();
  }

  @Test
  public void shouldAskWhetherTheResourceOfTheTransactionManagerIsBound() {
    DriverManagerDataSource engineDataSource = new DriverManagerDataSource("jdbc:h2:mem:fasti-probe-engine", "sa", "");
    DriverManagerDataSource otherDataSource = new DriverManagerDataSource("jdbc:h2:mem:fasti-probe-other", "sa", "");
    BooleanSupplier probe = SpringTransactionProbe.forManager(new DataSourceTransactionManager(engineDataSource));
    assertThat(probe.getAsBoolean()).isFalse();
    Boolean inEngineTransaction = new TransactionTemplate(new DataSourceTransactionManager(engineDataSource))
        .execute(status -> probe.getAsBoolean());
    assertThat(inEngineTransaction).isTrue();
    Boolean inOtherTransaction = new TransactionTemplate(new DataSourceTransactionManager(otherDataSource))
        .execute(status -> probe.getAsBoolean());
    assertThat(inOtherTransaction).isFalse();
  }
}
