/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.test.standalone.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * With the default configuration, failed logins delay the next attempt but
 * never lock a user for good: otherwise anyone who knows the name of the only
 * administrator could lock it out until somebody edits the database.
 */
public class LoginAttemptsDefaultTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.setJdbcUrl("jdbc:h2:mem:LoginAttemptsDefaultTest;DB_CLOSE_DELAY=1000");
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP);
  });

  @Rule
  public ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);

  @After
  public void tearDown() {
    ClockUtil.reset();
    IdentityService identityService = engineRule.getIdentityService();
    for (User user : identityService.createUserQuery().list()) {
      identityService.deleteUser(user.getId());
    }
  }

  @Test
  public void shouldNotLockUserForGoodByDefault() {
    // given
    ProcessEngineConfigurationImpl configuration = engineRule.getProcessEngineConfiguration();
    assertThat(configuration.getLoginMaxAttempts()).isEqualTo(Integer.MAX_VALUE);
    IdentityService identityService = engineRule.getIdentityService();
    User user = identityService.newUser("admin");
    user.setPassword("right-password");
    identityService.saveUser(user);

    Date now = new Date();
    ClockUtil.setCurrentTime(now);

    // when the password is guessed wrong 25 times, each time after the delay
    for (int attempt = 0; attempt < 25; attempt++) {
      assertThat(identityService.checkPassword("admin", "wrong-" + attempt)).isFalse();
      now = new Date(now.getTime() + (configuration.getLoginDelayMaxTime() + 1) * 1000L);
      ClockUtil.setCurrentTime(now);
    }

    // then the right password still works once the delay is over
    assertThat(identityService.checkPassword("admin", "right-password")).isTrue();
  }

  @Test
  public void shouldStillDelayAttemptsAfterFailedLogin() {
    // given
    IdentityService identityService = engineRule.getIdentityService();
    User user = identityService.newUser("bob");
    user.setPassword("right-password");
    identityService.saveUser(user);
    ClockUtil.setCurrentTime(new Date());

    // when
    identityService.checkPassword("bob", "wrong");

    // then the right password is refused while the delay lasts
    assertThat(identityService.checkPassword("bob", "right-password")).isFalse();
  }
}
