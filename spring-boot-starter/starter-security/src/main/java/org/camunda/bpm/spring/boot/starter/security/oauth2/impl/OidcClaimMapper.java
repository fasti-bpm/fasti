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

import org.camunda.bpm.oidc.ClaimRules;
import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * The {@link ClaimRules} of the {@code camunda.bpm.oauth2.*} and
 * {@code fasti.security.oidc.*} properties; the same rules apply on Tomcat
 * and WildFly, configured there through a properties file.
 * <p>
 * A user never becomes an administrator of the engine just because a claim
 * happens to carry the value {@code camunda-admin}: groups of an identity
 * provider are often managed by people who must not become administrators of
 * the engine.
 */
public class OidcClaimMapper extends ClaimRules {

  private static volatile OidcClaimMapper current = new OidcClaimMapper(new OAuth2Properties(), new FastiOidcProperties());

  public OidcClaimMapper(OAuth2Properties oAuth2Properties, FastiOidcProperties properties) {
    super(ClaimRules.builder()
        .groupsClaim(oAuth2Properties.getIdentityProvider().getGroupNameAttribute())
        .groupsDelimiter(oAuth2Properties.getIdentityProvider().getGroupNameDelimiter())
        .restGroupsClaim(properties.getRest().getGroupsClaim())
        .admin(properties.getGroups().getAdmin())
        .mapping(properties.getGroups().getMapping())
        .includeUnmapped(properties.getGroups().isIncludeUnmapped())
        .tenantsClaim(properties.getTenants().getClaim())
        .firstNameClaim(properties.getUser().getFirstNameClaim())
        .lastNameClaim(properties.getUser().getLastNameClaim())
        .emailClaim(properties.getUser().getEmailClaim()));
  }

  /**
   * The mapper used by components that the engine or the web applications
   * instantiate by class name, such as authentication providers.
   */
  public static OidcClaimMapper current() {
    return current;
  }

  public static void install(OidcClaimMapper mapper) {
    current = mapper;
  }

  /**
   * Installs the claim mapper of an application context once all singletons
   * exist, whichever configuration (or the application itself) defined it.
   */
  public static SmartInitializingSingleton installer(ObjectProvider<OidcClaimMapper> mappers) {
    return () -> {
      OidcClaimMapper mapper = mappers.getIfUnique();
      if (mapper != null) {
        install(mapper);
      }
    };
  }
}
