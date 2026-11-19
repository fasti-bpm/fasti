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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.OidcClaimMapper;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.RestBearerIdentityFilter;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.TenantGrantedAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jersey.autoconfigure.JerseyApplicationPath;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts {@code Authorization: Bearer <JWT>} on the REST API. The token is
 * validated here (signature, algorithm, issuer, audience, expiry); the
 * {@code ProcessEngineAuthenticationFilter} with the
 * {@link org.camunda.bpm.spring.boot.starter.security.oauth2.impl.BearerTokenAuthenticationProvider}
 * and the {@link RestBearerIdentityFilter} set the engine identity.
 * <p>
 * Every request of the REST API must be authenticated: without a valid bearer
 * token it is rejected with 401. Requests with basic credentials pass on only
 * when a {@code ProcessEngineAuthenticationFilter} is registered for the REST
 * API to check them (Run with {@code camunda.bpm.run.auth.enabled=true});
 * CORS preflight requests carry no credentials and pass on as well.
 */
@AutoConfigureOrder(CamundaSpringSecurityOAuth2AutoConfiguration.CAMUNDA_OAUTH2_ORDER - 1)
@AutoConfigureAfter(CamundaBpmAutoConfiguration.class)
@ConditionalOnBean(CamundaBpmProperties.class)
@ConditionalOnClass(JwtAuthenticationConverter.class)
@ConditionalOnProperty(prefix = FastiOidcProperties.PREFIX + ".rest", name = "bearer-enabled", havingValue = "true")
@EnableConfigurationProperties({ OAuth2Properties.class, FastiOidcProperties.class })
public class FastiRestBearerAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(FastiRestBearerAutoConfiguration.class);

  public static final int REST_FILTER_CHAIN_ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

  @Bean
  @ConditionalOnMissingBean(OidcClaimMapper.class)
  public OidcClaimMapper fastiOidcClaimMapper(OAuth2Properties oAuth2Properties, FastiOidcProperties properties) {
    return new OidcClaimMapper(oAuth2Properties, properties);
  }

  /** Makes the claim mapper of the context, possibly one of the application, the current one. */
  @Bean
  @ConditionalOnMissingBean(name = "fastiOidcClaimMapperInstaller")
  public static SmartInitializingSingleton fastiOidcClaimMapperInstaller(ObjectProvider<OidcClaimMapper> mappers) {
    return OidcClaimMapper.installer(mappers);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder fastiRestJwtDecoder(FastiOidcProperties properties) {
    return createDecoder(properties.getRest());
  }

  @Bean
  @Order(REST_FILTER_CHAIN_ORDER)
  public SecurityFilterChain fastiRestBearerFilterChain(HttpSecurity http,
                                                        JwtDecoder jwtDecoder,
                                                        OidcClaimMapper claimMapper,
                                                        FastiOidcProperties properties,
                                                        ObjectProvider<JerseyApplicationPath> applicationPath,
                                                        ObjectProvider<ProcessEngine> processEngine,
                                                        ApplicationContext context) throws Exception {
    JerseyApplicationPath path = applicationPath.getIfAvailable();
    String pattern = path != null ? path.getRelativePath("/**") : "/engine-rest/**";
    String urlMapping = path != null ? path.getUrlMapping() : "/engine-rest/*";
    // Basic credentials are let through to the engine filter only if it exists and they are
    // not switched off; otherwise a Basic request has no bearer token and gets 401 here
    boolean basicChecked = properties.getRest().isBasicEnabled() && hasEngineAuthenticationFilter(context, urlMapping);
    logger.info("Requiring bearer tokens on {}{}", pattern,
        basicChecked ? " (basic credentials are checked by the engine authentication filter)" : "");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(properties.getRest().getUserIdClaim());
    converter.setJwtGrantedAuthoritiesConverter(jwt -> authorities(jwt, claimMapper));

    // @formatter:off
    http.securityMatcher(pattern)
        .authorizeHttpRequests(c -> {
          c.requestMatchers(FastiRestBearerAutoConfiguration::isCorsPreflight).permitAll();
          if (basicChecked) {
            c.requestMatchers(FastiRestBearerAutoConfiguration::hasBasicCredentials).permitAll();
          }
          c.anyRequest().authenticated();
        })
        .addFilterBefore(new IdentityProviderUnavailableFilter(), BearerTokenAuthenticationFilter.class)
        .addFilterAfter(new RestBearerIdentityFilter(
            name -> name == null ? defaultEngine(processEngine) : ProcessEngines.getProcessEngines().get(name),
            path != null ? path.getPath() : "/engine-rest"), AuthorizationFilter.class)
        .oauth2ResourceServer(c -> c
            .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
            .authenticationEntryPoint(entryPoint(basicChecked)))
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        // stateless: no cookie authenticates a request of this chain
        .csrf(AbstractHttpConfigurer::disable);
    // @formatter:on
    return http.build();
  }

  /** Challenges for a bearer token and, where basic credentials are checked, for those too. */
  protected static AuthenticationEntryPoint entryPoint(boolean basicChecked) {
    AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    if (!basicChecked) {
      return bearer;
    }
    return (request, response, exception) -> {
      response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"default\"");
      bearer.commence(request, response, exception);
    };
  }

  protected static ProcessEngine defaultEngine(ObjectProvider<ProcessEngine> processEngine) {
    ProcessEngine engine = processEngine.getIfUnique();
    return engine != null ? engine : ProcessEngines.getDefaultProcessEngine(false);
  }

  protected static boolean isCorsPreflight(HttpServletRequest request) {
    return HttpMethod.OPTIONS.matches(request.getMethod())
        && request.getHeader(HttpHeaders.ORIGIN) != null
        && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;
  }

  protected static boolean hasBasicCredentials(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
  }

  /**
   * Whether a {@link ProcessEngineAuthenticationFilter} is registered for the
   * REST API, i.e. something checks credentials other than bearer tokens.
   */
  @SuppressWarnings("rawtypes")
  protected static boolean hasEngineAuthenticationFilter(ApplicationContext context, String urlMapping) {
    return context.getBeanProvider(FilterRegistrationBean.class).stream()
        .anyMatch(registration -> registration.getFilter() instanceof ProcessEngineAuthenticationFilter
            && registration.isEnabled()
            && registration.getUrlPatterns().contains(urlMapping));
  }

  protected static Collection<GrantedAuthority> authorities(Jwt jwt, OidcClaimMapper claimMapper) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    var claims = jwt.getClaims();
    claimMapper.groups(claims, claimMapper.getRestGroupsClaim())
        .forEach(group -> authorities.add(new SimpleGrantedAuthority(group)));
    claimMapper.tenants(claims).forEach(tenant -> authorities.add(new TenantGrantedAuthority(tenant)));
    return authorities;
  }

  public static JwtDecoder createDecoder(FastiOidcProperties.Rest rest) {
    String issuer = rest.getIssuerUri();
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalStateException(FastiOidcProperties.PREFIX + ".rest.issuer-uri must be set when bearer tokens are enabled");
    }
    List<String> audiences = rest.getAudiences().stream().filter(a -> a != null && !a.isBlank()).toList();
    if (audiences.isEmpty()) {
      throw new IllegalStateException(FastiOidcProperties.PREFIX + ".rest.audiences must name at least one audience when bearer tokens are enabled");
    }
    List<SignatureAlgorithm> algorithms = new ArrayList<>();
    for (String name : rest.getJwsAlgorithms()) {
      SignatureAlgorithm algorithm = SignatureAlgorithm.from(name);
      if (algorithm == null) {
        // only asymmetric algorithms: a shared secret would let every client mint tokens
        throw new IllegalStateException("Unsupported JWS algorithm: " + name);
      }
      algorithms.add(algorithm);
    }
    if (algorithms.isEmpty()) {
      throw new IllegalStateException(FastiOidcProperties.PREFIX + ".rest.jws-algorithms must not be empty");
    }

    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        // the default validators only check exp if it is present
        new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
        new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
            aud -> aud != null && aud.stream().anyMatch(audiences::contains)));

    String jwkSetUri = rest.getJwkSetUri();
    return new IdentityProviderAwareJwtDecoder(new SupplierJwtDecoder(() -> {
      NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = jwkSetUri != null && !jwkSetUri.isBlank()
          ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
          : NimbusJwtDecoder.withIssuerLocation(issuer);
      builder.jwsAlgorithms(set -> {
        set.clear();
        set.addAll(algorithms);
      });
      NimbusJwtDecoder decoder = builder.build();
      decoder.setJwtValidator(validator);
      return decoder;
    }));
  }

  /**
   * Thrown when a bearer token cannot be checked because the identity provider
   * (discovery document or key set) cannot be reached. It is not an
   * authentication failure: the request is answered with 503.
   */
  public static class IdentityProviderUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IdentityProviderUnavailableException(Throwable cause) {
      super("The identity provider cannot be reached", cause);
    }
  }

  /**
   * Tells failures to reach the identity provider apart from invalid tokens:
   * an invalid token stays a {@link BadJwtException} (401), anything else the
   * decoder throws becomes an {@link IdentityProviderUnavailableException}.
   */
  public static class IdentityProviderAwareJwtDecoder implements JwtDecoder {
    protected final JwtDecoder delegate;

    public IdentityProviderAwareJwtDecoder(JwtDecoder delegate) {
      this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) {
      try {
        return delegate.decode(token);
      } catch (BadJwtException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new IdentityProviderUnavailableException(e);
      }
    }
  }

  /** Answers 503 without details when the identity provider cannot be reached. */
  protected static class IdentityProviderUnavailableFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      try {
        chain.doFilter(request, response);
      } catch (IdentityProviderUnavailableException e) {
        handle(response, e);
      } catch (ServletException | RuntimeException e) {
        IdentityProviderUnavailableException unavailable = findUnavailable(e);
        if (unavailable == null) {
          throw e;
        }
        handle(response, unavailable);
      }
    }

    protected static IdentityProviderUnavailableException findUnavailable(Throwable e) {
      for (Throwable t = e; t != null; t = t.getCause()) {
        if (t instanceof IdentityProviderUnavailableException unavailable) {
          return unavailable;
        }
      }
      return null;
    }

    protected void handle(HttpServletResponse response, IdentityProviderUnavailableException e) throws IOException {
      FastiRestBearerAutoConfiguration.logger.warn("Bearer token not checked: {}", String.valueOf(e.getCause()));
      if (response.isCommitted()) {
        return;
      }
      response.resetBuffer();
      response.setHeader(HttpHeaders.RETRY_AFTER, "30");
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"temporarily_unavailable\"");
      response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
  }
}
