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
package dev.fasti.spring.boot.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.fasti.spring.boot.mcp.auth.BasicMcpAuthenticator;

/**
 * The cache of basic authentication with the default time to live: changes of users, groups and
 * memberships through the engine take effect at once.
 */
@SpringBootTest(classes = McpTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "camunda.bpm.process-engine-name=mcpBasicAuthCache",
        "spring.datasource.url=jdbc:h2:mem:mcpBasicAuthCache;DB_CLOSE_ON_EXIT=FALSE"
    })
class McpBasicAuthCacheTest {

  static final String USER = "dave";
  static final String PASSWORD = "dave-Secret-1";

  @Autowired
  ProcessEngine engine;

  @Autowired
  BasicMcpAuthenticator authenticator;

  IdentityService identityService;

  @BeforeEach
  void setUp() {
    identityService = engine.getIdentityService();
    User user = identityService.newUser(USER);
    user.setPassword(PASSWORD);
    identityService.saveUser(user);
    identityService.saveGroup(identityService.newGroup("auditors"));
  }

  @AfterEach
  void tearDown() {
    identityService.deleteUser(USER);
    identityService.deleteGroup("auditors");
  }

  static String basic(String user, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void isOnByDefault() {
    assertThat(authenticator).extracting("cacheTimeToLiveNanos").isEqualTo(30_000_000_000L);
  }

  @Test
  void forgetsADeletedUser() {
    assertThat(authenticator.authenticate(basic(USER, PASSWORD), engine)).isNotNull();

    identityService.deleteUser(USER);

    assertThat(authenticator.authenticate(basic(USER, PASSWORD), engine)).isNull();
  }

  @Test
  void forgetsTheOldPassword() {
    assertThat(authenticator.authenticate(basic(USER, PASSWORD), engine)).isNotNull();

    User user = identityService.createUserQuery().userId(USER).singleResult();
    user.setPassword("dave-Other-2");
    identityService.saveUser(user);

    // the new password first: a failed login delays the next ones
    assertThat(authenticator.authenticate(basic(USER, "dave-Other-2"), engine)).isNotNull();
    assertThat(authenticator.authenticate(basic(USER, PASSWORD), engine)).isNull();
  }

  @Test
  void seesNewMemberships() {
    assertThat(authenticator.authenticate(basic(USER, PASSWORD), engine).groups()).isEmpty();

    identityService.createMembership(USER, "auditors");

    assertThat(authenticator.authenticate(basic(USER, PASSWORD), engine).groups()).containsExactly("auditors");
  }
}
