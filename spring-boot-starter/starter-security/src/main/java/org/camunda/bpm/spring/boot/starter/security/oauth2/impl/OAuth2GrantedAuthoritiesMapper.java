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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

/**
 * Replaces the authorities of a login with the engine groups and tenants
 * derived from the claims of the user. Authorities such as {@code OIDC_USER}
 * or {@code SCOPE_openid} are dropped: they would otherwise show up as groups.
 */
public class OAuth2GrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

  private static final Logger logger = LoggerFactory.getLogger(OAuth2GrantedAuthoritiesMapper.class);

  private final OidcClaimMapper claimMapper;

  public OAuth2GrantedAuthoritiesMapper(OAuth2Properties oAuth2Properties) {
    this(new OidcClaimMapper(oAuth2Properties, new FastiOidcProperties()));
  }

  public OAuth2GrantedAuthoritiesMapper(OidcClaimMapper claimMapper) {
    this.claimMapper = claimMapper;
  }

  @Override
  public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
    Set<GrantedAuthority> mappedAuthorities = new LinkedHashSet<>();
    for (GrantedAuthority authority : authorities) {
      if (authority instanceof OAuth2UserAuthority userAuthority) {
        var claims = userAuthority.getAttributes();
        String groupsClaim = claimMapper.getGroupsClaim();
        if (groupsClaim != null) {
          Object raw = OidcClaimMapper.lookup(claims, groupsClaim);
          if (raw == null) {
            logger.debug("Attribute {} is not available", groupsClaim);
          } else if (!(raw instanceof Collection) && !(raw instanceof String)) {
            logger.error("Could not map granted authorities, unsupported group attribute type: {}", raw.getClass());
          }
        }
        claimMapper.groups(claims, claimMapper.getGroupsClaim())
            .forEach(group -> mappedAuthorities.add(new SimpleGrantedAuthority(group)));
        claimMapper.tenants(claims)
            .forEach(tenant -> mappedAuthorities.add(new TenantGrantedAuthority(tenant)));
      }
    }
    logger.debug("Authorities mapped from {} to {}", authorities, mappedAuthorities);
    return mappedAuthorities;
  }
}
