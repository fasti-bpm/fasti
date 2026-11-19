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

import java.util.List;
import java.util.Map;

import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.junit.Before;
import org.junit.Test;

public class OidcClaimMapperTest {

  private OAuth2Properties oAuth2Properties;
  private FastiOidcProperties properties;

  @Before
  public void setUp() {
    oAuth2Properties = new OAuth2Properties();
    oAuth2Properties.getIdentityProvider().setGroupNameAttribute("groups");
    properties = new FastiOidcProperties();
  }

  private OidcClaimMapper mapper() {
    return new OidcClaimMapper(oAuth2Properties, properties);
  }

  @Test
  public void mapsListClaimToGroups() {
    var groups = mapper().groups(Map.of("groups", List.of("sales", " accounting ", "", "sales")), "groups");

    assertThat(groups).containsExactly("sales", "accounting");
  }

  @Test
  public void splitsStringClaimLiterally() {
    oAuth2Properties.getIdentityProvider().setGroupNameDelimiter("|");

    var groups = mapper().groups(Map.of("groups", "a|b.c|d"), "groups");

    // the delimiter is not a regular expression
    assertThat(groups).containsExactly("a", "b.c", "d");
  }

  @Test
  public void neverGrantsAdministratorsFromRawClaim() {
    var groups = mapper().groups(Map.of("groups", List.of("camunda-admin", "sales")), "groups");

    assertThat(groups).containsExactly("sales");
  }

  @Test
  public void grantsAdministratorsThroughAdminList() {
    properties.getGroups().setAdmin(List.of("idp-admins"));

    var groups = mapper().groups(Map.of("groups", List.of("idp-admins")), "groups");

    assertThat(groups).containsExactlyInAnyOrder("idp-admins", "camunda-admin");
  }

  @Test
  public void grantsAdministratorsThroughMapping() {
    properties.getGroups().setMapping(Map.of("3f0e-entra-object-id", "camunda-admin"));

    var groups = mapper().groups(Map.of("groups", List.of("3f0e-entra-object-id")), "groups");

    assertThat(groups).containsExactly("camunda-admin");
  }

  @Test
  public void dropsUnmappedValuesWhenConfigured() {
    properties.getGroups().setMapping(Map.of("idp-sales", "sales"));
    properties.getGroups().setIncludeUnmapped(false);

    var groups = mapper().groups(Map.of("groups", List.of("idp-sales", "other")), "groups");

    assertThat(groups).containsExactly("sales");
  }

  @Test
  public void readsNestedClaims() {
    var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of("operator")));

    assertThat(mapper().groups(claims, "realm_access.roles")).containsExactly("operator");
  }

  @Test
  public void ignoresMissingAndUnsupportedClaims() {
    assertThat(mapper().groups(Map.of(), "groups")).isEmpty();
    assertThat(mapper().groups(Map.of("groups", 42), "groups")).isEmpty();
    assertThat(mapper().groups(Map.of("groups", List.of(1, "x")), "groups")).containsExactly("x");
    assertThat(mapper().groups(Map.of("groups", List.of("x")), null)).isEmpty();
  }

  @Test
  public void mapsTenantsOnlyWhenConfigured() {
    Map<String, Object> claims = Map.of("tenants", List.of("t1", "t2"));
    assertThat(mapper().mapsTenants()).isFalse();
    assertThat(mapper().tenants(claims)).isEmpty();

    properties.getTenants().setClaim("tenants");
    assertThat(mapper().tenants(claims)).containsExactly("t1", "t2");
  }

  @Test
  public void readsUserAttributes() {
    properties.getUser().setEmailClaim("upn");
    Map<String, Object> claims = Map.of("given_name", "Lan", "family_name", "Nguyen", "upn", "lan@example.org",
        "email", "ignored@example.org");

    var mapper = mapper();
    assertThat(mapper.firstName(claims)).isEqualTo("Lan");
    assertThat(mapper.lastName(claims)).isEqualTo("Nguyen");
    assertThat(mapper.email(claims)).isEqualTo("lan@example.org");
  }

  @Test
  public void restGroupsClaimFallsBackToLoginClaim() {
    assertThat(mapper().getRestGroupsClaim()).isEqualTo("groups");
    properties.getRest().setGroupsClaim("roles");
    assertThat(mapper().getRestGroupsClaim()).isEqualTo("roles");
  }

  @Test
  public void mapsGroupsOnlyWithASource() {
    oAuth2Properties.getIdentityProvider().setGroupNameAttribute(null);
    assertThat(mapper().mapsGroups(null)).isFalse();
    properties.getGroups().setAdmin(List.of("x"));
    assertThat(mapper().mapsGroups(null)).isTrue();
  }
}
