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
                                         @Nullable SsoLogoutSuccessHandler ssoLogoutSuccessHandler) throws Exception {
    logger.info("Enabling Camunda Spring Security oauth2 integration");

    // PKCE also for confidential clients, in addition to state and nonce
    var authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI);
    authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

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
        .oidcLogout(c -> c.backChannel(Customizer.withDefaults()))
        .oauth2Login(c -> c
            .authorizationEndpoint(a -> a.authorizationRequestResolver(authorizationRequestResolver))
            .successHandler(new ReturnUrlAuthenticationSuccessHandler()))
        .logout(c -> c
            .logoutRequestMatcher(new SameOriginPostRequestMatcher(LOGOUT_PATH))
            .clearAuthentication(true)
            .invalidateHttpSession(true)
            .logoutSuccessHandler(finalLogoutSuccessHandler)
        )
        .oauth2Client(Customizer.withDefaults())
        .cors(AbstractHttpConfigurer::disable)
        // the web applications protect /api and /app with their own CSRF filter
        // (X-XSRF-TOKEN); the logout only accepts same-origin POST requests
        .csrf(AbstractHttpConfigurer::disable);
    // @formatter:on

    return http.build();
  }
}
