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
package dev.fasti.spring.boot.mcp.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.User;

/**
 * HTTP basic authentication against the users of the engine
 * ({@link IdentityService#checkPassword}, including its lockout after failed attempts), as the
 * REST API of Run checks it.
 * <p>
 * The identity is that of the user the identity service returns for the name, so a name typed in
 * another case (which a case-insensitive database collation or LDAP accepts) acts as the same
 * user, for example in four-eyes checks.
 * <p>
 * With a cache time to live above zero, a successful check is remembered for that time together
 * with the groups and tenants of the user, so that the tool calls of a client do not read them
 * again each time. The cache holds a salted SHA-256 hash of engine, user name and password, never
 * the password. A new password takes effect at once (it is another key); changes of users,
 * groups, tenants and memberships made on this node clear the cache
 * ({@link IdentityChangePlugin}); changes made elsewhere, and a lockout after failed attempts,
 * take effect after the time to live at the latest. Failed checks are never cached.
 */
public class BasicMcpAuthenticator implements McpAuthenticator {

  protected static final String PREFIX = "Basic ";

  /** Entries above which expired entries are removed, and the whole cache if that is not enough. */
  protected static final int CACHE_MAX_ENTRIES = 10_000;

  private static final byte[] CACHE_KEY_SALT = new byte[32];

  static {
    new SecureRandom().nextBytes(CACHE_KEY_SALT);
  }

  protected final long cacheTimeToLiveNanos;
  protected final Map<String, CachedIdentity> cache = new ConcurrentHashMap<>();
  /** Counts the clearings, so that a check that ran across one does not refill the cache. */
  protected final AtomicLong generation = new AtomicLong();

  /** A successful check, with the identity it resolved to. */
  protected record CachedIdentity(McpIdentity identity, long expiresAtNanos) {

    boolean isExpired(long nowNanos) {
      return nowNanos - expiresAtNanos >= 0;
    }
  }

  /** Without cache: every request checks the password and reads groups and tenants. */
  public BasicMcpAuthenticator() {
    this(Duration.ZERO);
  }

  /** @param cacheTimeToLive how long a successful check is remembered; zero turns the cache off */
  public BasicMcpAuthenticator(Duration cacheTimeToLive) {
    if (cacheTimeToLive == null || cacheTimeToLive.isNegative()) {
      throw new IllegalArgumentException("The cache time to live must not be negative: " + cacheTimeToLive);
    }
    this.cacheTimeToLiveNanos = cacheTimeToLive.toNanos();
  }

  /** Forgets every cached check, for example after users, groups or tenants changed. */
  public void clearCache() {
    generation.incrementAndGet();
    cache.clear();
  }

  @Override
  public boolean supports(String authorizationHeader) {
    return authorizationHeader != null
        && authorizationHeader.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
  }

  @Override
  public McpIdentity authenticate(String authorizationHeader, ProcessEngine engine) {
    String decoded;
    try {
      byte[] bytes = Base64.getDecoder().decode(authorizationHeader.substring(PREFIX.length()).trim());
      decoded = new String(bytes, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
    int colon = decoded.indexOf(':');
    if (colon <= 0) {
      return null;
    }
    String userName = decoded.substring(0, colon);
    String password = decoded.substring(colon + 1);
    if (password.isEmpty()) {
      return null;
    }
    if (cacheTimeToLiveNanos == 0) {
      return check(engine, userName, password);
    }

    String key = cacheKey(engine.getName(), userName, password);
    long now = System.nanoTime();
    CachedIdentity cached = cache.get(key);
    if (cached != null && !cached.isExpired(now)) {
      return cached.identity();
    }
    if (cached != null) {
      cache.remove(key, cached);
    }
    long checkedGeneration = generation.get();
    McpIdentity identity = check(engine, userName, password);
    if (identity == null) {
      return null;
    }
    if (cache.size() >= CACHE_MAX_ENTRIES) {
      cache.values().removeIf(entry -> entry.isExpired(now));
      if (cache.size() >= CACHE_MAX_ENTRIES) {
        cache.clear();
      }
    }
    CachedIdentity entry = new CachedIdentity(identity, now + cacheTimeToLiveNanos);
    if (generation.get() == checkedGeneration) {
      cache.put(key, entry);
      // identities changed while the entry was added: it may be stale
      if (generation.get() != checkedGeneration) {
        cache.remove(key, entry);
      }
    }
    return identity;
  }

  /** Checks the password and resolves the identity of the user, or returns {@code null}. */
  protected McpIdentity check(ProcessEngine engine, String userName, String password) {
    IdentityService identityService = engine.getIdentityService();
    if (!identityService.checkPassword(userName, password)) {
      return null;
    }
    User user = identityService.createUserQuery().userId(userName).singleResult();
    String userId = user != null && user.getId() != null ? user.getId() : userName;
    return McpIdentity.resolve(identityService, userId, null, null);
  }

  protected static String cacheKey(String engineName, String userName, String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(CACHE_KEY_SALT);
      for (String part : new String[] { engineName, userName, password }) {
        byte[] bytes = String.valueOf(part).getBytes(StandardCharsets.UTF_8);
        // length-prefixed, so that no two triples hash the same input
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
  public String challenge(HttpServletRequest request) {
    return "Basic realm=\"fasti-mcp\", charset=\"UTF-8\"";
  }
}
