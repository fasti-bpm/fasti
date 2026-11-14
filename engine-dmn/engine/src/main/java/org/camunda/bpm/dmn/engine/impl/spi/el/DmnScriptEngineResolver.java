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
package org.camunda.bpm.dmn.engine.impl.spi.el;

import javax.script.ScriptEngine;

/**
 * Resolver for available script engines.
 */
public interface DmnScriptEngineResolver {

  /**
   * Get a script engine by script engine language.
   * <p>
   * A GraalJS script engine is closed by the DMN engine after it has
   * evaluated an expression and should therefore be created whenever this
   * method is invoked. If the same GraalJS script engine is returned again,
   * it is only used as template for the configuration of the context in
   * which an expression is evaluated.
   * See {@link org.camunda.bpm.dmn.engine.impl.el.GraalJsScriptSession}.
   * </p>
   *
   * @param language the language of the script engine
   * @return the script engine or null if no script engine for this language exists
   */
  ScriptEngine getScriptEngineForLanguage(String language);

}

