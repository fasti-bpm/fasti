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
package org.camunda.bpm.engine.rest.security.auth.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.digest._apacheCommonsCodec.Base64;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

/**
 * <p>
 * Authenticates a request against the provided process engine's identity service by applying http basic authentication.
 * </p>
 * <p>
 * Fasti: with a cache time to live above zero ({@link #setCacheTimeToLiveMillis(long)}), a
 * successful check is remembered for that time together with the groups and tenants of the user,
 * so that repeated requests with the same credentials do not read the user, its groups and its
 * tenants again. The cache holds a salted SHA-256 hash of engine, user name and password, never
 * the password. A new password takes effect at once (it is another key); a deleted or locked
 * user, changed memberships and the old password after a change stop working after the time to
 * live at the latest. Failed checks are never cached. Off by default (time to live 0).
 * </p>
 * <p>
 * Fasti: after a successful check the user name as typed is replaced by the id the identity
 * service stores for the user, as the webapp login does. An identity provider that matches user
 * names without regard to case (LDAP, a database with a case-insensitive collation) accepts
 * {@code john} and {@code JOHN} alike; both now authenticate as the same user, so that checks
 * comparing user ids, such as the four-eyes control, see one person.
 * </p>
 *
 * @author Thorben Lindhauer
 */
public class HttpBasicAuthenticationProvider implements AuthenticationProvider {

  protected static final String BASIC_AUTH_HEADER_PREFIX = "Basic ";

  /** Fasti: entries above which expired entries are removed, and the whole cache if that is not enough. */
  protected static final int CACHE_MAX_ENTRIES = 10_000;

  private static final byte[] CACHE_KEY_SALT = new byte[32];
  static {
    new SecureRandom().nextBytes(CACHE_KEY_SALT);
  }

  protected volatile long cacheTimeToLiveMillis = 0;
  protected final Map<String, CachedIdentity> cache = new ConcurrentHashMap<>();
  /**
   * Fasti: counts the times the cache was emptied, so that a check that ran while it was emptied
   * (users, groups or tenants changed meanwhile) does not store what it read before the change.
   */
  protected final AtomicLong cacheGeneration = new AtomicLong();

  /** Fasti: a successful check of a user name and password, with the user's groups and tenants. */
  protected static final class CachedIdentity {
    final String userId;
    final List<String> groups;
    final List<String> tenants;
    final long expiresAtNanos;

    CachedIdentity(String userId, List<String> groups, List<String> tenants, long expiresAtNanos) {
      this.userId = userId;
      this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
      this.tenants = Collections.unmodifiableList(new ArrayList<>(tenants));
      this.expiresAtNanos = expiresAtNanos;
    }

    boolean isExpired(long nowNanos) {
      return nowNanos - expiresAtNanos >= 0;
    }
  }

  /**
   * Fasti: how long a successful check is cached, in milliseconds; 0 (the default) turns the
   * cache off.
   */
  public void setCacheTimeToLiveMillis(long cacheTimeToLiveMillis) {
    if (cacheTimeToLiveMillis < 0) {
      throw new IllegalArgumentException("The cache time to live must not be negative: " + cacheTimeToLiveMillis);
    }
    this.cacheTimeToLiveMillis = cacheTimeToLiveMillis;
    if (cacheTimeToLiveMillis == 0) {
      clearCache();
    }
  }

  public long getCacheTimeToLiveMillis() {
    return cacheTimeToLiveMillis;
  }

  /** Fasti: forgets every cached check, for example after users, groups or tenants changed. */
  public void clearCache() {
    cacheGeneration.incrementAndGet();
    cache.clear();
  }

