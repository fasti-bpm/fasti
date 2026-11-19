/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.dmn.engine.impl.el;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

import org.camunda.bpm.dmn.engine.impl.DmnEngineLogger;

/**
 * The evaluation of one script (and the environment scripts it depends on)
 * by a GraalJS script engine.
 * <p>
 * A GraalJS script engine evaluates JavaScript in a polyglot Context, a
 * resource that holds memory outside of the reach of the script engine and
 * that must be closed. The engine only uses its own Context for an evaluation
 * if it is given its own bindings. Given any other {@link Bindings}
 * implementation it copies them into an additional Context that nobody can
 * close. A session therefore
 * </p>
 * <ul>
 * <li>provides the {@link #getBindings() bindings} that make the engine
 * evaluate in a single Context,</li>
 * <li>copies the variables of the script into these bindings, see
 * {@link #bind(String, Object)},</li>
 * <li>{@link #detach(Object) detaches} the result of the script from the
 * Context,</li>
 * <li>tells which global variables the script has
 * {@link #getChangedScriptVariables(Map) declared or changed} and</li>
 * <li>closes the Context when it is {@link #close() closed} itself.</li>
 * </ul>
 * <p>
 * <strong>Ownership of the script engine:</strong> the first session that is
 * opened for a script engine uses the Context of the engine and closes the
 * engine at the end. The engine cannot be used afterwards. A script engine
 * resolver should therefore provide a new GraalJS script engine whenever it
 * is asked for one, like the resolvers of the process engine and the DMN
 * engine do. If a resolver hands out the same engine again nevertheless, e.g.
 * because it caches script engines, the session evaluates in a new Context
 * that is created from the configuration of that engine and only closes this
 * Context. Every evaluation gets a Context of its own in both cases.
 * </p>
 * <p>
 * <strong>Concurrency:</strong> a session is used by one thread. Sessions of
 * the same script engine can be open at the same time in different threads,
 * their Contexts are created one after the other while holding the monitor of
 * the script engine. A script engine that is used by sessions should not be
 * used in any other way at the same time.
 * </p>
 * <p>
 * <strong>Sharing of parsed code:</strong> the Contexts of all script engines
 * of a script engine factory belong to one polyglot Engine, which caches the
 * parsed code of the scripts. The factory only refers to it weakly, so that
 * it is collected, and the scripts are parsed again, once no script engine of
 * the factory is in use and the garbage collector runs. A session does not
 * keep the polyglot Engine alive on purpose: it refers to the classes of the
 * Java objects that scripts have used and would prevent that the class
 * loader of an undeployed process application is collected.
 * </p>
 * <p>
 * Note that a polyglot Engine only accepts Contexts with the same access to
 * Java (host access): the script engines of one factory, i.e. of one
 * {@link javax.script.ScriptEngineManager}, should all be configured alike in
 * that respect. As long as the polyglot Engine exists, a Context with a
 * different host access fails to be created with an
 * {@link IllegalStateException}.
 * </p>
 * <p>
 * GraalVM is only accessed through {@code javax.script} and reflection, so
 * that this module does not depend on it.
 * </p>
 */
public class GraalJsScriptSession implements AutoCloseable {

  protected static final DmnEngineLogger LOG = DmnEngineLogger.ENGINE_LOGGER;

  /** the name of the script engine that is provided by GraalJS */
  public static final String GRAAL_JS_ENGINE_NAME = "Graal.js";

  /**
   * GraalJS does not treat a binding with this prefix as variable of the
   * script but as option of the Context to create.
   */
  public static final String OPTION_KEY_PREFIX = "polyglot.js.";

  /**
   * GraalJS evaluates in the Context that is bound to this key, if it is
   * given bindings that are not its own.
   */
  public static final String CONTEXT_KEY = "polyglot.context";

