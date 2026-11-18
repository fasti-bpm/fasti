/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.cfg;

import java.util.Collection;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;

/**
 * <p>The messages of the {@link DuplicateEngineLibraryCheck}. They belong to
 * the configuration of the process engine and continue the numbers of the
 * {@link ConfigurationLogger}.</p>
 *
 * <p>They are not part of the {@link ConfigurationLogger} itself, because the
 * check has to work on a class path with a second process engine library: a
 * class that both libraries contain may then be loaded from the other one,
 * which does not have these messages.</p>
 */
public class EngineLibraryLogger extends ProcessEngineLogger {

  public static final EngineLibraryLogger ENGINE_LIBRARY_LOGGER = createLogger(
      EngineLibraryLogger.class, PROJECT_CODE, "org.camunda.bpm.engine.cfg", "12");

  public ProcessEngineException duplicateEngineLibraries(Collection<String> libraries, String checkEnabledProperty) {
    return new ProcessEngineException(exceptionMessage("021",
        "The classes of the process engine were found in {} libraries on the class path: {}. "
            + "Libraries that contain the package 'org.camunda.bpm.engine' must not be combined, for example "
            + "'dev.fasti.platform:fasti-engine' and 'org.camunda.bpm:camunda-engine': the engine would run with "
            + "a mix of classes from both of them. Remove all but one of the libraries from the application, "
            + "e.g. by excluding the dependency that is not wanted. "
            + "The check can be switched off by setting the system property '{}' to false.",
        libraries.size(), libraries, checkEnabledProperty));
  }

  public void debugCouldNotEnumerateEngineLibraries(ClassLoader classLoader, Throwable cause) {
    logDebug("022", "Could not look up the process engine libraries of class loader {}: {}", classLoader, cause.getMessage());
  }

}
