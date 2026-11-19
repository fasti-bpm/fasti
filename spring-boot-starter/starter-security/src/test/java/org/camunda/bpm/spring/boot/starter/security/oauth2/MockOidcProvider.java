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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny OpenID provider for tests: discovery document, signing keys, token
 * endpoint. The authorization endpoint is never called; a test reads the
 * authorization request from the redirect and calls the callback itself.
 */
public class MockOidcProvider {

  public static final String CLIENT_ID = "fasti-web";
  public static final String API_AUDIENCE = "fasti-api";

  private final HttpServer server;
  private final String issuer;
  private final RSAKey signingKey;
  private final RSAKey foreignKey;

  /** claims of the next ID token, set by the test before the callback */
  public volatile Map<String, Object> nextUserClaims = Map.of();
  /** nonce of the authorization request, set by the test before the callback */
  public volatile String nextNonce;
  /** form parameters of the last token request */
  public final Map<String, String> lastTokenRequest = new ConcurrentHashMap<>();

  public MockOidcProvider(int port) {
    try {
      this.issuer = "http://localhost:" + port;
      this.signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
      this.foreignKey = new RSAKeyGenerator(2048).keyID("k1").generate();
      this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    } catch (IOException | JOSEException e) {
      throw new IllegalStateException(e);
    }
    server.createContext("/.well-known/openid-configuration", exchange -> respond(exchange, 200, discovery()));
    server.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString()));
    server.createContext("/token", this::token);
    server.start();
  }

  public void stop() {
    server.stop(0);
  }

  public String getIssuer() {
    return issuer;
  }

  private String discovery() {
    return "{\"issuer\":\"" + issuer + "\","
        + "\"authorization_endpoint\":\"" + issuer + "/authorize\","
        + "\"token_endpoint\":\"" + issuer + "/token\","
        + "\"jwks_uri\":\"" + issuer + "/jwks\","
        + "\"end_session_endpoint\":\"" + issuer + "/logout\","
        + "\"response_types_supported\":[\"code\"],"
        + "\"subject_types_supported\":[\"public\"],"
        + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
  }

  private void token(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    lastTokenRequest.clear();
    for (String pair : body.split("&")) {
      int idx = pair.indexOf('=');
      if (idx > 0) {
        lastTokenRequest.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
            URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
      }
    }
    Map<String, Object> idClaims = new HashMap<>(nextUserClaims);
    idClaims.put("aud", List.of(CLIENT_ID));
    if (nextNonce != null) {
      idClaims.put("nonce", nextNonce);
    }
    String idToken = sign(idClaims, Instant.now().plusSeconds(300));
    String accessToken = sign(Map.of("sub", nextUserClaims.getOrDefault("sub", "x"), "aud", List.of(CLIENT_ID)),
        Instant.now().plusSeconds(300));
    respond(exchange, 200, "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\","
        + "\"expires_in\":300,\"scope\":\"openid\",\"id_token\":\"" + idToken + "\"}");
  }

  /** A token of this issuer, signed with its key. */
  public String sign(Map<String, Object> claims, Instant expiresAt) {
    return signWith(signingKey, issuer, claims, expiresAt);
  }

  /** A token that names this issuer but is signed with another key. */
  public String signWithForeignKey(Map<String, Object> claims) {
    return signWith(foreignKey, issuer, claims, Instant.now().plusSeconds(300));
  }

  /** A token that names another issuer. */
  public String signForIssuer(String otherIssuer, Map<String, Object> claims) {
    return signWith(signingKey, otherIssuer, claims, Instant.now().plusSeconds(300));
  }

  /** An unsigned token ({@code alg: none}). */
  public String unsigned(Map<String, Object> claims) {
    return new PlainJWT(claimsSet(issuer, claims, Instant.now().plusSeconds(300))).serialize();
  }

  /** A token signed with HMAC, keyed with the public modulus of the provider. */
  public String hmacSigned(Map<String, Object> claims) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("k1").build(),
          claimsSet(issuer, claims, Instant.now().plusSeconds(300)));
      jwt.sign(new MACSigner(signingKey.getModulus().decode()));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String signWith(RSAKey key, String issuer, Map<String, Object> claims, Instant expiresAt) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
          claimsSet(issuer, claims, expiresAt));
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JWTClaimsSet claimsSet(String issuer, Map<String, Object> claims, Instant expiresAt) {
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(Instant.now().minusSeconds(5)))
        .expirationTime(expiresAt == null ? null : Date.from(expiresAt));
    claims.forEach(builder::claim);
    return builder.build();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
