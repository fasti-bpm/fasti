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
package dev.fasti.spring.boot.mcp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BasicMcpAuthenticatorTest {

  ProcessEngine engine;
  IdentityService identityService;

  @BeforeEach
  void setUp() {
    engine = mock(ProcessEngine.class, RETURNS_DEEP_STUBS);
    when(engine.getName()).thenReturn("default");
    identityService = engine.getIdentityService();
    // a case-insensitive identity provider: "JOHN" is the user "john"
    when(identityService.checkPassword(anyString(), anyString()))
        .thenAnswer(call -> "john".equalsIgnoreCase(call.getArgument(0)) && "secret".equals(call.getArgument(1)));
    User john = mock(User.class);
    when(john.getId()).thenReturn("john");
    when(identityService.createUserQuery().userId(anyString()).singleResult()).thenReturn(john);
    Group staff = mock(Group.class);
    when(staff.getId()).thenReturn("staff");
    when(identityService.createGroupQuery().groupMember("john").list()).thenReturn(List.of(staff));
    when(identityService.createTenantQuery().userMember("john").includingGroupsOfUser(true).list())
        .thenReturn(List.<Tenant>of());
  }

  static String basic(String user, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void actsAsTheUserTheIdentityServiceKnowsWhateverCaseWasTyped() {
    McpIdentity identity = new BasicMcpAuthenticator().authenticate(basic("JOHN", "secret"), engine);

    assertThat(identity.userId()).isEqualTo("john");
    assertThat(identity.groups()).containsExactly("staff");
  }

  @Test
  void checksEveryRequestWithoutCache() {
    BasicMcpAuthenticator authenticator = new BasicMcpAuthenticator();

    authenticator.authenticate(basic("john", "secret"), engine);
    authenticator.authenticate(basic("john", "secret"), engine);

    verify(identityService, times(2)).checkPassword("john", "secret");
  }

  @Test
  void remembersSuccessfulChecks() {
    BasicMcpAuthenticator authenticator = new BasicMcpAuthenticator(Duration.ofSeconds(30));

    McpIdentity first = authenticator.authenticate(basic("john", "secret"), engine);
    McpIdentity second = authenticator.authenticate(basic("john", "secret"), engine);

    assertThat(second).isEqualTo(first);
    verify(identityService, times(1)).checkPassword("john", "secret");
    verify(identityService.createGroupQuery().groupMember("john"), times(1)).list();
  }

  @Test
  void keepsNoPasswordAndNoFailure() {
    BasicMcpAuthenticator authenticator = new BasicMcpAuthenticator(Duration.ofSeconds(30));
    authenticator.authenticate(basic("john", "secret"), engine);

    assertThat(authenticator.authenticate(basic("john", "wrong"), engine)).isNull();
    assertThat(authenticator.authenticate(basic("john", "wrong"), engine)).isNull();

    verify(identityService, times(2)).checkPassword("john", "wrong");
    assertThat(authenticator.cache.keySet()).hasSize(1)
        .allSatisfy(key -> assertThat(key).doesNotContain("secret").doesNotContain("john").hasSize(64));
  }

  @Test
  void checksAgainAfterTheCacheWasCleared() {
    BasicMcpAuthenticator authenticator = new BasicMcpAuthenticator(Duration.ofSeconds(30));
    authenticator.authenticate(basic("john", "secret"), engine);

    authenticator.clearCache();
    authenticator.authenticate(basic("john", "secret"), engine);

    verify(identityService, times(2)).checkPassword("john", "secret");
  }

  @Test
  void doesNotKeepACheckThatRanWhileIdentitiesChanged() {
    BasicMcpAuthenticator authenticator = new BasicMcpAuthenticator(Duration.ofSeconds(30));
    when(identityService.checkPassword("john", "secret")).thenAnswer(call -> {
      // a membership change commits while this check runs
      authenticator.clearCache();
      return true;
    });

    assertThat(authenticator.authenticate(basic("john", "secret"), engine)).isNotNull();

    assertThat(authenticator.cache).isEmpty();
  }

  @Test
  void checksAgainAfterTheTimeToLive() {
    BasicMcpAuthenticator authenticator = new BasicMcpAuthenticator(Duration.ofNanos(1));
    authenticator.authenticate(basic("john", "secret"), engine);

    authenticator.authenticate(basic("john", "secret"), engine);

    verify(identityService, times(2)).checkPassword("john", "secret");
  }

  @Test
  void refusesNegativeTimeToLive() {
    assertThatThrownBy(() -> new BasicMcpAuthenticator(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
