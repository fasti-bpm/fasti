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
package org.camunda.bpm.engine.impl.scripting.env;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.script.Bindings;
import javax.script.ScriptEngine;

import org.camunda.bpm.application.AbstractProcessApplication;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.application.ProcessApplicationUnavailableException;
import org.camunda.bpm.dmn.engine.impl.el.GraalJsScriptSession;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.scripting.ExecutableScript;
import org.camunda.bpm.engine.impl.scripting.ScriptFactory;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptBindings;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptingEngines;

/**
 * <p>The scripting environment contains scripts that provide an environment to
 * a user provided script. The environment may contain additional libraries
 * or imports.</p>
 *
 * <p>The environment performs lazy initialization of scripts: the first time a script of a given
 * script language is executed, the environment will use the {@link ScriptEnvResolver ScriptEnvResolvers}
 * for resolving the environment scripts for that language. The scripts (if any) are then pre-compiled
 * and cached for reuse.</p>
 *
 * @author Daniel Meyer
 *
 */
public class ScriptingEnvironment {

  /** the cached environment scripts per script language */
  protected Map<String, List<ExecutableScript>> env = new HashMap<>();

  /** the resolvers */
  protected List<ScriptEnvResolver> envResolvers;

  /** the script factory used for compiling env scripts */
  protected ScriptFactory scriptFactory;

  /** the scripting engines */
  protected ScriptingEngines scriptingEngines;

  public ScriptingEnvironment(ScriptFactory scriptFactory, List<ScriptEnvResolver> scriptEnvResolvers, ScriptingEngines scriptingEngines) {
    this.scriptFactory = scriptFactory;
    this.envResolvers = scriptEnvResolvers;
    this.scriptingEngines = scriptingEngines;
  }

  /**
   * execute a given script in the environment
   *
   * @param script the {@link ExecutableScript} to execute
   * @param scope the scope in which to execute the script
   * @return the result of the script evaluation
   */
  public Object execute(ExecutableScript script, VariableScope scope) {

    // get script engine
    ScriptEngine scriptEngine = scriptingEngines.getScriptEngineForLanguage(script.getLanguage());

    if (GraalJsScriptSession.isSupported(scriptEngine)) {
      return executeInSession(script, scope, scriptEngine);
    }

    // create bindings
    Bindings bindings = scriptingEngines.createBindings(scriptEngine, scope);

    return execute(script, scope, bindings, scriptEngine);
  }

