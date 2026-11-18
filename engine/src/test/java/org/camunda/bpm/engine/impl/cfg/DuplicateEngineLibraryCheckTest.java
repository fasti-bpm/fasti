/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.cfg;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DuplicateEngineLibraryCheckTest {

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

  // one library ///////////////////////////////////////////////////////////////

  @Test
  public void shouldAcceptClassPathOfTheBuild() {
    // the engine classes of this module are present exactly once
    assertThatCode(() -> DuplicateEngineLibraryCheck.check()).doesNotThrowAnyException();
    assertThat(DuplicateEngineLibraryCheck.findEngineLibraries(DuplicateEngineLibraryCheck.class.getClassLoader()))
      .hasSize(1);
  }

  @Test
  public void shouldAcceptSingleDirectory() throws Exception {
    File library = engineDirectory("classes");

    try (URLClassLoader classLoader = classLoader(null, library)) {
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(classLoader)).doesNotThrowAnyException();
      assertThat(DuplicateEngineLibraryCheck.findEngineLibraries(classLoader))
        .containsExactly(library.toPath().toRealPath().toString());
    }
  }

  @Test
  public void shouldAcceptSingleJar() throws Exception {
    File library = engineJar("fasti-engine.jar");

    try (URLClassLoader classLoader = classLoader(null, library)) {
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(classLoader)).doesNotThrowAnyException();
      assertThat(DuplicateEngineLibraryCheck.findEngineLibraries(classLoader))
        .containsExactly(library.toPath().toRealPath().toString());
    }
  }

  @Test
  public void shouldAcceptClassPathWithoutEngine() throws Exception {
    try (URLClassLoader classLoader = classLoader(null, folder.newFolder("empty"))) {
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(classLoader)).doesNotThrowAnyException();
      assertThat(DuplicateEngineLibraryCheck.findEngineLibraries(classLoader)).isEmpty();
    }
  }

  @Test
  public void shouldAcceptBootstrapClassLoader() {
    assertThatCode(() -> DuplicateEngineLibraryCheck.check(null)).doesNotThrowAnyException();
    assertThat(DuplicateEngineLibraryCheck.findEngineLibraries(null)).isEmpty();
  }

  // the same library more than once ///////////////////////////////////////////

  @Test
  public void shouldCountLibraryThatIsListedTwiceOnce() throws Exception {
    File library = engineJar("fasti-engine.jar");

    try (URLClassLoader classLoader = classLoader(null, library, library)) {
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(classLoader)).doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldCountLibraryWithDifferentSpellingsOnce() throws Exception {
    File library = engineJar("fasti-engine.jar");
    String url = library.toURI().toString();
    String otherSpelling = url.substring(0, url.lastIndexOf('/')) + "/." + url.substring(url.lastIndexOf('/'));

    try (URLClassLoader classLoader = new URLClassLoader(new URL[] { new URL(url), new URL(otherSpelling) }, null)) {
      assertThat(classLoader.findResources(DuplicateEngineLibraryCheck.MARKER_RESOURCE).hasMoreElements()).isTrue();
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(classLoader)).doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldNotCountLibraryOfParentClassLoaderTwice() throws Exception {
    // parent-first delegation: the child reports the library of its parent, too
    File library = engineJar("fasti-engine.jar");

    try (URLClassLoader parent = classLoader(null, library);
         URLClassLoader child = classLoader(parent, folder.newFolder("application"))) {
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(child)).doesNotThrowAnyException();
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(parent)).doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldAcceptOwnLibraryNextToLibraryOfParentClassLoader() throws Exception {
    // e.g. a web application with an embedded engine on a server with a shared engine
    File sharedLibrary = engineJar("shared-engine.jar");
    File embeddedLibrary = engineJar("embedded-engine.jar");

    try (URLClassLoader parent = classLoader(null, sharedLibrary);
         URLClassLoader child = classLoader(parent, embeddedLibrary)) {
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(child)).doesNotThrowAnyException();
      assertThat(DuplicateEngineLibraryCheck.findEngineLibraries(child))
        .containsExactly(embeddedLibrary.toPath().toRealPath().toString());
    }
  }

  // more than one library /////////////////////////////////////////////////////

  @Test
  public void shouldRejectTwoJars() throws Exception {
    File firstLibrary = engineJar("fasti-engine.jar");
    File secondLibrary = engineJar("camunda-engine.jar");

    try (URLClassLoader classLoader = classLoader(null, firstLibrary, secondLibrary)) {
      assertThatThrownBy(() -> DuplicateEngineLibraryCheck.check(classLoader))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-12021")
        .hasMessageContaining("2 libraries")
        .hasMessageContaining(firstLibrary.toPath().toRealPath().toString())
        .hasMessageContaining(secondLibrary.toPath().toRealPath().toString())
        .hasMessageContaining(PROPERTY);
    }
  }

  @Test
  public void shouldRejectJarAndDirectory() throws Exception {
    File firstLibrary = engineDirectory("classes");
    File secondLibrary = engineJar("camunda-engine.jar");

    try (URLClassLoader classLoader = classLoader(null, firstLibrary, secondLibrary)) {
      List<String> libraries = DuplicateEngineLibraryCheck.findEngineLibraries(classLoader);
      assertThat(libraries).containsExactly(
          firstLibrary.toPath().toRealPath().toString(),
          secondLibrary.toPath().toRealPath().toString());

      assertThatThrownBy(() -> DuplicateEngineLibraryCheck.check(classLoader))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-12021");
    }
  }

  @Test
  public void shouldRejectTwoLibrariesBelowParentClassLoader() throws Exception {
    File sharedLibrary = engineJar("shared-engine.jar");
    File firstLibrary = engineJar("fasti-engine.jar");
    File secondLibrary = engineJar("camunda-engine.jar");

    try (URLClassLoader parent = classLoader(null, sharedLibrary);
         URLClassLoader child = classLoader(parent, firstLibrary, secondLibrary)) {
      assertThatThrownBy(() -> DuplicateEngineLibraryCheck.check(child))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("2 libraries")
        .hasMessageContaining(firstLibrary.getName())
        .hasMessageContaining(secondLibrary.getName())
        .hasMessageNotContaining(sharedLibrary.getName());
    }
  }

  // switching the check off ///////////////////////////////////////////////////

  @Test
  public void shouldNotCheckIfSwitchedOff() throws Exception {
    System.setProperty(PROPERTY, "false");

    try (URLClassLoader classLoader = classLoader(null, engineJar("fasti-engine.jar"), engineJar("camunda-engine.jar"))) {
      assertThat(DuplicateEngineLibraryCheck.isCheckEnabled()).isFalse();
      assertThatCode(() -> DuplicateEngineLibraryCheck.check(classLoader)).doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldCheckIfPropertyHasAnotherValue() throws Exception {
    System.setProperty(PROPERTY, "true");

    try (URLClassLoader classLoader = classLoader(null, engineJar("fasti-engine.jar"), engineJar("camunda-engine.jar"))) {
      assertThat(DuplicateEngineLibraryCheck.isCheckEnabled()).isTrue();
      assertThatThrownBy(() -> DuplicateEngineLibraryCheck.check(classLoader))
        .isInstanceOf(ProcessEngineException.class);
    }
  }

  // engine startup ////////////////////////////////////////////////////////////

  @Test
  public void shouldFailToBuildProcessEngine() throws Exception {
    try (URLClassLoader twoLibraries = classLoader(null, engineJar("fasti-engine.jar"), engineJar("camunda-engine.jar"))) {
      ProcessEngineConfigurationImpl configuration = new StandaloneInMemProcessEngineConfiguration() {
        @Override
        protected void checkEngineLibraries() {
          // "classLoader" is a field of the configuration
          DuplicateEngineLibraryCheck.check(twoLibraries);
        }
      };
      configuration.setProcessEngineName("duplicateEngineLibraryCheckTest");

      assertThatThrownBy(() -> configuration.buildProcessEngine())
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-12021");

      // the check runs before anything else is set up
      assertThat(configuration.getDataSource()).isNull();
      assertThat(configuration.getCommandExecutorTxRequired()).isNull();
    }
  }

  // helper ////////////////////////////////////////////////////////////////////

  protected URLClassLoader classLoader(ClassLoader parent, File... classPath) throws IOException {
    URL[] urls = new URL[classPath.length];
    for (int i = 0; i < classPath.length; i++) {
      urls[i] = classPath[i].toURI().toURL();
    }
    return new URLClassLoader(urls, parent);
  }

  /** a directory that looks like the compiled classes of a process engine */
  protected File engineDirectory(String name) throws IOException {
    File directory = folder.newFolder(name);
    File marker = new File(directory, DuplicateEngineLibraryCheck.MARKER_RESOURCE);
    assertThat(marker.getParentFile().mkdirs()).isTrue();
    assertThat(marker.createNewFile()).isTrue();
    return directory;
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
