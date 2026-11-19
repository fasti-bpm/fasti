/*
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Turns the claims of an ID token, user info response or access token into
 * the engine's view of a user: groups, tenants, name and email.
 * <p>
 * The administrators group is only granted through an explicit mapping, never
 * because a claim happens to carry the value {@code camunda-admin}: groups of
 * an identity provider are often managed by people who must not become
 * administrators of the engine.
 */
public class OidcClaimMapper {

  private static volatile OidcClaimMapper current = new OidcClaimMapper(new OAuth2Properties(), new FastiOidcProperties());

  protected final String groupsClaim;
  protected final String groupsDelimiter;
  protected final FastiOidcProperties properties;

  public OidcClaimMapper(OAuth2Properties oAuth2Properties, FastiOidcProperties properties) {
    this.groupsClaim = oAuth2Properties.getIdentityProvider().getGroupNameAttribute();
    String delimiter = oAuth2Properties.getIdentityProvider().getGroupNameDelimiter();
    this.groupsDelimiter = delimiter == null || delimiter.isEmpty() ? "," : delimiter;
    this.properties = properties;
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

  /** The claim that holds the groups of a login, may be null. */
  public String getGroupsClaim() {
    return groupsClaim;
  }

  /** The claim that holds the groups of a REST API access token, may be null. */
  public String getRestGroupsClaim() {
    String claim = properties.getRest().getGroupsClaim();
    return claim != null && !claim.isBlank() ? claim : groupsClaim;
  }

  /** Whether groups can be derived from claims at all. */
  public boolean mapsGroups(String claim) {
    return (claim != null && !claim.isBlank()) || !properties.getGroups().getMapping().isEmpty()
        || !properties.getGroups().getAdmin().isEmpty();
  }

  public boolean mapsTenants() {
    String claim = properties.getTenants().getClaim();
    return claim != null && !claim.isBlank();
  }

  /**
   * @return the engine group ids of the given claims, in the order of the claim
   */
  public List<String> groups(Map<String, Object> claims, String claim) {
    FastiOidcProperties.Groups config = properties.getGroups();
    Set<String> result = new LinkedHashSet<>();
    for (String value : values(claims, claim)) {
      String mapped = config.getMapping().get(value);
      if (mapped != null && !mapped.isBlank()) {
        result.add(mapped.trim());
      }
      if (config.getAdmin().contains(value)) {
        result.add(Groups.CAMUNDA_ADMIN);
      }
      if (mapped == null && config.isIncludeUnmapped() && !Groups.CAMUNDA_ADMIN.equals(value)) {
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
    return new ArrayList<>(new LinkedHashSet<>(values(claims, properties.getTenants().getClaim())));
  }

  public String firstName(Map<String, Object> claims) {
    return string(claims, properties.getUser().getFirstNameClaim());
  }

  public String lastName(Map<String, Object> claims) {
    return string(claims, properties.getUser().getLastNameClaim());
  }

  public String email(Map<String, Object> claims) {
    return string(claims, properties.getUser().getEmailClaim());
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

  protected static String string(Map<String, Object> claims, String claim) {
    Object value = lookup(claims, claim);
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  protected static void addTrimmed(List<String> result, String value) {
    String trimmed = value.trim();
    if (!trimmed.isEmpty()) {
      result.add(trimmed);
    }
  }
}
