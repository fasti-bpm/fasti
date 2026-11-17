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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.camunda.bpm.engine.authorization.Groups;

/**
 * Maps the claims of an ID or access token to the engine identity: groups
 * (with the administrators group only through an explicit rule), tenants and
 * the name of the user. Shared by Run/Spring Boot and the servlet container
 * integration, so that the same settings mean the same everywhere.
 * <p>
 * A user never becomes an administrator just because a claim happens to carry
 * the value {@code camunda-admin}: groups of an identity provider are often
 * managed by people who must not become administrators of the engine.
 */
public class ClaimRules {

  protected final String groupsClaim;
  protected final String groupsDelimiter;
  protected final String restGroupsClaim;
  protected final List<String> admin;
  protected final Map<String, String> mapping;
  protected final boolean includeUnmapped;
  protected final String tenantsClaim;
  protected final String firstNameClaim;
  protected final String lastNameClaim;
  protected final String emailClaim;

  protected ClaimRules(Builder builder) {
    this.groupsClaim = blankToNull(builder.groupsClaim);
    this.groupsDelimiter = builder.groupsDelimiter == null || builder.groupsDelimiter.isEmpty() ? "," : builder.groupsDelimiter;
    this.restGroupsClaim = blankToNull(builder.restGroupsClaim);
    this.admin = List.copyOf(builder.admin);
    this.mapping = Collections.unmodifiableMap(new LinkedHashMap<>(builder.mapping));
    this.includeUnmapped = builder.includeUnmapped;
    this.tenantsClaim = blankToNull(builder.tenantsClaim);
    this.firstNameClaim = builder.firstNameClaim;
    this.lastNameClaim = builder.lastNameClaim;
    this.emailClaim = builder.emailClaim;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The claim that holds the groups of a login, may be null. */
  public String getGroupsClaim() {
    return groupsClaim;
  }

  /** The claim that holds the groups of a REST API access token, may be null. */
  public String getRestGroupsClaim() {
    return restGroupsClaim != null ? restGroupsClaim : groupsClaim;
  }

  /** Whether groups can be derived from claims at all. */
  public boolean mapsGroups(String claim) {
    return (claim != null && !claim.isBlank()) || !mapping.isEmpty() || !admin.isEmpty();
  }

  public boolean mapsTenants() {
    return tenantsClaim != null;
  }

  /**
   * @return the engine group ids of the given claims, in the order of the claim
   */
  public List<String> groups(Map<String, Object> claims, String claim) {
    Set<String> result = new LinkedHashSet<>();
    for (String value : values(claims, claim)) {
      String mapped = mapping.get(value);
      if (mapped != null && !mapped.isBlank()) {
        result.add(mapped.trim());
      }
      if (admin.contains(value)) {
        result.add(Groups.CAMUNDA_ADMIN);
      }
      if (mapped == null && includeUnmapped && !Groups.CAMUNDA_ADMIN.equals(value)) {
        result.add(value);
      }
    }
    return new ArrayList<>(result);
  }

  /**
   * @return the tenant ids of the given claims, empty if no tenant claim is configured
   */
  public List<String> tenants(Map<String, Object> claims) {
    if (!mapsTenants()) {
      return Collections.emptyList();
    }
    return new ArrayList<>(new LinkedHashSet<>(values(claims, tenantsClaim)));
  }

  public String firstName(Map<String, Object> claims) {
    return string(claims, firstNameClaim);
  }

  public String lastName(Map<String, Object> claims) {
    return string(claims, lastNameClaim);
  }

  public String email(Map<String, Object> claims) {
    return string(claims, emailClaim);
  }

  /**
   * Reads a claim that is either a list of strings or one string of values
   * separated by the delimiter. Nested claims such as Keycloak's
   * {@code realm_access.roles} are addressed with dots.
   */
  protected List<String> values(Map<String, Object> claims, String claim) {
    Object value = lookup(claims, claim);
    List<String> result = new ArrayList<>();
    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (item instanceof String text) {
          addTrimmed(result, text);
        }
      }
    } else if (value instanceof String text) {
      for (String item : text.split(Pattern.quote(groupsDelimiter))) {
        addTrimmed(result, item);
      }
    }
    return result;
  }

  public static Object lookup(Map<String, Object> claims, String claim) {
    if (claims == null || claim == null || claim.isBlank()) {
      return null;
    }
    if (claims.containsKey(claim)) {
      return claims.get(claim);
    }
    Object current = claims;
    for (String part : claim.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(part);
    }
    return current;
  }

  /** A claim of a single string value, null if absent, blank or of another type. */
  public static String string(Map<String, Object> claims, String claim) {
    Object value = lookup(claims, claim);
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  protected static void addTrimmed(List<String> result, String value) {
    String trimmed = value.trim();
    if (!trimmed.isEmpty()) {
      result.add(trimmed);
    }
  }

  protected static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public static class Builder {
    protected String groupsClaim;
    protected String groupsDelimiter;
    protected String restGroupsClaim;
    protected List<String> admin = new ArrayList<>();
    protected Map<String, String> mapping = new LinkedHashMap<>();
    protected boolean includeUnmapped = true;
    protected String tenantsClaim;
    protected String firstNameClaim = "given_name";
    protected String lastNameClaim = "family_name";
    protected String emailClaim = "email";

    public Builder groupsClaim(String groupsClaim) {
      this.groupsClaim = groupsClaim;
      return this;
    }

    public Builder groupsDelimiter(String groupsDelimiter) {
      this.groupsDelimiter = groupsDelimiter;
      return this;
    }

    public Builder restGroupsClaim(String restGroupsClaim) {
      this.restGroupsClaim = restGroupsClaim;
      return this;
    }

    public Builder admin(List<String> admin) {
      this.admin = admin == null ? new ArrayList<>() : new ArrayList<>(admin);
      return this;
    }

    public Builder mapping(Map<String, String> mapping) {
      this.mapping = mapping == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mapping);
      return this;
    }

    public Builder includeUnmapped(boolean includeUnmapped) {
      this.includeUnmapped = includeUnmapped;
      return this;
    }

    public Builder tenantsClaim(String tenantsClaim) {
      this.tenantsClaim = tenantsClaim;
      return this;
    }

    public Builder firstNameClaim(String firstNameClaim) {
      this.firstNameClaim = firstNameClaim;
      return this;
    }

    public Builder lastNameClaim(String lastNameClaim) {
      this.lastNameClaim = lastNameClaim;
      return this;
    }

    public Builder emailClaim(String emailClaim) {
      this.emailClaim = emailClaim;
      return this;
    }

    public ClaimRules build() {
      return new ClaimRules(this);
    }
  }
}
