/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.runlistener;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.DuplicateEngineLibraryCheck;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * <p>Lets the application fail to start if the classes of the process engine
 * are present in more than one library, see {@link DuplicateEngineLibraryCheck}.</p>
 *
 * <p>The process engine performs this check itself when it is built, but only
 * if its classes are loaded from this distribution. On a class path on which
 * another process engine library comes first, the engine is built by the
 * classes of that library, and they do not know the check. This listener is
 * registered in <code>META-INF/spring.factories</code> and does not exist in
 * other libraries, so it runs in any case.</p>
 */
public class EngineLibraryCheckListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    DuplicateEngineLibraryCheck.check(getEngineClassLoader());
  }

  /**
   * @return the class loader that provides the classes of the process engine
   */
  protected ClassLoader getEngineClassLoader() {
    return ProcessEngine.class.getClassLoader();
  }

  /**
   * After the listeners of Spring Boot, in particular after the logging
   * system is set up, so that the failure is reported in the log.
   */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

}
