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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A minimal OpenID Connect provider for tests: discovery, keys, token endpoint
 * (checks client, code and PKCE verifier), user info and signed tokens.
 */
public class MockOidcServer implements AutoCloseable {

  public static final String CLIENT_ID = "fasti-web";
  public static final String CLIENT_SECRET = "s3cret";

  protected final HttpServer server;
  protected final RSAKey key;
  protected final RSAKey otherKey;
  protected final String issuer;

  /** code -> pending authorization (nonce, challenge, claims) */
  protected final Map<String, Map<String, Object>> codes = new ConcurrentHashMap<>();
  public final Map<String, String> lastTokenRequest = new ConcurrentHashMap<>();
  public volatile Map<String, Object> userInfo = Map.of();

  public MockOidcServer() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("k1").generate();
    otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realm";
    server.createContext("/realm/.well-known/openid-configuration", ex -> json(ex, 200, discovery()));
    server.createContext("/realm/keys", ex -> json(ex, 200, new JWKSet(key.toPublicJWK()).toString()));
    server.createContext("/realm/token", this::token);
    server.createContext("/realm/userinfo", ex -> json(ex, 200, new com.nimbusds.jose.shaded.gson.Gson().toJson(userInfo)));
    server.start();
  }

  public String getIssuer() {
    return issuer;
  }

  protected String discovery() {
    return "{\"issuer\":\"" + issuer + "\",\"authorization_endpoint\":\"" + issuer + "/auth\","
        + "\"token_endpoint\":\"" + issuer + "/token\",\"userinfo_endpoint\":\"" + issuer + "/userinfo\","
        + "\"jwks_uri\":\"" + issuer + "/keys\",\"end_session_endpoint\":\"" + issuer + "/logout\","
        + "\"response_types_supported\":[\"code\"],\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
  }

  /** Lets the next token request with this code succeed. */
  public void expectCode(String code, String nonce, String codeChallenge, Map<String, Object> claims) {
    Map<String, Object> pending = new LinkedHashMap<>();
    pending.put("nonce", nonce);
    pending.put("challenge", codeChallenge);
    pending.put("claims", claims);
    codes.put(code, pending);
  }

  @SuppressWarnings("unchecked")
  protected void token(HttpExchange ex) throws IOException {
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Map<String, String> form = new LinkedHashMap<>();
    for (String pair : body.split("&")) {
      int eq = pair.indexOf('=');
      form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
    }
    lastTokenRequest.clear();
    lastTokenRequest.putAll(form);
    String auth = ex.getRequestHeaders().getFirst("Authorization");
    String expected = "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    Map<String, Object> pending = codes.remove(form.get("code"));
    if (!expected.equals(auth) || pending == null || !"authorization_code".equals(form.get("grant_type"))) {
      json(ex, 400, "{\"error\":\"invalid_grant\"}");
      return;
    }
    try {
      String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
          .digest(form.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
      if (!challenge.equals(pending.get("challenge"))) {
        json(ex, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE\"}");
        return;
      }
      Map<String, Object> claims = new LinkedHashMap<>((Map<String, Object>) pending.get("claims"));
      userInfo = new LinkedHashMap<>(claims);
      claims.put("nonce", pending.get("nonce"));
      String idToken = sign(claims, CLIENT_ID, 300);
      json(ex, 200, "{\"access_token\":\"at-" + UUID.randomUUID() + "\",\"token_type\":\"Bearer\",\"expires_in\":300,"
          + "\"id_token\":\"" + idToken + "\"}");
    } catch (Exception e) {
      json(ex, 500, "{\"error\":\"server_error\"}");
    }
  }

  /** A token signed with the key of the provider. */
  public String sign(Map<String, Object> claims, String audience, long expiresInSeconds) {
    return sign(claims, audience, expiresInSeconds, key, null);
  }

  public String sign(Map<String, Object> claims, String audience, long expiresInSeconds, RSAKey signingKey,
                     JOSEObjectType type) {
    try {
      JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
          .issuer(issuer)
          .audience(audience)
          .issueTime(new Date())
          .expirationTime(new Date(System.currentTimeMillis() + expiresInSeconds * 1000));
      claims.forEach(builder::claim);
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).type(type).build(),
          builder.build());
      jwt.sign(new RSASSASigner(signingKey));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Signed with a key the provider does not publish (same key id). */
  public String signWithOtherKey(Map<String, Object> claims, String audience) {
    return sign(claims, audience, 300, otherKey, null);
  }

  /** HS256 with a shared secret, which must never be accepted. */
  public String signSymmetric(Map<String, Object> claims, String audience) {
    try {
      JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
          .expirationTime(new Date(System.currentTimeMillis() + 300_000));
      claims.forEach(builder::claim);
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
      jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A back-channel logout token for the client. */
  public String logoutToken(String subject, String sid) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", subject);
    if (sid != null) {
      claims.put("sid", sid);
    }
    claims.put("jti", UUID.randomUUID().toString());
    claims.put("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
    return sign(claims, CLIENT_ID, 120, key, new JOSEObjectType("logout+jwt"));
  }

  public static List<String> list(String... values) {
    return List.of(values);
  }

  protected static void json(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = ex.getResponseBody()) {
      out.write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
