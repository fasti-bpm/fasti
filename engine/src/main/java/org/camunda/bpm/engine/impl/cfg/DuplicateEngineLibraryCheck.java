/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.cfg;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;

/**
 * <p>Checks on startup that the classes of the process engine are present only
 * once on the class path.</p>
 *
 * <p>The process engine exists under more than one set of Maven
 * coordinates (<code>dev.fasti.platform:fasti-engine</code> and the original
 * <code>org.camunda.bpm:camunda-engine</code>) that contain the same Java
 * packages. A build tool treats them as unrelated libraries, so an application
 * can end up with both of them, for example because a third-party library
 * depends on the other one. The class loader then picks every class from
 * whichever library comes first, and the engine runs with a mix of classes
 * from two versions. The resulting errors show up late and are hard to
 * relate to their cause, so the engine refuses to start instead.</p>
 *
 * <p>A library is recognized by a class file that every process engine library
 * contains exactly once, whatever its coordinates and version:
 * {@value #MARKER_RESOURCE}. The check counts the distinct locations of that
 * file which the class loader of the engine adds to those of its parent:</p>
 *
 * <ul>
 *   <li>a location that is reported more than once (e.g. by a class loader and
 *   its parent) is counted once;</li>
 *   <li>a copy that is only visible through the parent class loader is not
 *   counted: class loaders with a hierarchy (a web application that brings its
 *   own engine to a server with a shared engine) keep the copies apart;</li>
 *   <li>the check only sees the class loader that loaded the engine, not the
 *   class loaders of applications deployed next to a shared engine.</li>
 * </ul>
 *
 * <p>The check is performed when a process engine is built. If the other
 * library precedes this one on the class path, the engine is built by the
 * classes of that library, which do not know the check. Integrations that are
 * started independently of the engine classes (such as the Spring Boot
 * starter) therefore perform the check as well. For the same reason this
 * class only uses classes that exist in this library alone or have been part
 * of every process engine library.</p>
 *
 * <p>The check can be switched off by setting the system property
 * {@value #CHECK_ENABLED_SYSTEM_PROPERTY} to <code>false</code>.</p>
 */
public class DuplicateEngineLibraryCheck {

  /** system property to switch the check off */
  public static final String CHECK_ENABLED_SYSTEM_PROPERTY = "fasti.engine.duplicate-library-check.enabled";

  /** a class file that is part of every process engine library */
  public static final String MARKER_RESOURCE = "org/camunda/bpm/engine/ProcessEngine.class";

  protected static final EngineLibraryLogger LOG = EngineLibraryLogger.ENGINE_LIBRARY_LOGGER;

  protected static final String JAR_URL_PREFIX = "jar:";
  protected static final String JAR_URL_SEPARATOR = "!/";
  protected static final String FILE_URL_PREFIX = "file:";

  /**
   * Performs the check for the class loader that loaded the process engine.
   *
   * @throws ProcessEngineException if more than one process engine library is found
   */
  public static void check() {
    check(ProcessEngine.class.getClassLoader());
  }

  /**
   * Performs the check for the given class loader.
   *
   * @throws ProcessEngineException if more than one process engine library is found
   */
  public static void check(ClassLoader classLoader) {
    if (!isCheckEnabled()) {
      return;
    }

    List<String> libraries = findEngineLibraries(classLoader);
    if (libraries.size() > 1) {
      throw LOG.duplicateEngineLibraries(libraries, CHECK_ENABLED_SYSTEM_PROPERTY);
    }
  }

  public static boolean isCheckEnabled() {
    return !"false".equalsIgnoreCase(System.getProperty(CHECK_ENABLED_SYSTEM_PROPERTY));
  }

  /**
   * @return the distinct locations (jar files or directories) of the process
   *         engine libraries that the given class loader adds to the ones of
   *         its parent class loader
   */
  public static List<String> findEngineLibraries(ClassLoader classLoader) {
    if (classLoader == null) {
      // the bootstrap class loader: nothing to enumerate
      return new ArrayList<>();
    }

    Set<String> libraries = findMarkerLocations(classLoader);

    ClassLoader parent = classLoader.getParent();
    if (parent != null) {
      libraries.removeAll(findMarkerLocations(parent));
    }

    return new ArrayList<>(libraries);
  }

  protected static Set<String> findMarkerLocations(ClassLoader classLoader) {
    Set<String> locations = new LinkedHashSet<>();
    try {
      Enumeration<URL> resources = classLoader.getResources(MARKER_RESOURCE);
      while (resources.hasMoreElements()) {
        locations.add(toLibraryLocation(resources.nextElement()));
      }
    } catch (IOException | RuntimeException e) {
      // a class loader that cannot enumerate its resources must not keep the engine from starting
      LOG.debugCouldNotEnumerateEngineLibraries(classLoader, e);
    }
    return locations;
  }

  /**
   * Reduces the URL of the marker resource to the library that contains it,
   * in a form that is the same for all spellings of the same file.
   */
  protected static String toLibraryLocation(URL markerResource) {
    String location = markerResource.toExternalForm();

    if (location.endsWith(MARKER_RESOURCE)) {
      location = location.substring(0, location.length() - MARKER_RESOURCE.length());
    }

    if (location.startsWith(JAR_URL_PREFIX) && location.endsWith(JAR_URL_SEPARATOR)) {
      String jar = location.substring(JAR_URL_PREFIX.length(), location.length() - JAR_URL_SEPARATOR.length());
      if (!jar.contains(JAR_URL_SEPARATOR)) {
        // a plain jar file; nested jars keep their complete URL
        location = jar;
      }
    }

    if (location.startsWith(FILE_URL_PREFIX)) {
      location = toCanonicalPath(location);
    }

    return location;
  }

  protected static String toCanonicalPath(String fileUrl) {
    try {
      return Paths.get(new URI(fileUrl)).toRealPath().toString();
    } catch (Exception e) {
      // not a path of the default file system: compare the URL as it is
      return fileUrl;
    }
  }

}
