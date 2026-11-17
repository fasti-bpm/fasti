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

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.LogoutTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import com.nimbusds.openid.connect.sdk.validators.LogoutTokenValidator;
import org.camunda.bpm.oidc.OidcProvider;
import org.camunda.bpm.oidc.OidcSettings;

/**
 * The relying party side of the authorization code flow with PKCE (S256),
 * {@code state} and {@code nonce}; the validation of ID and logout tokens; and
 * the RP-initiated logout URL. Holds no per-user state.
 */
public class OidcClient {

  /** Claims of the user info response that never replace those of the ID token. */
  protected static final Set<String> PROTECTED_CLAIMS = Set.of("iss", "sub", "aud", "exp", "iat", "nbf", "nonce",
      "azp", "sid", "auth_time", "acr", "amr", "at_hash", "c_hash", "jti");

  protected final OidcProvider provider;
  protected final ClientID clientId;
  protected final Secret clientSecret;
  protected final boolean secretPost;
  protected final Scope scope;
  protected final boolean userInfo;
  protected final int clockSkewSeconds;

  public OidcClient(OidcSettings settings) {
    this(new OidcProvider(settings.getIssuerUri(), settings.get("jwk-set-uri"), settings.getList("jws-algorithms")),
        settings.get("client-id"), settings.get("client-secret"),
        "client_secret_post".equals(settings.get("client-authentication-method")),
        settings.get("scope", "openid profile email"),
        settings.getBoolean("userinfo", true), settings.getClockSkewSeconds());
  }

  public OidcClient(OidcProvider provider, String clientId, String clientSecret, boolean secretPost, String scope,
                    boolean userInfo, long clockSkewSeconds) {
    if (clientId == null || clientId.isBlank()) {
      throw new IllegalStateException(OidcSettings.PREFIX + "client-id must be set");
    }
    this.provider = provider;
    this.clientId = new ClientID(clientId);
    this.clientSecret = clientSecret == null || clientSecret.isBlank() ? null : new Secret(clientSecret);
    this.secretPost = secretPost;
    Scope parsed = Scope.parse(scope.replace(',', ' '));
    if (!parsed.contains("openid")) {
      parsed.add("openid");
    }
    this.scope = parsed;
    this.userInfo = userInfo;
    this.clockSkewSeconds = (int) Math.max(0, Math.min(clockSkewSeconds, 600));
  }

  public OidcProvider getProvider() {
    return provider;
  }

  /** A pending login: what the callback must find again. */
  public record PendingLogin(String state, String nonce, String codeVerifier, String returnUrl, long createdAt)
      implements java.io.Serializable {
  }

  public PendingLogin newPendingLogin(String returnUrl) {
    return new PendingLogin(new State().getValue(), new Nonce().getValue(), new CodeVerifier().getValue(), returnUrl,
        System.currentTimeMillis());
  }

  /** The URL of the authorization endpoint for the pending login. */
  public URI authorizationUri(PendingLogin pending, URI redirectUri) {
    OIDCProviderMetadata metadata = provider.metadata();
    return new AuthenticationRequest.Builder(new ResponseType(ResponseType.Value.CODE), scope, clientId, redirectUri)
        .endpointURI(metadata.getAuthorizationEndpointURI())
        .state(new State(pending.state()))
        .nonce(new Nonce(pending.nonce()))
        .codeChallenge(new CodeVerifier(pending.codeVerifier()), CodeChallengeMethod.S256)
        .build()
        .toURI();
  }

  /** The result of a successful login: the claims and what logout needs. */
  public record LoginResult(Map<String, Object> claims, String idToken) {
  }

