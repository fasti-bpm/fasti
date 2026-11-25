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
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.camunda.bpm.engine.spring.SpringProcessEngineServicesConfiguration;
import org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.spring.boot.starter.property.WebappProperty;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.AuthorizeTokenFilter;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.JsonAwareRedirectStrategy;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.OAuth2AuthenticationProvider;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.OAuth2ContainerBasedAuthenticationFilter;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.OAuth2GrantedAuthoritiesMapper;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.OAuth2IdentityProviderPlugin;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.OidcClaimMapper;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.ReturnUrlAuthenticationSuccessHandler;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.SameOriginPostRequestMatcher;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.SsoLoginSupportFilter;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.SsoLogoutSuccessHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.ConditionalOnOAuth2ClientRegistrationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@AutoConfigureOrder(CamundaSpringSecurityOAuth2AutoConfiguration.CAMUNDA_OAUTH2_ORDER)
@AutoConfigureAfter({ CamundaBpmAutoConfiguration.class, SpringProcessEngineServicesConfiguration.class })
@ConditionalOnBean(CamundaBpmProperties.class)
@ConditionalOnOAuth2ClientRegistrationProperties
@EnableConfigurationProperties({ OAuth2Properties.class, FastiOidcProperties.class })
public class CamundaSpringSecurityOAuth2AutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(CamundaSpringSecurityOAuth2AutoConfiguration.class);
  public static final int CAMUNDA_OAUTH2_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;
  public static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";
  public static final String LOGOUT_PATH = "/logout";

  private final OAuth2Properties oAuth2Properties;
  private final FastiOidcProperties fastiOidcProperties;
  private final String webappPath;

  public CamundaSpringSecurityOAuth2AutoConfiguration(CamundaBpmProperties properties,
                                                      OAuth2Properties oAuth2Properties,
                                                      FastiOidcProperties fastiOidcProperties) {
    this.oAuth2Properties = oAuth2Properties;
    this.fastiOidcProperties = fastiOidcProperties;
    WebappProperty webapp = properties.getWebapp();
    this.webappPath = webapp.getApplicationPath();
  }

  @Bean
  public FilterRegistrationBean<?> webappAuthenticationFilter() {
    FilterRegistrationBean<Filter> filterRegistration = new FilterRegistrationBean<>();
    filterRegistration.setName("Container Based Authentication Filter");
    filterRegistration.setFilter(new OAuth2ContainerBasedAuthenticationFilter());
    filterRegistration.setInitParameters(Map.of(
        ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM, OAuth2AuthenticationProvider.class.getName()));
    // make sure the filter is registered after the Spring Security Filter Chain
    filterRegistration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
    filterRegistration.addUrlPatterns(webappPath + "/app/*", webappPath + "/api/*");
    filterRegistration.setDispatcherTypes(DispatcherType.REQUEST);
    return filterRegistration;
  }

  @Bean
  @ConditionalOnMissingBean(OidcClaimMapper.class)
  public OidcClaimMapper fastiOidcClaimMapper() {
    return new OidcClaimMapper(oAuth2Properties, fastiOidcProperties);
  }

  /** Makes the claim mapper of the context, possibly one of the application, the current one. */
  @Bean
  @ConditionalOnMissingBean(name = "fastiOidcClaimMapperInstaller")
  public static SmartInitializingSingleton fastiOidcClaimMapperInstaller(ObjectProvider<OidcClaimMapper> mappers) {
    return OidcClaimMapper.installer(mappers);
  }

  @Bean
  public FilterRegistrationBean<SsoLoginSupportFilter> ssoLoginSupportFilter(ClientRegistrationRepository registrations) {
    ClientRegistration registration = loginRegistration(registrations);
    String label = fastiOidcProperties.getLoginLabel();
    if (label == null || label.isBlank()) {
      label = registration.getClientName();
    }
    FilterRegistrationBean<SsoLoginSupportFilter> filterRegistration = new FilterRegistrationBean<>(
        new SsoLoginSupportFilter(webappPath, registration.getRegistrationId(), label));
    filterRegistration.setName("SSO Login Support Filter");
    filterRegistration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);
    filterRegistration.addUrlPatterns(webappPath + "/sso/*");
    filterRegistration.setDispatcherTypes(DispatcherType.REQUEST);
    return filterRegistration;
  }

  protected ClientRegistration loginRegistration(ClientRegistrationRepository registrations) {
    String id = fastiOidcProperties.getRegistrationId();
    if (id != null && !id.isBlank()) {
      ClientRegistration registration = registrations.findByRegistrationId(id);
      if (registration == null) {
        throw new IllegalStateException("No client registration '" + id + "' ("
            + FastiOidcProperties.PREFIX + ".registration-id)");
      }
      return registration;
    }
    List<ClientRegistration> all = new ArrayList<>();
    if (registrations instanceof Iterable<?> iterable) {
      iterable.forEach(r -> all.add((ClientRegistration) r));
    }
    if (all.isEmpty()) {
      throw new IllegalStateException("Set " + FastiOidcProperties.PREFIX
          + ".registration-id to choose the client registration of the login page");
    }
    if (all.size() > 1) {
      logger.warn("Several client registrations configured, the login page uses '{}'; set {}.registration-id to choose another",
          all.get(0).getRegistrationId(), FastiOidcProperties.PREFIX);
    }
    return all.get(0);
  }

  @Bean
  @ConditionalOnProperty(name = "identity-provider.enabled", havingValue = "true", prefix = OAuth2Properties.PREFIX, matchIfMissing = true)
  public OAuth2IdentityProviderPlugin identityProviderPlugin() {
    logger.debug("Registering OAuth2IdentityProviderPlugin");
    return new OAuth2IdentityProviderPlugin();
  }

  /**
   * Turns the initial setup of the web applications off while the engine reads
   * users and groups from the token of the session. The setup asks for the
   * first administrator when no member of {@code camunda-admin} is found, which
   * with this identity provider means: when the signed-in user is no
   * administrator. Every other user would be sent to a setup page that cannot
   * create a user. Administrators come from the claims
   * ({@code fasti.security.oidc.groups.admin}) instead.
   */
  @Bean
  @ConditionalOnProperty(name = "identity-provider.enabled", havingValue = "true", prefix = OAuth2Properties.PREFIX, matchIfMissing = true)
  public static BeanPostProcessor fastiOidcInitialSetupDisabler() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof CamundaBpmProperties properties) {
          disableInitialSetup(properties);
        }
        return bean;
      }
    };
  }

  protected static void disableInitialSetup(CamundaBpmProperties properties) {
    WebappProperty webapp = properties.getWebapp();
    if (webapp != null && webapp.isInitialSetupEnabled()) {
      logger.info("Initial setup of the web applications is off: users and groups come from the identity provider");
      webapp.setInitialSetupEnabled(false);
    }
  }

  @Bean
  protected GrantedAuthoritiesMapper grantedAuthoritiesMapper(OidcClaimMapper fastiOidcClaimMapper) {
    logger.debug("Registering OAuth2GrantedAuthoritiesMapper");
    return new OAuth2GrantedAuthoritiesMapper(fastiOidcClaimMapper);
  }

  @Bean
  @ConditionalOnProperty(name = "sso-logout.enabled", havingValue = "true", prefix = OAuth2Properties.PREFIX)
  protected SsoLogoutSuccessHandler ssoLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
    logger.debug("Registering SsoLogoutSuccessHandler");
    return new SsoLogoutSuccessHandler(clientRegistrationRepository, oAuth2Properties);
  }

  @Bean
  protected AuthorizeTokenFilter authorizeTokenFilter(OAuth2AuthorizedClientManager clientManager) {
    logger.debug("Registering AuthorizeTokenFilter");
    return new AuthorizeTokenFilter(clientManager);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http,
                                         AuthorizeTokenFilter authorizeTokenFilter,
                                         ClientRegistrationRepository clientRegistrationRepository,
                                         @Nullable SsoLogoutSuccessHandler ssoLogoutSuccessHandler,
                                         Environment environment) throws Exception {
    logger.info("Enabling Camunda Spring Security oauth2 integration");

    // PKCE also for confidential clients, in addition to state and nonce
    var authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI);
    authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    // only the registration of the login page signs users in: the user id is the bare value of a
    // claim, so another registration (another issuer) could otherwise sign in as any user
    String loginRegistrationId = loginRegistration(clientRegistrationRepository).getRegistrationId();
    OAuth2AuthorizationRequestResolver loginOnlyResolver = new LoginRegistrationOnlyResolver(
        authorizationRequestResolver, loginRegistrationId);

    LogoutSuccessHandler logoutSuccessHandler = ssoLogoutSuccessHandler;
    if (logoutSuccessHandler == null) {
      var simpleHandler = new SimpleUrlLogoutSuccessHandler();
      simpleHandler.setDefaultTargetUrl("/");
      simpleHandler.setRedirectStrategy(new JsonAwareRedirectStrategy());
      logoutSuccessHandler = simpleHandler;
    }
    LogoutSuccessHandler finalLogoutSuccessHandler = logoutSuccessHandler;

    // @formatter:off
    http.authorizeHttpRequests(c -> c
            // the new web application shows its own login page; its data is below /api
            .requestMatchers(webappPath + "/app/next/**").permitAll()
            .requestMatchers(webappPath + "/app/**").authenticated()
            .requestMatchers(webappPath + "/api/**").authenticated()
            .anyRequest().permitAll()
        )
        // scripts get 401 instead of a redirect to the identity provider they cannot follow
        .exceptionHandling(c -> c.defaultAuthenticationEntryPointFor(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
            PathPatternRequestMatcher.withDefaults().matcher(webappPath + "/api/**")))
        .addFilterAfter(authorizeTokenFilter, OAuth2AuthorizationRequestRedirectFilter.class)
        .anonymous(AbstractHttpConfigurer::disable)
        .oidcLogout(c -> c.backChannel(b -> b.logoutUri(internalLogoutUri(environment,
            fastiOidcProperties.getBackChannelLogoutUri()))))
        .oauth2Login(c -> c
            .authorizationEndpoint(a -> a.authorizationRequestResolver(loginOnlyResolver))
            // a second line of defence: a login that comes back from another registration
            // (for example started by the OAuth2 client filter) is refused
            .userInfoEndpoint(u -> u
                .oidcUserService(loginRegistrationOnly(new OidcUserService(), loginRegistrationId))
                .userService(loginRegistrationOnly(new DefaultOAuth2UserService(), loginRegistrationId)))
            .successHandler(new ReturnUrlAuthenticationSuccessHandler()))
        .logout(c -> c
            .logoutRequestMatcher(new SameOriginPostRequestMatcher(LOGOUT_PATH))
            .clearAuthentication(true)
            .invalidateHttpSession(true)
            .logoutSuccessHandler(finalLogoutSuccessHandler)
        )
        .oauth2Client(c -> c.authorizationCodeGrant(g -> g.authorizationRequestResolver(loginOnlyResolver)))
        .cors(AbstractHttpConfigurer::disable)
        // the web applications protect /api and /app with their own CSRF filter
        // (X-XSRF-TOKEN); the logout only accepts same-origin POST requests
        .csrf(AbstractHttpConfigurer::disable);
    // @formatter:on

    return http.build();
  }

  /**
   * The URI the back-channel logout posts to for each session it ends. Spring Security uses the
   * base URL of the incoming logout request, which a container often cannot reach (published
   * port, name of the proxy in front); the local connector is always reachable.
   */
  protected static String internalLogoutUri(Environment environment, String configured) {
    if (configured != null && !configured.isBlank()) {
      return configured.trim();
    }
    String port = environment.getProperty("server.port", "8080").trim();
    if ("0".equals(port) || "-1".equals(port) || tlsConfigured(environment)) {
      // a random port is unknown here; with TLS the certificate rarely names the local
      // address: keep the address of the incoming request (fasti.security.oidc.back-channel-logout-uri
      // sets another one)
      return "{baseUrl}" + LOGOUT_PATH;
    }
    String address = environment.getProperty("server.address", "").trim();
    String host = address.isEmpty() || "0.0.0.0".equals(address) || "::".equals(address) ? "localhost"
        : address.contains(":") ? "[" + address + "]" : address;
    String contextPath = environment.getProperty("server.servlet.context-path", "");
    return "http://" + host + ":" + port + contextPath + LOGOUT_PATH;
  }

  /** Whether the server connector uses TLS: Spring Boot turns it on as soon as a key store or bundle is set. */
  protected static boolean tlsConfigured(Environment environment) {
    if ("false".equalsIgnoreCase(environment.getProperty("server.ssl.enabled", "").trim())) {
      return false;
    }
    return "true".equalsIgnoreCase(environment.getProperty("server.ssl.enabled", "").trim())
        || hasText(environment.getProperty("server.ssl.key-store"))
        || hasText(environment.getProperty("server.ssl.bundle"))
        || hasText(environment.getProperty("server.ssl.certificate"));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  /** Refuses a user that signed in through another than the login registration. */
  static <R extends OAuth2UserRequest, U extends OAuth2User> OAuth2UserService<R, U> loginRegistrationOnly(
      OAuth2UserService<R, U> delegate, String registrationId) {
    return request -> {
      String id = request.getClientRegistration().getRegistrationId();
      if (!registrationId.equals(id)) {
        logger.warn("Refused a login through client registration '{}': only '{}' signs users in", id, registrationId);
        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client_registration",
            "This client registration does not sign users in", null));
      }
      return delegate.loadUser(request);
    };
  }

  /** Resolves authorization requests of the login registration only. */
  static final class LoginRegistrationOnlyResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final String registrationId;

    LoginRegistrationOnlyResolver(OAuth2AuthorizationRequestResolver delegate, String registrationId) {
      this.delegate = delegate;
      this.registrationId = registrationId;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
      return allowed(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
      return registrationId.equals(clientRegistrationId) ? delegate.resolve(request, clientRegistrationId) : null;
    }

    private OAuth2AuthorizationRequest allowed(OAuth2AuthorizationRequest request) {
      if (request == null) {
        return null;
      }
      Object id = request.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
      if (!registrationId.equals(id)) {
        logger.warn("Refused a login through client registration '{}': only '{}' signs users in ({}.registration-id)",
            id, registrationId, FastiOidcProperties.PREFIX);
        return null;
      }
      return request;
    }
  }
}
