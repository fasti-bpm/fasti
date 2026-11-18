/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.runlistener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.DuplicateEngineLibraryCheck;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;

public class EngineLibraryCheckListenerTest {

  protected static final String PROPERTY = DuplicateEngineLibraryCheck.CHECK_ENABLED_SYSTEM_PROPERTY;

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  protected String originalPropertyValue;

  @Before
  public void rememberProperty() {
    originalPropertyValue = System.getProperty(PROPERTY);
    System.clearProperty(PROPERTY);
  }

  @After
  public void restoreProperty() {
    if (originalPropertyValue == null) {
      System.clearProperty(PROPERTY);
    } else {
      System.setProperty(PROPERTY, originalPropertyValue);
    }
  }

  @Test
  public void shouldBeRegisteredAsApplicationListener() {
    List<ApplicationListener> listeners = SpringFactoriesLoader
        .loadFactories(ApplicationListener.class, getClass().getClassLoader());

    assertThat(listeners).hasAtLeastOneElementOfType(EngineLibraryCheckListener.class);
  }

  @Test
  public void shouldAcceptClassPathOfTheBuild() {
    assertThatCode(() -> new EngineLibraryCheckListener().onApplicationEvent(null)).doesNotThrowAnyException();
  }

  @Test
  public void shouldRejectTwoEngineLibraries() throws Exception {
    try (URLClassLoader twoLibraries = twoEngineLibraries()) {
      EngineLibraryCheckListener listener = listenerFor(twoLibraries);

      assertThatThrownBy(() -> listener.onApplicationEvent(null))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-12021")
        .hasMessageContaining("fasti-engine.jar")
        .hasMessageContaining("camunda-engine.jar");
    }
  }

  @Test
  public void shouldNotCheckIfSwitchedOff() throws Exception {
    System.setProperty(PROPERTY, "false");

    try (URLClassLoader twoLibraries = twoEngineLibraries()) {
      EngineLibraryCheckListener listener = listenerFor(twoLibraries);

      assertThatCode(() -> listener.onApplicationEvent(null)).doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldLetApplicationFailToStart() throws Exception {
    try (URLClassLoader twoLibraries = twoEngineLibraries()) {
      SpringApplication application = new SpringApplication(EmptyConfiguration.class);
      application.setWebApplicationType(WebApplicationType.NONE);
      application.setLogStartupInfo(false);
      application.addListeners(listenerFor(twoLibraries));

      assertThatThrownBy(() -> {
        try (ConfigurableApplicationContext context = application.run()) {
          // not reached: the application does not start
        }
      })
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-12021");
    }
  }

  // helper ////////////////////////////////////////////////////////////////////

  @Configuration
  public static class EmptyConfiguration {
  }

  protected EngineLibraryCheckListener listenerFor(ClassLoader engineClassLoader) {
    return new EngineLibraryCheckListener() {
      @Override
      protected ClassLoader getEngineClassLoader() {
        return engineClassLoader;
      }
    };
  }

  protected URLClassLoader twoEngineLibraries() throws IOException {
    return new URLClassLoader(new URL[] {
        engineJar("fasti-engine.jar").toURI().toURL(),
        engineJar("camunda-engine.jar").toURI().toURL()
    }, null);
  }

  /** a jar file that looks like a process engine library */
  protected File engineJar(String name) throws IOException {
    File jar = folder.newFile(name);
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
      out.putNextEntry(new JarEntry(DuplicateEngineLibraryCheck.MARKER_RESOURCE));
      out.closeEntry();
    }
    return jar;
  }

}
