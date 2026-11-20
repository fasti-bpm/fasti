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
package org.camunda.bpm.identity.impl.ldap.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Support for the option <code>acceptUntrustedCertificates</code> of the LDAP plugin.
 *
 * <p>Untrusted certificates are accepted for the connections to the LDAP server only: the LDAP
 * client hands {@link UntrustedCertificatesSocketFactory} to JNDI with the environment property
 * {@link #SOCKET_FACTORY_PROPERTY}. The default {@link SSLContext} of the JVM, which is used by
 * every other TLS connection of the application, stays untouched.</p>
 */
public final class CertificateHelper {

  /** the JNDI environment property that names the socket factory of LDAP connections */
  public static final String SOCKET_FACTORY_PROPERTY = "java.naming.ldap.factory.socket";

  private CertificateHelper() {
  }

  /**
   * Socket factory for LDAP connections that accepts any server certificate. JNDI instantiates it
   * by name and calls {@link #getDefault()}.
   */
  public static class UntrustedCertificatesSocketFactory extends SSLSocketFactory {

    private static final UntrustedCertificatesSocketFactory INSTANCE = new UntrustedCertificatesSocketFactory();

    private final SSLSocketFactory delegate;

    public UntrustedCertificatesSocketFactory() {
      try {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(new KeyManager[0], new TrustManager[] { new AcceptAllTrustManager() }, new SecureRandom());
        delegate = sslContext.getSocketFactory();
      } catch (GeneralSecurityException ex) {
        throw new IllegalStateException("Could not create an SSL context that accepts untrusted certificates", ex);
      }
    }

    /** called by JNDI */
    public static SocketFactory getDefault() {
      return INSTANCE;
    }

    @Override
    public String[] getDefaultCipherSuites() {
      return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
      return delegate.createSocket();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
      return delegate.createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
      return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
      return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      return delegate.createSocket(address, port, localAddress, localPort);
    }
  }

  /**
   * Accepts every certificate chain. A plain {@link X509TrustManager} (not an extended one), so
   * that the host name of the server is still verified where JNDI asks for it, as before.
   */
  private static class AcceptAllTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // accepted by configuration
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // accepted by configuration
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
