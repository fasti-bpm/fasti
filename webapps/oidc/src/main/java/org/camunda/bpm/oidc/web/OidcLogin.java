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
package org.camunda.bpm.oidc.web;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionActivationListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import jakarta.servlet.http.HttpSessionEvent;

/**
 * The single sign-on login of an HTTP session: the engine identity derived
 * from the ID token at login, and what is needed to end the session at the
 * provider. Registers its session for back-channel logout while bound.
 */
public class OidcLogin implements Serializable, HttpSessionBindingListener, HttpSessionActivationListener {

  private static final long serialVersionUID = 1L;

  public static final String SESSION_ATTRIBUTE = OidcLogin.class.getName();

  /** Sessions by {@code sid:<sid>} and {@code sub:<sub>}, for back-channel logout. */
  protected static final Map<String, Set<HttpSession>> SESSIONS = new ConcurrentHashMap<>();

  protected final String userId;
  protected final String subject;
  protected final String providerSessionId;
  protected final String idToken;
  protected final List<String> groups;
  protected final List<String> tenants;
  protected final long authenticatedAt;

  /** When the web application authentication was last built, per engine. */
  protected final Map<String, Long> builtAt = new ConcurrentHashMap<>();

  public OidcLogin(String userId, String subject, String providerSessionId, String idToken,
                   List<String> groups, List<String> tenants) {
    this.userId = userId;
    this.subject = subject;
    this.providerSessionId = providerSessionId;
    this.idToken = idToken;
    this.groups = groups == null ? null : List.copyOf(groups);
    this.tenants = tenants == null ? null : List.copyOf(tenants);
    this.authenticatedAt = System.currentTimeMillis();
  }

  public static OidcLogin of(HttpSession session) {
    if (session == null) {
      return null;
    }
    try {
      Object value = session.getAttribute(SESSION_ATTRIBUTE);
      return value instanceof OidcLogin login ? login : null;
    } catch (IllegalStateException e) {
      // invalidated meanwhile
      return null;
    }
  }

  public String getUserId() {
    return userId;
  }

  public String getSubject() {
    return subject;
  }

  public String getProviderSessionId() {
    return providerSessionId;
  }

  public String getIdToken() {
    return idToken;
  }

  /** The groups from the claims, null if groups are not mapped from claims. */
  public List<String> getGroups() {
    return groups;
  }

  /** The tenants from the claims, null if tenants are not mapped from claims. */
  public List<String> getTenants() {
    return tenants;
  }

  public Map<String, Long> getBuiltAt() {
    return builtAt;
  }

  // ---------------------------------------------------------------- registry

  /** The sessions of a provider session ({@code sid}) or, if null, of a subject. */
  public static Set<HttpSession> sessionsOf(String providerSessionId, String subject) {
    String key = providerSessionId != null ? "sid:" + providerSessionId : "sub:" + subject;
    Set<HttpSession> sessions = SESSIONS.get(key);
    return sessions == null ? Set.of() : Set.copyOf(sessions);
  }

  protected void register(HttpSession session) {
    for (String key : keys()) {
      SESSIONS.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
    }
  }

  protected void unregister(HttpSession session) {
    for (String key : keys()) {
      SESSIONS.computeIfPresent(key, (k, set) -> {
        set.remove(session);
        return set.isEmpty() ? null : set;
      });
    }
  }

  protected List<String> keys() {
    if (providerSessionId != null) {
      return subject != null ? List.of("sid:" + providerSessionId, "sub:" + subject) : List.of("sid:" + providerSessionId);
    }
    return subject != null ? List.of("sub:" + subject) : List.of();
  }

  @Override
  public void valueBound(HttpSessionBindingEvent event) {
    register(event.getSession());
  }

  @Override
  public void valueUnbound(HttpSessionBindingEvent event) {
    unregister(event.getSession());
  }

  @Override
  public void sessionWillPassivate(HttpSessionEvent event) {
    unregister(event.getSession());
  }

  @Override
  public void sessionDidActivate(HttpSessionEvent event) {
    register(event.getSession());
  }
}
