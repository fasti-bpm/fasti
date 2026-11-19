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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class OidcClaimMapperInstallerTest {

  static final OidcClaimMapper APPLICATION_MAPPER = new OidcClaimMapper(new OAuth2Properties(), new FastiOidcProperties());

  private OidcClaimMapper previous;

  @Before
  public void remember() {
    previous = OidcClaimMapper.current();
  }

  @After
  public void restore() {
    OidcClaimMapper.install(previous);
  }

  @Configuration
  static class ApplicationMapper {
    @Bean
    OidcClaimMapper customClaimMapper() {
      return APPLICATION_MAPPER;
    }

    @Bean
    static SmartInitializingSingleton installer(ObjectProvider<OidcClaimMapper> mappers) {
      return OidcClaimMapper.installer(mappers);
    }
  }

  @Test
  public void installsTheMapperOfTheApplication() {
    new ApplicationContextRunner().withUserConfiguration(ApplicationMapper.class)
        .run(context -> assertThat(OidcClaimMapper.current()).isSameAs(APPLICATION_MAPPER));
  }
}
