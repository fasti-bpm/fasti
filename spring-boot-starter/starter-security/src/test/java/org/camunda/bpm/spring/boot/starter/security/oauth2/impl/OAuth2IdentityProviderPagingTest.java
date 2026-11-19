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

import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.Page;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class OAuth2IdentityProviderPagingTest {

  @Before
  public void authenticate() {
    List<GrantedAuthority> authorities = List.of(
        new SimpleGrantedAuthority("g1"), new SimpleGrantedAuthority("g2"), new SimpleGrantedAuthority("g3"),
        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.BEARER_AUTHORITY),
        new TenantGrantedAuthority("t1"), new TenantGrantedAuthority("t2"), new TenantGrantedAuthority("t3"));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("alice", null, authorities));
  }

  @After
  public void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void tenantQueryAppliesPage() {
    var query = new OAuth2IdentityProvider.OAuth2TenantQuery();
    assertThat(query.executeList(null, new Page(1, 1)).stream().map(Tenant::getId)).containsExactly("t2");
    assertThat(query.executeList(null, new Page(2, 5)).stream().map(Tenant::getId)).containsExactly("t3");
    assertThat(query.executeList(null, null)).hasSize(3);
    assertThat(query.executeCount(null)).isEqualTo(3);
  }

  @Test
  public void groupQueryAppliesPage() {
    var query = new OAuth2IdentityProvider.OAuth2GroupQuery();
    assertThat(query.executeList(null, new Page(0, 2)).stream().map(Group::getId)).containsExactly("g1", "g2");
    assertThat(query.executeList(null, new Page(3, 2))).isEmpty();
    assertThat(query.executeCount(null)).isEqualTo(3);
  }

  @Test
  public void groupQueryLeavesOutAuthenticationFactors() {
    var query = new OAuth2IdentityProvider.OAuth2GroupQuery();
    assertThat(query.executeList(null, null).stream().map(Group::getId)).containsExactly("g1", "g2", "g3");
  }

  @Test
  public void userQueryAppliesPageAndCountsMatches() {
    var query = new OAuth2IdentityProvider.OAuth2UserQuery();
    assertThat(query.executeList(null, new Page(0, 1)).stream().map(User::getId)).containsExactly("alice");
    assertThat(query.executeList(null, new Page(1, 1))).isEmpty();
    assertThat(query.executeCount(null)).isEqualTo(1);

    var other = new OAuth2IdentityProvider.OAuth2UserQuery();
    other.userId("bob");
    assertThat(other.executeCount(null)).isZero();
  }
}