  @Override
  public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request,
      ProcessEngine engine) {
    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authorizationHeader != null && authorizationHeader.startsWith(BASIC_AUTH_HEADER_PREFIX)) {
      String encodedCredentials = authorizationHeader.substring(BASIC_AUTH_HEADER_PREFIX.length());
      String decodedCredentials = new String(Base64.decodeBase64(encodedCredentials));
      int firstColonIndex = decodedCredentials.indexOf(":");

      if (firstColonIndex == -1) {
        return AuthenticationResult.unsuccessful();
      } else {
        String userName = decodedCredentials.substring(0, firstColonIndex);
        String password = decodedCredentials.substring(firstColonIndex + 1);
        if (cacheTimeToLiveMillis > 0) {
          return authenticateCached(engine, userName, password);
        }
        if (isAuthenticated(engine, userName, password)) {
          return AuthenticationResult.successful(canonicalUserId(engine, userName));
        } else {
          return AuthenticationResult.unsuccessful(userName);
        }
      }
    } else {
      return AuthenticationResult.unsuccessful();
    }
  }

  protected boolean isAuthenticated(ProcessEngine engine, String userName, String password) {
    return engine.getIdentityService().checkPassword(userName, password);
  }

  /**
   * Fasti: the id the identity service stores for the user whose password was just checked
   * under the given name, or the name itself if the user cannot be read or the name matches more
   * than one user.
   */
  protected String canonicalUserId(ProcessEngine engine, String userName) {
    User user;
    try {
      user = engine.getIdentityService().createUserQuery().userId(userName).singleResult();
    } catch (ProcessEngineException e) {
      // an identity provider that finds more than one user for the id: no stored id to choose
      return userName;
    }
    if (user != null && user.getId() != null && !user.getId().isEmpty()) {
      return user.getId();
    }
    return userName;
  }

  /** Fasti: the check through the cache, see the class comment. */
  protected AuthenticationResult authenticateCached(ProcessEngine engine, String userName, String password) {
    String key = cacheKey(engine.getName(), userName, password);
    long now = System.nanoTime();
    CachedIdentity cached = cache.get(key);
    if (cached != null && !cached.isExpired(now)) {
      return successful(cached);
    }
    if (cached != null) {
      cache.remove(key, cached);
    }
    long generation = cacheGeneration.get();
    if (!isAuthenticated(engine, userName, password)) {
      return AuthenticationResult.unsuccessful(userName);
    }
    // the key stays the name as typed (each spelling is checked once), the entry the stored id
    String userId = canonicalUserId(engine, userName);
    CachedIdentity identity = new CachedIdentity(userId, groupsOfUser(engine, userId),
        tenantsOfUser(engine, userId), now + cacheTimeToLiveMillis * 1_000_000L);
    if (cache.size() >= CACHE_MAX_ENTRIES) {
      cache.values().removeIf(entry -> entry.isExpired(now));
      if (cache.size() >= CACHE_MAX_ENTRIES) {
        cache.clear();
      }
    }
    cache.put(key, identity);
    if (cacheGeneration.get() != generation) {
      // emptied while this check ran: what was read may already be stale, answer without keeping it
      cache.remove(key, identity);
    }
    return successful(identity);
  }

  protected AuthenticationResult successful(CachedIdentity identity) {
    AuthenticationResult result = AuthenticationResult.successful(identity.userId);
    result.setGroups(new ArrayList<>(identity.groups));
    result.setTenants(new ArrayList<>(identity.tenants));
    return result;
  }

  /** The same groups as the authentication filter reads when a provider gives none. */
  protected List<String> groupsOfUser(ProcessEngine engine, String userId) {
    List<String> groupIds = new ArrayList<>();
    for (Group group : engine.getIdentityService().createGroupQuery().groupMember(userId).list()) {
      groupIds.add(group.getId());
    }
    return groupIds;
  }

  /** The same tenants as the authentication filter reads when a provider gives none. */
  protected List<String> tenantsOfUser(ProcessEngine engine, String userId) {
    List<String> tenantIds = new ArrayList<>();
    for (Tenant tenant : engine.getIdentityService().createTenantQuery()
        .userMember(userId).includingGroupsOfUser(true).list()) {
      tenantIds.add(tenant.getId());
    }
    return tenantIds;
  }

  protected static String cacheKey(String engineName, String userName, String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(CACHE_KEY_SALT);
      for (String part : new String[] { engineName, userName, password }) {
        byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[] { (byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
            (byte) (bytes.length >>> 8), (byte) bytes.length });
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  @Override
  public void augmentResponseByAuthenticationChallenge(
      HttpServletResponse response, ProcessEngine engine) {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_AUTH_HEADER_PREFIX + "realm=\"" + engine.getName() + "\"");
  }
}
