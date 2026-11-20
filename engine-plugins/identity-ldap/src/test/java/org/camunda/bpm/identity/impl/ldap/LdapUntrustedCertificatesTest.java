/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.identity.impl.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.util.Hashtable;

import javax.naming.Context;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.identity.impl.ldap.plugin.LdapIdentityProviderPlugin;
import org.camunda.bpm.identity.impl.ldap.util.CertificateHelper;
import org.camunda.bpm.identity.impl.ldap.util.CertificateHelper.UntrustedCertificatesSocketFactory;
import org.junit.Test;

/**
 * The option <code>acceptUntrustedCertificates</code> applies to the connections to the LDAP
 * server only. It used to replace the default SSL context of the JVM, so that every other TLS
 * connection of the application (identity provider, connectors, mail) accepted any certificate
 * as well.
 */
public class LdapUntrustedCertificatesTest {

  @Test
  public void shouldNotChangeDefaultSslContextOfJvm() throws Exception {
    // given
    SSLContext defaultContext = SSLContext.getDefault();
    LdapIdentityProviderPlugin plugin = new LdapIdentityProviderPlugin();
    plugin.setAcceptUntrustedCertificates(true);

    // when
    plugin.preInit(new StandaloneInMemProcessEngineConfiguration());

    // then
    assertThat(SSLContext.getDefault()).isSameAs(defaultContext);
  }

  @Test
  public void shouldUseOwnSocketFactoryForLdapConnections() {
    // given
    LdapIdentityProviderPlugin configuration = new LdapIdentityProviderPlugin();
    configuration.setServerUrl("ldaps://ldap.example.com:636");
    configuration.setUseSsl(true);
    configuration.setAcceptUntrustedCertificates(true);

    // when
    Hashtable<String, String> env = new LdapClient(configuration).createEnvironment("cn=manager", "secret");

    // then
    assertThat(env).containsEntry(CertificateHelper.SOCKET_FACTORY_PROPERTY,
        UntrustedCertificatesSocketFactory.class.getName());
  }

  @Test
  public void shouldUseOwnSocketFactoryForLdapsUrl() {
    // given
    LdapConfiguration configuration = new LdapConfiguration();
    configuration.setServerUrl("ldap://ldap1.example.com:389 LDAPS://ldap2.example.com:636");
    configuration.setAcceptUntrustedCertificates(true);

    // when
    Hashtable<String, String> env = new LdapClient(configuration).createEnvironment("cn=manager", "secret");

    // then
    assertThat(env).containsEntry(CertificateHelper.SOCKET_FACTORY_PROPERTY,
        UntrustedCertificatesSocketFactory.class.getName());
  }

  @Test
  public void shouldUseOwnSocketFactoryForSecurityProtocolOfContextProperties() {
    // given
    LdapConfiguration configuration = new LdapConfiguration();
    configuration.setServerUrl("ldap://ldap.example.com:636");
    configuration.setAcceptUntrustedCertificates(true);
    configuration.getContextProperties().put(Context.SECURITY_PROTOCOL, "ssl");

    // when
    Hashtable<String, String> env = new LdapClient(configuration).createEnvironment("cn=manager", "secret");

    // then
    assertThat(env).containsEntry(CertificateHelper.SOCKET_FACTORY_PROPERTY,
        UntrustedCertificatesSocketFactory.class.getName());
  }

  @Test
  public void shouldNotUseSslSocketsForPlainConnection() {
    // given a plain connection, which must keep working with the option switched on
    LdapConfiguration configuration = new LdapConfiguration();
    configuration.setServerUrl("ldap://ldap.example.com:389");
    configuration.setAcceptUntrustedCertificates(true);

    // when
    Hashtable<String, String> env = new LdapClient(configuration).createEnvironment("cn=manager", "secret");

    // then
    assertThat(env).doesNotContainKey(CertificateHelper.SOCKET_FACTORY_PROPERTY);
  }

  @Test
  public void shouldUseDefaultSocketFactoryByDefault() {
    // given
    LdapConfiguration configuration = new LdapConfiguration();
    configuration.setServerUrl("ldaps://ldap.example.com:636");
    configuration.setUseSsl(true);

    // when
    Hashtable<String, String> env = new LdapClient(configuration).createEnvironment("cn=manager", "secret");

    // then
    assertThat(configuration.isAcceptUntrustedCertificates()).isFalse();
    assertThat(env).doesNotContainKey(CertificateHelper.SOCKET_FACTORY_PROPERTY);
  }

  @Test
  public void shouldLetContextPropertiesOverrideSocketFactory() {
    // given
    LdapConfiguration configuration = new LdapConfiguration();
    configuration.setServerUrl("ldaps://ldap.example.com:636");
    configuration.setAcceptUntrustedCertificates(true);
    configuration.getContextProperties().put(CertificateHelper.SOCKET_FACTORY_PROPERTY, "com.example.MySocketFactory");

    // when
    Hashtable<String, String> env = new LdapClient(configuration).createEnvironment("cn=manager", "secret");

    // then
    assertThat(env).containsEntry(CertificateHelper.SOCKET_FACTORY_PROPERTY, "com.example.MySocketFactory");
  }

  @Test
  public void shouldCreateSslSocketsForJndi() throws Exception {
    // JNDI instantiates the factory by name through its static getDefault()
    Object factory = Class.forName(UntrustedCertificatesSocketFactory.class.getName())
        .getMethod("getDefault").invoke(null);

    assertThat(factory).isInstanceOf(UntrustedCertificatesSocketFactory.class);
    try (Socket socket = ((UntrustedCertificatesSocketFactory) factory).createSocket()) {
      assertThat(socket).isInstanceOf(SSLSocket.class);
    }
  }
}
