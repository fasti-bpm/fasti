/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.dmn.engine.el;

/**
 * A Java object that is used by a script. The class only depends on the JDK,
 * so that a test can load it with a class loader of its own.
 */
public class ScriptHostObject {

  public int twice(int value) {
    return 2 * value;
  }

}