  /**
   * Executes a script with a GraalJS script engine. Such an engine evaluates
   * in a polyglot Context that must be closed and that it only uses if it is
   * given its own bindings (see {@link GraalJsScriptSession}). In contrast to
   * the script engines of other languages
   * <ul>
   * <li>the script engine is closed after the evaluation and cannot be used again,</li>
   * <li>the variables that are visible to the script are copied into the
   * bindings of the script engine before the evaluation,</li>
   * <li>the variables of the script are stored in the variable scope after
   * the evaluation and not when they are assigned, if
   * {@link ProcessEngineConfigurationImpl#isAutoStoreScriptVariables()} is
   * set, see {@link #storeScriptVariables(Map, Map, VariableScope)}, and</li>
   * <li>JavaScript objects and arrays that are returned by the script are
   * copied to Java collections.</li>
   * </ul>
   *
   * @param script the {@link ExecutableScript} to execute
   * @param scope the scope in which to execute the script
   * @param scriptEngine a script engine that is supported by {@link GraalJsScriptSession}
   * @return the result of the script evaluation
   */
  protected Object executeInSession(ExecutableScript script, VariableScope scope, ScriptEngine scriptEngine) {
    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      session.bindAll(scriptingEngines.createBindings(scope));

      if (!isAutoStoreScriptVariables(scope)) {
        Object result = execute(script, scope, session.getBindings(), scriptEngine);
        return session.detach(result);
      }

      Map<String, Object> initialScopeVariables = new HashMap<>(scope.getVariables());
      Map<String, Object> initialScriptVariables = session.getScriptVariables();

      Object result = execute(script, scope, session.getBindings(), scriptEngine);

      storeScriptVariables(session.getChangedScriptVariables(initialScriptVariables), initialScopeVariables, scope);
      return session.detach(result);
    }
  }

  protected boolean isAutoStoreScriptVariables(VariableScope scope) {
    ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
    return scope != null && config != null && config.isAutoStoreScriptVariables();
  }

  /**
   * Stores the global variables that a script has declared or changed in the
   * variable scope, like {@link ScriptBindings} does for the script engines
   * that write their variables to the bindings. JavaScript objects and arrays
   * are stored as Java collections. Not stored are
   * <ul>
   * <li>the names that are never stored for any script language, e.g.
   * <code>execution</code>,</li>
   * <li>variables that the script has set in the variable scope itself, e.g.
   * with <code>execution.setVariable</code>: the value that was set
   * explicitly is kept, and</li>
   * <li>functions and other values that cannot be used outside of the script
   * (see {@link GraalJsScriptSession#getChangedScriptVariables(Map)}).</li>
   * </ul>
   *
   * @param scriptVariables the variables that the script has declared or changed
   * @param initialScopeVariables the variables of the scope before the script was executed
   * @param scope the scope in which the script was executed
   */
  protected void storeScriptVariables(Map<String, Object> scriptVariables, Map<String, Object> initialScopeVariables, VariableScope scope) {
    for (Map.Entry<String, Object> scriptVariable : scriptVariables.entrySet()) {
      String name = scriptVariable.getKey();
      if (ScriptBindings.isUnstoredKey(name)) {
        continue;
      }
      boolean setByScript = scope.hasVariable(name) != initialScopeVariables.containsKey(name)
          || !Objects.equals(scope.getVariable(name), initialScopeVariables.get(name));
      if (!setByScript) {
        scope.setVariable(name, scriptVariable.getValue());
      }
    }
  }

  public Object execute(ExecutableScript script, VariableScope scope, Bindings bindings, ScriptEngine scriptEngine) {

    // first, evaluate the env scripts (if any)
    List<ExecutableScript> envScripts = getEnvScripts(script, scriptEngine);
    for (ExecutableScript envScript : envScripts) {
      envScript.execute(scriptEngine, scope, bindings);
    }

    // next evaluate the actual script
    return script.execute(scriptEngine, scope, bindings);
  }

  protected Map<String, List<ExecutableScript>> getEnv(String language) {
    ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
    ProcessApplicationReference processApplication = Context.getCurrentProcessApplication();

    Map<String, List<ExecutableScript>> result = null;
    if (config.isEnableFetchScriptEngineFromProcessApplication()) {
      if(processApplication != null) {
        result = getPaEnvScripts(processApplication);
      }
    }

    return result != null ? result : env;
  }

  protected Map<String, List<ExecutableScript>> getPaEnvScripts(ProcessApplicationReference pa) {
    try {
      ProcessApplicationInterface processApplication = pa.getProcessApplication();
      ProcessApplicationInterface rawObject = processApplication.getRawObject();

      if (rawObject instanceof AbstractProcessApplication) {
        AbstractProcessApplication abstractProcessApplication = (AbstractProcessApplication) rawObject;
        return abstractProcessApplication.getEnvironmentScripts();
      }
      return null;
    }
    catch (ProcessApplicationUnavailableException e) {
      throw new ProcessEngineException("Process Application is unavailable.", e);
    }
  }

  protected List<ExecutableScript> getEnvScripts(ExecutableScript script, ScriptEngine scriptEngine) {
    List<ExecutableScript> envScripts = getEnvScripts(script.getLanguage().toLowerCase());
    if (envScripts.isEmpty()) {
      envScripts = getEnvScripts(scriptEngine.getFactory().getLanguageName().toLowerCase());
    }
    return envScripts;
  }

  /**
   * Returns the env scripts for the given language. Performs lazy initialization of the env scripts.
   *
   * @param scriptLanguage the language
   * @return a list of executable environment scripts. Never null.
   */
  protected List<ExecutableScript> getEnvScripts(String scriptLanguage) {
    Map<String, List<ExecutableScript>> environment = getEnv(scriptLanguage);
    List<ExecutableScript> envScripts = environment.get(scriptLanguage);
    if(envScripts == null) {
      synchronized (this) {
        envScripts = environment.get(scriptLanguage);
        if(envScripts == null) {
          envScripts = initEnvForLanguage(scriptLanguage);
          environment.put(scriptLanguage, envScripts);
        }
      }
    }
    return envScripts;
  }

  /**
   * Initializes the env scripts for a given language.
   *
   * @param language the language
   * @return the list of env scripts. Never null.
   */
  protected List<ExecutableScript> initEnvForLanguage(String language) {

    List<ExecutableScript> scripts = new ArrayList<>();
    for (ScriptEnvResolver resolver : envResolvers) {
      String[] resolvedScripts = resolver.resolve(language);
      if(resolvedScripts != null) {
        for (String resolvedScript : resolvedScripts) {
          scripts.add(scriptFactory.createScriptFromSource(language, resolvedScript));
        }
      }
    }

    return scripts;
  }

}