  /** the script engines a session has been opened for */
  protected static final Set<ScriptEngine> OWNED_SCRIPT_ENGINES =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<ScriptEngine, Boolean>()));

  protected final ScriptEngine scriptEngine;
  protected final Bindings bindings;
  protected final boolean scriptEngineOwner;
  protected boolean closed = false;

  protected GraalJsScriptSession(ScriptEngine scriptEngine, Bindings bindings, boolean scriptEngineOwner) {
    this.scriptEngine = scriptEngine;
    this.bindings = bindings;
    this.scriptEngineOwner = scriptEngineOwner;
  }

  /**
   * @return true if scripts of the given engine must be evaluated in a
   *   session: the engine is a GraalJS script engine that can be closed
   */
  public static boolean isSupported(ScriptEngine scriptEngine) {
    if (!(scriptEngine instanceof AutoCloseable)) {
      return false;
    }
    ScriptEngineFactory factory = scriptEngine.getFactory();
    return factory != null && GRAAL_JS_ENGINE_NAME.equals(factory.getEngineName());
  }

  /**
   * @return true if GraalJS gives the binding with the given key a meaning
   *   of its own. Such a key must never be taken from a variable.
   */
  public static boolean isReservedKey(Object key) {
    if (key instanceof String) {
      String name = (String) key;
      return name.startsWith(OPTION_KEY_PREFIX) || name.equals(CONTEXT_KEY);
    }
    return false;
  }

  /**
   * Opens a session for an evaluation. The session must be closed when the
   * evaluation is done.
   *
   * @param scriptEngine a script engine that {@link #isSupported(ScriptEngine) is supported}
   */
  public static GraalJsScriptSession open(ScriptEngine scriptEngine) {
    if (!isSupported(scriptEngine)) {
      throw new IllegalArgumentException("Not a closeable GraalJS script engine: " + scriptEngine);
    }
    // the first session uses the bindings that make the engine evaluate in
    // its own Context. If the engine belongs to another session or is closed
    // already, new bindings create a Context with the same configuration
    boolean scriptEngineOwner = OWNED_SCRIPT_ENGINES.add(scriptEngine);

    // GraalJS creates all Contexts of a script engine with one builder, which
    // it modifies whenever it creates a Context and which is not thread-safe.
    // The Contexts of the sessions of a script engine are therefore created
    // one after the other, here instead of when the first variable is bound.
    synchronized (scriptEngine) {
      Bindings bindings = scriptEngineOwner ? scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE) : scriptEngine.createBindings();
      GraalJsScriptSession session = new GraalJsScriptSession(scriptEngine, bindings, scriptEngineOwner);
      try {
        session.createContext();
      } catch (RuntimeException | Error e) {
        session.close();
        throw e;
      }
      return session;
    }
  }

  /**
   * Makes GraalJS create the Context of the bindings, which it does when the
   * bindings are accessed for the first time.
   */
  protected void createContext() {
    if (bindings != null) {
      bindings.entrySet();
    }
  }

  /**
   * @return the bindings to pass to the script engine and to compiled scripts
   *   of it when evaluating a script
   */
  public Bindings getBindings() {
    return bindings;
  }

  /**
   * @return true if the session evaluates in the Context of the script
   *   engine and closes the engine at the end
   */
  public boolean isScriptEngineOwner() {
    return scriptEngineOwner;
  }

  /**
   * Makes a value available to the script as global variable. Names that
   * GraalJS reserves for the configuration of a Context are ignored, so that
   * the variables of a process or a decision cannot change the privileges of
   * a script. Such a variable cannot be referred to by a script anyway.
   */
  public void bind(String name, Object value) {
    if (name == null || name.isEmpty() || isReservedKey(name)) {
      return;
    }
    bindings.put(name, value);
  }

  public void bindAll(Map<String, ?> values) {
    for (Map.Entry<String, ?> entry : values.entrySet()) {
      bind(entry.getKey(), entry.getValue());
    }
  }

  /**
   * @param value a value that was produced by the script
   * @return the value in a form that can be used after the session has been
   *   closed, see {@link ScriptValueDetacher}
   */
  public Object detach(Object value) {
    return ScriptValueDetacher.detach(value);
  }

  /**
   * @return a copy of the global variables of the script: the values that
   *   have been bound and the variables that scripts have declared so far.
   *   The functions and objects that JavaScript itself provides are not
   *   contained.
   */
  public Map<String, Object> getScriptVariables() {
    return new LinkedHashMap<>(bindings);
  }

  /**
   * Determines the global variables that scripts have declared or assigned a
   * new value to: variables that are declared with <code>var</code> at the
   * top level of a script, variables that are assigned without being declared
   * and properties of the global object. Variables that are declared with
   * <code>let</code> or <code>const</code> are local to their script.
   * <p>
   * The values are {@link #detach(Object) detached} from the Context.
   * Variables with a value that cannot be used without the Context are left
   * out, e.g. functions. So are the names that GraalJS
   * {@link #isReservedKey(Object) reserves}. A Java object that was bound
   * and has been modified, but not replaced, is not considered as changed.
   * </p>
   *
   * @param initialVariables the {@link #getScriptVariables() variables} as
   *   they were before the scripts have been evaluated
   * @return the variables that are new or have another value than before
   */
  public Map<String, Object> getChangedScriptVariables(Map<String, Object> initialVariables) {
    Map<String, Object> changedVariables = new LinkedHashMap<>();
    for (Map.Entry<String, Object> variable : bindings.entrySet()) {
      String name = variable.getKey();
      Object value = variable.getValue();
      if (isReservedKey(name)) {
        continue;
      }
      if (initialVariables.containsKey(name) && isSameValue(initialVariables.get(name), value)) {
        continue;
      }
      Object detachedValue = detach(value);
      if (ScriptValueDetacher.isDetached(detachedValue)) {
        changedVariables.put(name, detachedValue);
      }
    }
    return changedVariables;
  }

  /**
   * @return true if a variable has not been assigned another value. Objects
   *   of JavaScript are equal if they are the same object.
   */
  protected boolean isSameValue(Object initialValue, Object value) {
    return initialValue == value || (initialValue != null && initialValue.equals(value));
  }

  /**
   * Closes the Context of the evaluation. A failure to do so is logged and
   * not propagated, so that it neither fails a successful evaluation nor
   * hides the failure of an unsuccessful one.
   */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (scriptEngineOwner) {
        ((AutoCloseable) scriptEngine).close();
      } else if (bindings instanceof AutoCloseable) {
        ((AutoCloseable) bindings).close();
      }
    } catch (Exception | LinkageError e) {
      LOG.logClosingScriptEngineFailed(e);
    }
  }

}
