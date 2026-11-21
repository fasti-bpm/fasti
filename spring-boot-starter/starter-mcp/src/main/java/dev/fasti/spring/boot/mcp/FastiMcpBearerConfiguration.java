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
package dev.fasti.spring.boot.mcp;

import java.util.function.Function;

import jakarta.servlet.http.HttpServletRequest;

import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import dev.fasti.spring.boot.mcp.auth.BearerMcpAuthenticator;
import dev.fasti.spring.boot.mcp.auth.McpAuthenticator;
import dev.fasti.spring.boot.mcp.auth.ProtectedResourceMetadataServlet;

/**
 * Bearer tokens on the MCP endpoint, with the checks and claim mapping of the REST API
 * ({@code fasti.security.oidc.rest.*}), when the Spring Security starter is present and
 * {@code fasti.security.oidc.rest.bearer-enabled=true}. Also serves the OAuth 2.0 protected
 * resource metadata (RFC 9728) that MCP clients use to find the identity provider.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
    "org.camunda.bpm.spring.boot.starter.security.oauth2.impl.BearerTokenAuthenticationProvider",
    "org.camunda.bpm.engine.rest.security.auth.AuthenticationResult",
    "org.springframework.security.oauth2.jwt.JwtDecoder" })
@ConditionalOnBooleanProperty(prefix = FastiOidcProperties.PREFIX + ".rest", name = "bearer-enabled")
@EnableConfigurationProperties(FastiOidcProperties.class)
public class FastiMcpBearerConfiguration {

  public static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

  @Bean
  @Order(0)
  public McpAuthenticator fastiMcpBearerAuthenticator(ObjectProvider<JwtDecoder> decoder, FastiOidcProperties oidc,
                                                      FastiMcpProperties properties) {
    // the decoder of the REST API (FastiRestBearerAutoConfiguration) or one of the application
    JwtDecoder jwtDecoder = decoder.getObject();
    String path = properties.getPath();
    Function<HttpServletRequest, String> metadataUrl = request -> baseUrl(request) + METADATA_PATH + path;
    return new BearerMcpAuthenticator(jwtDecoder, oidc.getRest().getUserIdClaim(), metadataUrl);
  }

  @Bean
  public ServletRegistrationBean<ProtectedResourceMetadataServlet> fastiMcpResourceMetadata(FastiOidcProperties oidc,
                                                                                           FastiMcpProperties properties) {
    String path = properties.getPath();
    ProtectedResourceMetadataServlet servlet = new ProtectedResourceMetadataServlet(oidc.getRest().getIssuerUri(),
        request -> baseUrl(request) + path);
    ServletRegistrationBean<ProtectedResourceMetadataServlet> registration =
        new ServletRegistrationBean<>(servlet, METADATA_PATH + path);
    registration.setName("fastiMcpResourceMetadata");
    return registration;
  }

  /** Scheme, host, port and context path of the request, as the client addressed the server. */
  protected static String baseUrl(HttpServletRequest request) {
    String scheme = request.getScheme();
    int port = request.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    String host = request.getServerName();
    if (host.contains(":") && !host.startsWith("[")) {
      host = "[" + host + "]";
    }
    return scheme + "://" + host + (defaultPort ? "" : ":" + port) + request.getContextPath();
  }
}
