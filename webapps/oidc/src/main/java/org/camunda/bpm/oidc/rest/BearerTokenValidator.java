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
package org.camunda.bpm.oidc.rest;

import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.camunda.bpm.oidc.OidcProvider;
import org.camunda.bpm.oidc.OidcSettings;

/**
 * Validates the JWT access tokens sent to the REST API: signature with a key of
 * the provider and an accepted asymmetric algorithm, issuer, audience (one of
 * {@code rest.audiences}), expiry (required) and not-before, with the
 * configured clock skew. Only signed tokens are accepted.
 */
public class BearerTokenValidator {

  private static final Logger LOG = Logger.getLogger(BearerTokenValidator.class.getName());

  protected final OidcProvider provider;
  protected final Set<String> audiences;
  protected final int clockSkewSeconds;
  protected volatile DefaultJWTProcessor<SecurityContext> processor;

  public BearerTokenValidator(OidcProvider provider, List<String> audiences, long clockSkewSeconds) {
    if (audiences == null || audiences.isEmpty()) {
      throw new IllegalStateException(OidcSettings.PREFIX + "rest.audiences must name at least one audience when bearer tokens are enabled");
    }
    this.provider = provider;
    this.audiences = Set.copyOf(audiences);
    this.clockSkewSeconds = (int) Math.max(0, Math.min(clockSkewSeconds, 600));
  }

  public static BearerTokenValidator of(OidcSettings settings) {
    String issuer = settings.get("rest.issuer-uri", settings.getIssuerUri());
    OidcProvider provider = new OidcProvider(issuer, settings.get("rest.jwk-set-uri"),
        settings.getList("rest.jws-algorithms"));
    return new BearerTokenValidator(provider, settings.getList("rest.audiences"), settings.getClockSkewSeconds());
  }

  /**
   * @return the claims of a valid token, null if the token is not valid
   */
  public Map<String, Object> validate(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      JWTClaimsSet claims = processor().process(jwt, null);
      return claims.toJSONObject();
    } catch (ParseException | BadJOSEException | JOSEException e) {
      LOG.log(Level.FINE, "Bearer token rejected: {0}", e.getMessage());
      return null;
    } catch (RuntimeException e) {
      // e.g. the provider cannot be reached to fetch its keys
      LOG.log(Level.WARNING, "Bearer token could not be validated: " + e.getMessage(), e);
      return null;
    }
  }

  protected DefaultJWTProcessor<SecurityContext> processor() {
    DefaultJWTProcessor<SecurityContext> result = processor;
    if (result == null) {
      synchronized (this) {
        result = processor;
        if (result == null) {
          result = new DefaultJWTProcessor<>();
          result.setJWSKeySelector(provider.keySelector());
          DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(
              new HashSet<>(audiences),
              new JWTClaimsSet.Builder().issuer(provider.getIssuer().getValue()).build(),
              Set.of(JWTClaimNames.EXPIRATION_TIME),
              null);
          verifier.setMaxClockSkew(clockSkewSeconds);
          result.setJWTClaimsSetVerifier(verifier);
          processor = result;
        }
      }
    }
    return result;
  }
}
