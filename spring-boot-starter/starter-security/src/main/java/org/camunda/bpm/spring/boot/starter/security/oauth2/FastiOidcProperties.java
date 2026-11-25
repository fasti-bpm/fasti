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
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Settings of the single sign-on integration that go beyond the upstream
 * {@code camunda.bpm.oauth2.*} properties. The upstream properties keep their
 * meaning: {@code camunda.bpm.oauth2.identity-provider.group-name-attribute}
 * still names the claim that holds the groups.
 */
@ConfigurationProperties(FastiOidcProperties.PREFIX)
public class FastiOidcProperties {

  public static final String PREFIX = "fasti.security.oidc";

  /**
   * Text of the sign-in button of the login page, e.g. {@code Keycloak}; the
   * page shows "Sign in with &lt;label&gt;". Defaults to the
   * {@code client-name} of the client registration.
   */
  private String loginLabel;

  /**
   * Id of the client registration used by the login page; only needed when
   * more than one registration is configured.
   */
  private String registrationId;

  /**
   * Where the back-channel logout posts the logout of each session, for example
   * {@code http://localhost:8080/logout}. Defaults to the local connector for
   * plain HTTP and to the address of the incoming request with TLS.
   */
  private String backChannelLogoutUri;

  @NestedConfigurationProperty
  private Groups groups = new Groups();

  @NestedConfigurationProperty
  private UserClaims user = new UserClaims();

  @NestedConfigurationProperty
  private Tenants tenants = new Tenants();

  @NestedConfigurationProperty
  private Rest rest = new Rest();

  public static class Groups {

    /**
     * Claim values that make the user a member of the administrators group
     * ({@code camunda-admin}).
     */
    private List<String> admin = new ArrayList<>();

    /**
     * Maps claim values to engine group ids, e.g. an Entra ID group object id
     * to {@code accounting}.
     */
    private Map<String, String> mapping = new LinkedHashMap<>();

    /**
     * Whether claim values without a mapping become groups of the same id.
     * The administrators group is never granted this way, only through
     * {@link #admin} or {@link #mapping}.
     */
    private boolean includeUnmapped = true;

    public List<String> getAdmin() {
      return admin;
    }

    public void setAdmin(List<String> admin) {
      this.admin = admin;
    }

    public Map<String, String> getMapping() {
      return mapping;
    }

    public void setMapping(Map<String, String> mapping) {
      this.mapping = mapping;
    }

    public boolean isIncludeUnmapped() {
      return includeUnmapped;
    }

    public void setIncludeUnmapped(boolean includeUnmapped) {
      this.includeUnmapped = includeUnmapped;
    }
  }

  public static class UserClaims {

    private String firstNameClaim = "given_name";
    private String lastNameClaim = "family_name";
    private String emailClaim = "email";

    public String getFirstNameClaim() {
      return firstNameClaim;
    }

    public void setFirstNameClaim(String firstNameClaim) {
      this.firstNameClaim = firstNameClaim;
    }

    public String getLastNameClaim() {
      return lastNameClaim;
    }

    public void setLastNameClaim(String lastNameClaim) {
      this.lastNameClaim = lastNameClaim;
    }

    public String getEmailClaim() {
      return emailClaim;
    }

    public void setEmailClaim(String emailClaim) {
      this.emailClaim = emailClaim;
    }
  }

  public static class Tenants {

    /**
     * Claim that holds the tenant ids of the user; tenants are not mapped if
     * it is not set.
     */
    private String claim;

    public String getClaim() {
      return claim;
    }

    public void setClaim(String claim) {
      this.claim = claim;
    }
  }

  public static class Rest {

    /**
     * Require {@code Authorization: Bearer <JWT>} on the REST API. Requests
     * without a valid token are rejected with 401; basic authentication keeps
     * working only where the engine authentication filter checks it (Run with
     * {@code camunda.bpm.run.auth.enabled=true}).
     */
    private boolean bearerEnabled = false;

    /**
     * Issuer of the access tokens; required. The signing keys are discovered
     * from its OpenID configuration unless {@link #jwkSetUri} is set.
     */
    private String issuerUri;

    /**
     * Location of the signing keys; optional.
     */
    private String jwkSetUri;

    /**
     * Accepted audiences ({@code aud}); at least one is required.
     */
    private List<String> audiences = new ArrayList<>();

    /**
     * Accepted signature algorithms.
     */
    private List<String> jwsAlgorithms = new ArrayList<>(List.of("RS256"));

    /**
     * Claim that holds the user id.
     */
    private String userIdClaim = "sub";

    /**
     * Claim that holds the groups; defaults to
     * {@code camunda.bpm.oauth2.identity-provider.group-name-attribute}.
     */
    private String groupsClaim;

    /**
     * Whether Basic credentials are still accepted next to bearer tokens on the REST API.
     * {@code false} makes the REST API bearer-only (single sign-on installations).
     */
    private boolean basicEnabled = true;

    public boolean isBasicEnabled() {
      return basicEnabled;
    }

    public void setBasicEnabled(boolean basicEnabled) {
      this.basicEnabled = basicEnabled;
    }

    public boolean isBearerEnabled() {
      return bearerEnabled;
    }

    public void setBearerEnabled(boolean bearerEnabled) {
      this.bearerEnabled = bearerEnabled;
    }

    public String getIssuerUri() {
      return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
      this.issuerUri = issuerUri;
    }

    public String getJwkSetUri() {
      return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
      this.jwkSetUri = jwkSetUri;
    }

    public List<String> getAudiences() {
      return audiences;
    }

    public void setAudiences(List<String> audiences) {
      this.audiences = audiences;
    }

    public List<String> getJwsAlgorithms() {
      return jwsAlgorithms;
    }

    public void setJwsAlgorithms(List<String> jwsAlgorithms) {
      this.jwsAlgorithms = jwsAlgorithms;
    }

    public String getUserIdClaim() {
      return userIdClaim;
    }

    public void setUserIdClaim(String userIdClaim) {
      this.userIdClaim = userIdClaim;
    }

    public String getGroupsClaim() {
      return groupsClaim;
    }

    public void setGroupsClaim(String groupsClaim) {
      this.groupsClaim = groupsClaim;
    }
  }

  public String getLoginLabel() {
    return loginLabel;
  }

  public void setLoginLabel(String loginLabel) {
    this.loginLabel = loginLabel;
  }

  public String getRegistrationId() {
    return registrationId;
  }

  public void setRegistrationId(String registrationId) {
    this.registrationId = registrationId;
  }

  public String getBackChannelLogoutUri() {
    return backChannelLogoutUri;
  }

  public void setBackChannelLogoutUri(String backChannelLogoutUri) {
    this.backChannelLogoutUri = backChannelLogoutUri;
  }

  public Groups getGroups() {
    return groups;
  }

  public void setGroups(Groups groups) {
    this.groups = groups;
  }

  public UserClaims getUser() {
    return user;
  }

  public void setUser(UserClaims user) {
    this.user = user;
  }

  public Tenants getTenants() {
    return tenants;
  }

  public void setTenants(Tenants tenants) {
    this.tenants = tenants;
  }

  public Rest getRest() {
    return rest;
  }

  public void setRest(Rest rest) {
    this.rest = rest;
  }
}
