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
package org.camunda.bpm.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.camunda.bpm.oidc.web.SsoRequests;
import org.junit.Test;

public class OidcSettingsTest {

  static OidcSettings settings(String... pairs) {
    Properties p = new Properties();
    for (int i = 0; i < pairs.length; i += 2) {
      p.setProperty("fasti.security.oidc." + pairs[i], pairs[i + 1]);
    }
    return OidcSettings.of(p, name -> "SECRET".equals(name) ? "from-env" : null);
  }

  @Test
  public void resolvesPlaceholdersListsAndMaps() {
    OidcSettings s = settings("client-secret", "${SECRET}", "scope", "${MISSING:openid email}",
        "groups.admin", "a, b", "groups.admin[0]", "c", "groups.mapping.x-y", "sales");
    assertThat(s.get("client-secret")).isEqualTo("from-env");
    assertThat(s.get("scope")).isEqualTo("openid email");
    assertThat(s.getList("groups.admin")).containsExactly("a", "b", "c");
    assertThat(s.getMap("groups.mapping")).isEqualTo(Map.of("x-y", "sales"));
    assertThatThrownBy(() -> settings("client-secret", "${MISSING}").get("client-secret")).hasMessageContaining("MISSING");
    assertThatThrownBy(() -> settings("userinfo", "yes").getBoolean("userinfo", true)).hasMessageContaining("true or false");
  }

  @Test
  public void loginNeedsAClientAndBearerAnExplicitSwitch() {
    assertThat(settings().isLoginEnabled()).isFalse();
    assertThat(settings("client-id", "c").isLoginEnabled()).isTrue();
    assertThat(settings("client-id", "c", "login.enabled", "false").isLoginEnabled()).isFalse();
    assertThat(settings().isRestBearerEnabled()).isFalse();
    assertThat(settings("rest.bearer-enabled", "true").isRestBearerEnabled()).isTrue();
  }

  @Test
  public void loadsAnExplicitFileAndFailsForAMissingOne() throws Exception {
    Path file = Files.createTempFile("fasti-oidc", ".properties");
    Files.writeString(file, "fasti.security.oidc.client-id=web\n");
    try {
      assertThat(OidcSettings.load(file.toString()).isLoginEnabled()).isTrue();
    } finally {
      Files.delete(file);
    }
    assertThatThrownBy(() -> OidcSettings.load(file + ".missing")).hasMessageContaining("not found");
  }

  @Test
  public void claimRulesGrantAdministratorsOnlyExplicitly() {
    ClaimRules rules = settings("groups-claim", "realm_access.roles", "groups.admin", "ops",
        "groups.mapping.idp-sales", "sales", "groups.include-unmapped", "false", "tenants.claim", "tenants").claimRules();
    Map<String, Object> claims = Map.of("realm_access", Map.of("roles", List.of("camunda-admin", "ops", "idp-sales", "x")),
        "tenants", "t1,t2");
    assertThat(rules.groups(claims, rules.getGroupsClaim())).containsExactly("camunda-admin", "sales");
    assertThat(rules.tenants(claims)).containsExactly("t1", "t2");
    ClaimRules none = settings().claimRules();
    assertThat(none.mapsGroups(none.getGroupsClaim())).isFalse();
    assertThat(none.groups(Map.of("groups", List.of("camunda-admin")), "groups")).isEmpty();
  }

  @Test
  public void returnUrlRules() {
    String prefix = "/fasti/app/next/";
    assertThat(SsoRequests.isAllowedReturnUrl(prefix, "/fasti/app/next/default/#/tasklist?x=%C3%A9")).isTrue();
    for (String url : new String[] { null, "", "https://evil/fasti/app/next/", "//evil", "/fasti/app/next//evil",
        "/fasti/app/next/../admin", "/fasti/app/next/%2e%2e/", "/fasti/app/next/\\x", "/fasti/app/next/a b",
        "/fasti/app/next/x#y", "/fasti/app/next/x#//evil", "/fasti/app/next/x#/a#b", "/fasti/app/cockpit/" }) {
      assertThat(SsoRequests.isAllowedReturnUrl(prefix, url)).as(String.valueOf(url)).isFalse();
    }
  }
}
