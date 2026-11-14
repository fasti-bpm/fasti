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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.client.autoconfigure.ConditionalOnOAuth2ClientRegistrationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches if no {@code spring.security.oauth2.client.registration} properties are defined.
 * It is the inverse of {@link ConditionalOnOAuth2ClientRegistrationProperties}; the condition class backing
 * that annotation is not public since Spring Boot 4, so the check is done here directly.
 */
public class ClientsNotConfiguredCondition extends SpringBootCondition {

  protected static final String REGISTRATION_PREFIX = "spring.security.oauth2.client.registration";

  protected static final Bindable<Map<String, OAuth2ClientProperties.Registration>> REGISTRATIONS =
      Bindable.mapOf(String.class, OAuth2ClientProperties.Registration.class);

  @Override
  public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
    ConditionMessage.Builder message = ConditionMessage.forCondition("OAuth2 Clients Not Configured Condition");
    Map<String, OAuth2ClientProperties.Registration> registrations = Binder.get(context.getEnvironment())
        .bind(REGISTRATION_PREFIX, REGISTRATIONS)
        .orElse(Collections.emptyMap());
    if (registrations.isEmpty()) {
      return ConditionOutcome.match(message.notAvailable("registered clients"));
    }
    return ConditionOutcome.noMatch(message.foundExactly("registered clients " + String.join(", ", registrations.keySet())));
  }
}
