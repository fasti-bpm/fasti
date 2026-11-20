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
package org.camunda.bpm.engine.rest.util.container;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import org.camunda.bpm.engine.rest.spi.ProcessEngineProvider;
import org.camunda.bpm.engine.rest.spi.impl.MockedProcessEngineProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystemBase;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.PomlessResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;


public abstract class TomcatServerBootstrap extends AbstractServerBootstrap {

  /** set by the Surefire configuration to the version of mockito-core that the build uses */
  protected static final String MOCKITO_VERSION_PROPERTY = "mockitoVersion";
  /** set by the Shrinkwrap Maven plugin to the settings.xml of the Maven build */
  protected static final String USER_SETTINGS_PROPERTY = "maven.execution.user-settings";

  private Tomcat tomcat;
  private String workingDir;
  private String webXmlPath;

  public TomcatServerBootstrap(String webXmlPath) {
    this.webXmlPath = webXmlPath;
  }

  @Override
  public void stop() {
    try {
      try {
        tomcat.stop();
      } catch (Exception e) {
        Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to stop tomcat instance", e);
      }

      try {
        tomcat.destroy();
      } catch (Exception e) {
        Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to destroy instance", e);
      }

      tomcat = null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void startServer(int startUpRetries) {
    tomcat = new Tomcat();
    tomcat.setBaseDir(getWorkingDir());
    // Tomcat 9 no longer creates the default HTTP connector implicitly on start
    tomcat.getConnector();

    tomcat.getHost().setAppBase(getWorkingDir());
    tomcat.getHost().setAutoDeploy(true);
    tomcat.getHost().setDeployOnStartup(true);

    String contextPath = "/" + getContextPath();

    MavenResolverSystemBase<PomEquippedResolveStage, PomlessResolveStage, MavenStrategyStage, MavenFormatStage> resolver =
        createResolver();

    WebArchive wa = ShrinkWrap.create(WebArchive.class, "rest-test.war").setWebXML(webXmlPath)
        .addAsLibraries(resolver.addDependencies(
            MavenDependencies.createDependency("org.mockito:mockito-core:" + getMockitoVersion(), ScopeType.TEST, false,
            MavenDependencies.createExclusion("org.hamcrest:hamcrest-core"))).resolve()
              .withTransitivity().asFile())

        .addAsServiceProvider(ProcessEngineProvider.class, MockedProcessEngineProvider.class)
        .add(new ClassLoaderAsset("runtime/tomcat/context.xml"), "META-INF/context.xml")
        .addPackages(true, "org.camunda.bpm.engine.rest");

    addRuntimeSpecificLibraries(wa, resolver);
    wa.setWebXML(webXmlPath);

    String webAppPath = getWorkingDir() + "/" + getContextPath() + ".war";

    wa.as(ZipExporter.class).exportTo(new File(webAppPath), true);

    tomcat.addWebapp(tomcat.getHost(), contextPath, webAppPath);

    try {
      // the port is chosen right before the start, after the web application has been built
      tomcat.getConnector().setPort(reservePort());
      tomcat.start();
      if (LifecycleState.FAILED.equals(tomcat.getConnector().getState())) {
        stop();
        try {
          Thread.sleep(1500L);
        } catch (Exception ex) {
        }
        startServer(--startUpRetries);
      }
    } catch (LifecycleException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a resolver for the libraries of the test web application that does not read the
   * pom.xml of this module: the libraries are requested with their versions (see below).
   * The settings.xml of the Maven build is used if the Shrinkwrap Maven plugin provides it
   * (see pom.xml for the plugin definition); this is needed where settings.xml is not in the
   * default location.
   */
  protected MavenResolverSystemBase<PomEquippedResolveStage, PomlessResolveStage, MavenStrategyStage, MavenFormatStage> createResolver() {
    ConfigurableMavenResolverSystem resolver = Maven.configureResolver().useLegacyLocalRepo(true);

    String userSettings = System.getProperty(USER_SETTINGS_PROPERTY);
    if (userSettings != null && new File(userSettings).canRead()) {
      return resolver.fromFile(userSettings);
    }
    return resolver;
  }

  protected String getMockitoVersion() {
    String mockitoVersion = System.getProperty(MOCKITO_VERSION_PROPERTY);
    if (mockitoVersion == null || mockitoVersion.isEmpty()) {
      throw new IllegalStateException("System property '" + MOCKITO_VERSION_PROPERTY + "' must be set to the version of "
          + "mockito-core that is added to the test web application (see the Surefire configuration in pom.xml)");
    }
    return mockitoVersion;
  }

  protected abstract void addRuntimeSpecificLibraries(WebArchive wa,
      MavenResolverSystemBase<PomEquippedResolveStage, PomlessResolveStage, MavenStrategyStage, MavenFormatStage> resolver);

  protected String getContextPath() {
    return "rest-test";
  }

  protected String getWorkingDir() {
    if (workingDir == null) {
      workingDir = System.getProperty("java.io.tmpdir");
    }
    return workingDir;
  }

  protected void setWorkingDir(String workingDir) {
    this.workingDir = workingDir;
  }

  /*
   * Must not read the pom.xml of this module with Shrinkwrap (loadPomFromFile, configureViaPlugin)
   * or else some Maven builds can fail. In particular, a Maven build will fail in the following
   * circumstances:
   *
   * - The BOM is part of the Maven reactor (e.g. if we build the root pom)
   * - The Maven build does not install its modules (e.g. we run mvn clean verify)
   * - The BOM is neither in the local repository nor in a remote repository
   *
   * Why?
   *
   * - The BOM is imported in the dependencyManagement section of this pom (via the parent pom)
   * - As the BOM is part of the reactor, it will not be fetched from remote by the Maven build
   * - As we do not run "install", Maven does not put the BOM into the local repository
   * - Shrinkwrap does not see the reactor and will not be able to resolve the BOM then; note:
   *   this is different if a pom is referenced as a parent; in that case, Shrinkwrap will
   *   navigate the relativePaths towards the local pom
   *
   * Solution
   *
   * - The pom.xml is only needed to look up versions of the libraries. The versions are
   *   passed to the tests as system properties instead and the libraries, which are
   *   third-party libraries from a repository, are resolved by their complete coordinates.
   */
}
