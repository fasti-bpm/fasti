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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

/**
 * An OpenID Connect provider: its metadata, discovered from the issuer on
 * first use (and retried until that succeeds, so a server may start while the
 * provider is down), and the signing keys, fetched and cached by Nimbus.
 */
public class OidcProvider {

  public static final int CONNECT_TIMEOUT_MILLIS = 5_000;
  public static final int READ_TIMEOUT_MILLIS = 10_000;

  protected final Issuer issuer;
  protected final URI configuredJwkSetUri;
  protected final Set<JWSAlgorithm> algorithms;

  protected volatile OIDCProviderMetadata metadata;
  protected volatile JWKSource<SecurityContext> jwkSource;

  /**
   * @param issuerUri the issuer; its OpenID configuration is read unless only
   *                  the keys are needed and {@code jwkSetUri} is given
   * @param jwkSetUri location of the signing keys, may be null
   * @param algorithms accepted signature algorithms; only asymmetric ones
   */
  public OidcProvider(String issuerUri, String jwkSetUri, List<String> algorithms) {
    if (issuerUri == null || issuerUri.isBlank()) {
      throw new IllegalStateException(OidcSettings.PREFIX + "issuer-uri must be set");
    }
    this.issuer = new Issuer(issuerUri);
    this.configuredJwkSetUri = jwkSetUri == null || jwkSetUri.isBlank() ? null : URI.create(jwkSetUri);
    this.algorithms = parseAlgorithms(algorithms);
  }

  public static Set<JWSAlgorithm> parseAlgorithms(List<String> names) {
    Set<JWSAlgorithm> result = new LinkedHashSet<>();
    for (String name : names == null || names.isEmpty() ? List.of("RS256") : names) {
      JWSAlgorithm algorithm = JWSAlgorithm.parse(name.trim());
      // a shared secret would let every client of the provider mint tokens
      if (!JWSAlgorithm.Family.RSA.contains(algorithm) && !JWSAlgorithm.Family.EC.contains(algorithm)
          && !JWSAlgorithm.Family.ED.contains(algorithm)) {
        throw new IllegalStateException("Unsupported JWS algorithm: " + name + " (only asymmetric algorithms)");
      }
      result.add(algorithm);
    }
    return result;
  }

  public Issuer getIssuer() {
    return issuer;
  }

  public Set<JWSAlgorithm> getAlgorithms() {
    return algorithms;
  }

  /** The metadata of the provider; discovered on first use. */
  public OIDCProviderMetadata metadata() {
    OIDCProviderMetadata result = metadata;
    if (result == null) {
      synchronized (this) {
        result = metadata;
        if (result == null) {
          try {
            result = OIDCProviderMetadata.resolve(issuer, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
          } catch (GeneralException | IOException e) {
            throw new IllegalStateException("Cannot read the OpenID configuration of " + issuer + ": " + e.getMessage(), e);
          }
          metadata = result;
        }
      }
    }
    return result;
  }

  public JWKSource<SecurityContext> jwkSource() {
    JWKSource<SecurityContext> result = jwkSource;
    if (result == null) {
      synchronized (this) {
        result = jwkSource;
        if (result == null) {
          URI uri = configuredJwkSetUri != null ? configuredJwkSetUri : metadata().getJWKSetURI();
          if (uri == null) {
            throw new IllegalStateException("The provider " + issuer + " announces no jwks_uri");
          }
          URL url;
          try {
            url = uri.toURL();
          } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid jwks_uri " + uri, e);
          }
          result = JWKSourceBuilder.<SecurityContext>create(url).retrying(true).build();
          jwkSource = result;
        }
      }
    }
    return result;
  }

  public JWSVerificationKeySelector<SecurityContext> keySelector() {
    return new JWSVerificationKeySelector<>(algorithms, jwkSource());
  }
}