  /**
   * Exchanges the code, validates the ID token (signature, issuer, audience,
   * expiry, nonce) and adds the claims of the user info endpoint.
   *
   * @throws OidcException if any step fails
   */
  public LoginResult completeLogin(String code, PendingLogin pending, URI redirectUri) {
    OIDCProviderMetadata metadata = provider.metadata();
    AuthorizationCodeGrant grant = new AuthorizationCodeGrant(new AuthorizationCode(code), redirectUri,
        new CodeVerifier(pending.codeVerifier()));
    TokenRequest request;
    if (clientSecret == null) {
      request = new TokenRequest(metadata.getTokenEndpointURI(), clientId, grant, null);
    } else {
      ClientAuthentication authentication = secretPost
          ? new ClientSecretPost(clientId, clientSecret) : new ClientSecretBasic(clientId, clientSecret);
      request = new TokenRequest(metadata.getTokenEndpointURI(), authentication, grant, null);
    }
    TokenResponse response;
    try {
      response = OIDCTokenResponseParser.parse(send(request.toHTTPRequest()).send());
    } catch (IOException | ParseException e) {
      throw new OidcException("token request failed: " + e.getMessage(), e);
    }
    if (!response.indicatesSuccess()) {
      throw new OidcException("token request rejected: " + response.toErrorResponse().getErrorObject());
    }
    OIDCTokenResponse success = (OIDCTokenResponse) response.toSuccessResponse();
    JWT idToken = success.getOIDCTokens().getIDToken();
    if (idToken == null) {
      throw new OidcException("no ID token in the token response");
    }
    Map<String, Object> claims;
    try {
      IDTokenValidator validator = new IDTokenValidator(provider.getIssuer(), clientId, provider.keySelector(), null);
      validator.setMaxClockSkew(clockSkewSeconds);
      claims = new LinkedHashMap<>(validator.validate(idToken, new Nonce(pending.nonce())).toJSONObject());
    } catch (BadJOSEException | JOSEException e) {
      throw new OidcException("invalid ID token: " + e.getMessage(), e);
    }
    if (userInfo && metadata.getUserInfoEndpointURI() != null) {
      addUserInfo(claims, metadata.getUserInfoEndpointURI(), success.getOIDCTokens().getAccessToken());
    }
    return new LoginResult(claims, idToken.getParsedString());
  }

  protected void addUserInfo(Map<String, Object> claims, URI endpoint, AccessToken accessToken) {
    UserInfoResponse response;
    try {
      response = UserInfoResponse.parse(send(new UserInfoRequest(endpoint, accessToken).toHTTPRequest()).send());
    } catch (IOException | ParseException e) {
      throw new OidcException("user info request failed: " + e.getMessage(), e);
    }
    if (!response.indicatesSuccess()) {
      throw new OidcException("user info request rejected: " + response.toErrorResponse().getErrorObject());
    }
    Map<String, Object> info = response.toSuccessResponse().getUserInfo().toJSONObject();
    if (!String.valueOf(claims.get("sub")).equals(String.valueOf(info.get("sub")))) {
      throw new OidcException("user info is about another subject");
    }
    info.forEach((name, value) -> {
      if (!PROTECTED_CLAIMS.contains(name)) {
        claims.put(name, value);
      }
    });
  }

  protected HTTPRequest send(HTTPRequest request) {
    request.setConnectTimeout(OidcProvider.CONNECT_TIMEOUT_MILLIS);
    request.setReadTimeout(OidcProvider.READ_TIMEOUT_MILLIS);
    return request;
  }

  /**
   * Validates a back-channel logout token.
   *
   * @return the claims {@code sid} and {@code sub}
   * @throws OidcException if the token is not valid
   */
  public LogoutTokenClaimsSet validateLogoutToken(String token) {
    try {
      JWT jwt = JWTParser.parse(token);
      // providers differ in the "typ" header; it is not required
      LogoutTokenValidator validator = new LogoutTokenValidator(provider.getIssuer(), clientId, false,
          provider.keySelector(), null);
      validator.setMaxClockSkew(clockSkewSeconds);
      return validator.validate(jwt);
    } catch (java.text.ParseException | BadJOSEException | JOSEException e) {
      throw new OidcException("invalid logout token: " + e.getMessage(), e);
    }
  }

  /** The end session endpoint with the hint and the page to come back to, null if the provider has none. */
  public URI endSessionUri(String idTokenHint, String postLogoutRedirectUri) {
    URI endpoint = provider.metadata().getEndSessionEndpointURI();
    if (endpoint == null) {
      return null;
    }
    StringBuilder uri = new StringBuilder(endpoint.toString());
    char separator = endpoint.getRawQuery() == null ? '?' : '&';
    if (idTokenHint != null) {
      uri.append(separator).append("id_token_hint=").append(encode(idTokenHint));
      separator = '&';
    }
    uri.append(separator).append("client_id=").append(encode(clientId.getValue()));
    if (postLogoutRedirectUri != null) {
      uri.append("&post_logout_redirect_uri=").append(encode(postLogoutRedirectUri));
    }
    return URI.create(uri.toString());
  }

  protected static String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  /** A failed step of the protocol; the message is for the log, not for the user. */
  public static class OidcException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OidcException(String message) {
      super(message);
    }

    public OidcException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
