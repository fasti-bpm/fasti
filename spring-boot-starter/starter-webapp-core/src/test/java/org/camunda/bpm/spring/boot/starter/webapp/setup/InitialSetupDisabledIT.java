/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.spring.boot.starter.webapp.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.spring.boot.starter.webapp.filter.util.FilterTestApp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { FilterTestApp.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
  "camunda.bpm.webapp.initial-setup-enabled=false"
})
@DirtiesContext
public class InitialSetupDisabledIT extends AbstractInitialSetupIT {

  @Test
  public void shouldNotOfferInitialSetup() {
    // given no administrator is defined
    assertThat(identityService.createUserQuery().count()).isZero();

    // then users are not sent to the setup page
    String redirect = requestTasklist();
    assertThat(redirect == null || !redirect.contains("setup")).isTrue();

    // and the setup page is not served
    assertThat(requestSetupPage()).endsWith("/camunda/app/admin/default/");
  }

  @Test
  public void shouldRefuseToCreateInitialUser() throws Exception {
    // given no administrator is defined
    assertThat(identityService.createUserQuery().count()).isZero();

    // when
    int status = createInitialUser();

    // then
    assertThat(status).isEqualTo(403);
    assertThat(httpClientRule.getErrorResponseContent()).contains("Setup action not available");
    assertThat(identityService.createUserQuery().count()).isZero();
  }

}
