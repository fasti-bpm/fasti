/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.dmn.engine.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.camunda.bpm.dmn.engine.impl.el.GraalJsScriptSession;
import org.camunda.bpm.dmn.engine.impl.el.ScriptValueDetacher;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

public class GraalJsScriptSessionTest {

  protected ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

  // which script engines are evaluated in a session ///////////////////////////

  @Test
  public void shouldSupportGraalJsScriptEngine() {
    assertThat(GraalJsScriptSession.isSupported(scriptEngineManager.getEngineByName("javascript"))).isTrue();
    assertThat(GraalJsScriptSession.isSupported(scriptEngineManager.getEngineByName("ecmascript"))).isTrue();
    assertThat(GraalJsScriptSession.isSupported(scriptEngineManager.getEngineByName("Graal.js"))).isTrue();
    assertThat(GraalJsScriptSession.isSupported(GraalJSScriptEngine.create())).isTrue();
  }

  @Test
  public void shouldNotSupportOtherScriptEngines() {
    assertThat(GraalJsScriptSession.isSupported(null)).isFalse();
    assertThat(GraalJsScriptSession.isSupported(scriptEngineManager.getEngineByName("groovy"))).isFalse();

    // a script engine without factory
    assertThat(GraalJsScriptSession.isSupported(mock(ScriptEngine.class))).isFalse();
    assertThat(GraalJsScriptSession.isSupported(mock(ScriptEngine.class, withSettings().extraInterfaces(AutoCloseable.class)))).isFalse();

    // a script engine of another language that can be closed
    assertThat(GraalJsScriptSession.isSupported(mockScriptEngine("other", true))).isFalse();

    // a script engine that calls itself Graal.js but cannot be closed
    assertThat(GraalJsScriptSession.isSupported(mockScriptEngine(GraalJsScriptSession.GRAAL_JS_ENGINE_NAME, false))).isFalse();
  }

  @Test
  public void shouldNotOpenSessionForOtherScriptEngine() {
    assertThatThrownBy(() -> GraalJsScriptSession.open(scriptEngineManager.getEngineByName("groovy")))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldDetectReservedKeys() {
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.js.allowAllAccess")).isTrue();
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.js.allowHostAccess")).isTrue();
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.js.unknownOption")).isTrue();
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.js.")).isTrue();
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.context")).isTrue();

    assertThat(GraalJsScriptSession.isReservedKey("polyglot")).isFalse();
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.js")).isFalse();
    assertThat(GraalJsScriptSession.isReservedKey("polyglot.contexts")).isFalse();
    assertThat(GraalJsScriptSession.isReservedKey("x")).isFalse();
    assertThat(GraalJsScriptSession.isReservedKey("")).isFalse();
    assertThat(GraalJsScriptSession.isReservedKey(null)).isFalse();
    assertThat(GraalJsScriptSession.isReservedKey(1)).isFalse();
  }

  // session that owns the script engine ///////////////////////////////////////

  @Test
  public void shouldEvaluateInContextOfScriptEngine() throws ScriptException {
    // given
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    ContextProbe probe = new ContextProbe();

    // when
    Object result;
    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      assertThat(session.isScriptEngineOwner()).isTrue();
      session.bind("probe", probe);
      session.bind("x", 2);
      result = scriptEngine.eval("probe.record(); x * 21", session.getBindings());

      // then
      assertThat(probe.contexts).containsExactly(scriptEngine.getPolyglotContext());
    }
    assertThat(result).isEqualTo(42);

    // and the script engine and its context are closed
    assertClosed(probe.contexts.get(0));
    assertThatThrownBy(() -> scriptEngine.eval("1 + 1")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldCloseScriptEngineThatWasNotUsed() {
    // given
    GraalJSScriptEngine scriptEngine = createScriptEngine();

    // when
    GraalJsScriptSession.open(scriptEngine).close();

    // then
    assertThatThrownBy(() -> scriptEngine.eval("1 + 1")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldIgnoreRepeatedClose() throws ScriptException {
    // given
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine);
    scriptEngine.eval("1 + 1", session.getBindings());

    // when
    session.close();
    session.close();

    // then
    assertThatThrownBy(() -> scriptEngine.eval("1 + 1")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldNotFailIfScriptEngineCannotBeClosed() throws Exception {
    // given a script engine that fails to close
    ScriptEngine scriptEngine = mockScriptEngine(GraalJsScriptSession.GRAAL_JS_ENGINE_NAME, true);
    org.mockito.Mockito.doThrow(new IllegalStateException("expected")).when((AutoCloseable) scriptEngine).close();
    GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine);

    // when
    session.close();

    // then no exception is thrown
    org.mockito.Mockito.verify((AutoCloseable) scriptEngine).close();
  }

  // variables //////////////////////////////////////////////////////////////////

  @Test
  public void shouldBindVariables() throws ScriptException {
    // given
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    Map<String, Object> variables = new HashMap<>();
    variables.put("a", 1);
    variables.put("b", "two");
    variables.put("c", null);

    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      // when
      session.bindAll(variables);

      // then
      assertThat(scriptEngine.eval("a + b + c", session.getBindings())).isEqualTo("1twonull");
    }
  }

  @Test
  public void shouldNotBindReservedKeys() throws ScriptException {
    // given a script engine that may not access Java
    ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("javascript");

    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      // when variables are named like options of GraalJS
      session.bind("polyglot.js.allowAllAccess", true);
      session.bind("polyglot.js.allowHostAccess", true);
      session.bind("polyglot.js.allowHostClassLookup", true);
      session.bind("polyglot.js.allowIO", true);
      session.bind("polyglot.js.nashorn-compat", true);
      session.bind("polyglot.js.unknownOption", true);
      session.bind("polyglot.context", "not a context");
      session.bind("x", 1);
      // and some more after the context has been created
      session.bind("polyglot.js.allowAllAccess", true);
      session.bind("polyglot.js.unknownOption", true);
      session.bind("", "empty");
      session.bind(null, "null");

      // then the variables are not available
      assertThat(scriptEngine.eval("x + 1", session.getBindings())).isEqualTo(2);
      assertThat(session.getBindings()).doesNotContainKeys("polyglot.js.allowAllAccess", "polyglot.js.unknownOption", "polyglot.context");

      // and did not change what the script may do
      assertThat(scriptEngine.eval("typeof Java", session.getBindings())).isEqualTo("undefined");
      assertThatThrownBy(() -> scriptEngine.eval("java.lang.System.getProperty('user.name')", session.getBindings()))
        .isInstanceOf(ScriptException.class);
    }
  }

  // script engine that is provided repeatedly //////////////////////////////////

  @Test
  public void shouldEvaluateInNewContextIfScriptEngineIsReused() throws ScriptException {
    // given a script engine that was used by a session already
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    GraalJsScriptSession.open(scriptEngine).close();
    ContextProbe probe = new ContextProbe();

    // when
    for (int i = 0; i < 2; i++) {
      try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
        assertThat(session.isScriptEngineOwner()).isFalse();
        session.bind("probe", probe);
        session.bind("x", i);

        // then the configuration of the script engine applies: the script may call the probe
        assertThat(scriptEngine.eval("probe.record(); x + 1", session.getBindings())).isEqualTo(i + 1);
      }
    }

    // and every evaluation had its own context, that is closed now
    assertThat(probe.contexts).hasSize(2).doesNotHaveDuplicates();
    for (Context context : probe.contexts) {
      assertClosed(context);
    }
  }

  @Test
  public void shouldSeparateSessionsOfSharedScriptEngine() throws ScriptException {
    // given two sessions of one script engine that are open at the same time
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    ContextProbe probe = new ContextProbe();
    GraalJsScriptSession first = GraalJsScriptSession.open(scriptEngine);
    GraalJsScriptSession second = GraalJsScriptSession.open(scriptEngine);
    try {
      first.bind("probe", probe);
      first.bind("x", 1);
      second.bind("probe", probe);
      second.bind("x", 2);

      // when
      Object firstResult = scriptEngine.eval("probe.record(); var y = x; y", first.getBindings());
      Object secondResult = scriptEngine.eval("probe.record(); typeof y + x", second.getBindings());

      // then they do not see each other
      assertThat(firstResult).isEqualTo(1);
      assertThat(secondResult).isEqualTo("undefined2");
      assertThat(probe.contexts).hasSize(2).doesNotHaveDuplicates();

      // and closing one does not affect the other
      first.close();
      assertThat(scriptEngine.eval("x + 1", second.getBindings())).isEqualTo(3);
    } finally {
      first.close();
      second.close();
    }
    for (Context context : probe.contexts) {
      assertClosed(context);
    }
  }

  // script variables ///////////////////////////////////////////////////////////

  @Test
  public void shouldOnlyProvideBoundValuesAsScriptVariables() {
    // given
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      session.bind("x", 1);
      session.bind("name", "a");

      // when, then nothing that JavaScript or the script engine provide is contained
      assertThat(session.getScriptVariables()).containsOnly(entry("x", 1), entry("name", "a"));
      assertThat(session.getChangedScriptVariables(session.getScriptVariables())).isEmpty();
    }
  }

  @Test
  public void shouldDetermineChangedScriptVariables() throws ScriptException {
    // given
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    List<Object> javaList = new ArrayList<>();
    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      session.bind("unchanged", 1);
      session.bind("changed", 1);
      session.bind("aLong", 5L);
      session.bind("aShort", (short) 6);
      session.bind("aChar", 'c');
      session.bind("javaList", javaList);
      Map<String, Object> initialVariables = session.getScriptVariables();

      // when
      scriptEngine.eval(
          "changed = changed + 1;"
        + "aLong = aLong; aShort = aShort; aChar = aChar;"
        + "javaList.add('modified');"
        + "var declared = {k: [1, 'a']};"
        + "undeclared = true;"
        + "let scoped = 1;"
        + "function declaredFunction() {}"
        + "var symbol = Symbol('s');"
        + "var withFunction = {f: function() {}};"
        + "this['polyglot.js.allowIO'] = true;"
        + "this['polyglot.context'] = 1;", session.getBindings());
      Map<String, Object> changedVariables = session.getChangedScriptVariables(initialVariables);

      // then
      assertThat(changedVariables).containsOnlyKeys("changed", "declared", "undeclared");
      assertThat(changedVariables.get("changed")).isEqualTo(2);
      assertThat(changedVariables.get("undeclared")).isEqualTo(true);
      Map<String, Object> declared = new HashMap<>();
      declared.put("k", Arrays.asList(1, "a"));
      assertThat(changedVariables.get("declared")).isExactlyInstanceOf(LinkedHashMap.class).isEqualTo(declared);
    }
  }

  @Test
  public void shouldTellIfValueIsDetached() throws ScriptException {
    GraalJSScriptEngine scriptEngine = createScriptEngine();
    try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
      assertThat(ScriptValueDetacher.isDetached(null)).isTrue();
      assertThat(ScriptValueDetacher.isDetached("a")).isTrue();
      assertThat(ScriptValueDetacher.isDetached(Arrays.asList(1, 2))).isTrue();
      assertThat(isDetachedResult(scriptEngine, session, "({a: [1, {b: new Date(0)}], s: new Set([1])})")).isTrue();
      assertThat(isDetachedResult(scriptEngine, session, "var cyclic = {}; cyclic.self = cyclic; cyclic")).isTrue();

      assertThat(isDetachedResult(scriptEngine, session, "(function() {})")).isFalse();
      assertThat(isDetachedResult(scriptEngine, session, "Symbol('s')")).isFalse();
      assertThat(isDetachedResult(scriptEngine, session, "/x/")).isFalse();
      assertThat(isDetachedResult(scriptEngine, session, "({a: [1, {f: function() {}}]})")).isFalse();
      assertThat(isDetachedResult(scriptEngine, session, "new Set([function() {}])")).isFalse();
      assertThat(isDetachedResult(scriptEngine, session, "var cyclicFunction = {f: function() {}}; cyclicFunction.self = cyclicFunction; cyclicFunction")).isFalse();
    }
  }

  protected boolean isDetachedResult(ScriptEngine scriptEngine, GraalJsScriptSession session, String script) throws ScriptException {
    return ScriptValueDetacher.isDetached(session.detach(scriptEngine.eval(script, session.getBindings())));
  }

  // concurrency ////////////////////////////////////////////////////////////////

  @Test
  public void shouldCreateContextsOfSharedScriptEngineOneAfterTheOther() throws Exception {
    // given a script engine that is shared by threads
    int threads = 4;
    ContextCreationProbe probe = new ContextCreationProbe();
    ScriptEngine scriptEngine = new SharedScriptEngine(probe);
    CyclicBarrier start = new CyclicBarrier(threads);

    // when all of them open a session at the same time
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Object>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        final int x = i;
        results.add(executor.submit((Callable<Object>) () -> {
          start.await();
          try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
            session.bind("x", x);
            return session.getBindings().get("x");
          }
        }));
      }
      for (int i = 0; i < threads; i++) {
        assertThat(results.get(i).get()).isEqualTo(i);
      }
    } finally {
      executor.shutdownNow();
    }

    // then every session had a context, which was not created while another one was created
    assertThat(probe.created.get()).isEqualTo(threads);
    assertThat(probe.maxConcurrentCreations.get()).isEqualTo(1);
  }

  @Test
  public void shouldCloseScriptEngineIfContextCannotBeCreated() {
    // given a script engine that cannot create a context
    ContextCreationProbe probe = new ContextCreationProbe();
    probe.failure = new IllegalStateException("expected");
    SharedScriptEngine scriptEngine = new SharedScriptEngine(probe);

    // when
    assertThatThrownBy(() -> GraalJsScriptSession.open(scriptEngine))
      // then the failure is reported
      .isSameAs(probe.failure);

    // and the script engine is closed
    assertThat(scriptEngine.closed).isTrue();
  }

  // class loaders ////////////////////////////////////////////////////////////

  @Test
  public void shouldNotKeepClassesOfJavaObjectsUsedByScripts() throws Exception {
    // given a Java object of a class loader that can be discarded, like the one of a process application
    URL[] classpath = { ScriptHostObject.class.getProtectionDomain().getCodeSource().getLocation() };
    URLClassLoader classLoader = new URLClassLoader(classpath, null);
    Object hostObject = classLoader.loadClass(ScriptHostObject.class.getName()).getDeclaredConstructor().newInstance();
    assertThat(hostObject.getClass().getClassLoader()).isSameAs(classLoader);

    // and scripts that used the object
    ScriptEngineManager manager = new ScriptEngineManager();
    for (int i = 0; i < 20; i++) {
      ScriptEngine scriptEngine = manager.getEngineByName("javascript");
      scriptEngine.getContext().setAttribute("polyglot.js.allowHostAccess", true, ScriptContext.ENGINE_SCOPE);
      try (GraalJsScriptSession session = GraalJsScriptSession.open(scriptEngine)) {
        session.bind("hostObject", hostObject);
        assertThat(scriptEngine.eval("hostObject.twice(21)", session.getBindings())).isEqualTo(42);
      }
    }

    // when the class loader is discarded
    WeakReference<ClassLoader> classLoaderReference = new WeakReference<>(classLoader);
    hostObject = null;
    classLoader.close();
    classLoader = null;
    for (int i = 0; i < 20 && classLoaderReference.get() != null; i++) {
      collectGarbage();
      // a thread refers to the last Context it has used until it uses the next one
      try (GraalJsScriptSession session = GraalJsScriptSession.open(new ScriptEngineManager().getEngineByName("javascript"))) {
        session.bind("x", i);
      }
    }

    // then it is collected although the script engine factory is still in use
    assertThat(classLoaderReference.get()).isNull();
    assertThat(manager.getEngineByName("javascript")).isNotNull();
  }

  // helper /////////////////////////////////////////////////////////////////////

  protected GraalJSScriptEngine createScriptEngine() {
    ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("javascript");
    // allow the script to call the probe
    scriptEngine.getContext().setAttribute("polyglot.js.allowHostAccess", true, ScriptContext.ENGINE_SCOPE);
    return (GraalJSScriptEngine) scriptEngine;
  }

  protected ScriptEngine mockScriptEngine(String engineName, boolean closeable) {
    ScriptEngineFactory factory = mock(ScriptEngineFactory.class);
    when(factory.getEngineName()).thenReturn(engineName);
    ScriptEngine scriptEngine = closeable
        ? mock(ScriptEngine.class, withSettings().extraInterfaces(AutoCloseable.class))
        : mock(ScriptEngine.class);
    when(scriptEngine.getFactory()).thenReturn(factory);
    return scriptEngine;
  }

  protected void assertClosed(Context context) {
    assertThatThrownBy(() -> context.eval("js", "1 + 1")).isInstanceOf(IllegalStateException.class);
  }

  protected void collectGarbage() {
    for (int i = 0; i < 3; i++) {
      System.gc();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static class ContextProbe {

    public final List<Context> contexts = new ArrayList<>();

    public void record() {
      contexts.add(Context.getCurrent());
    }
  }

  /** counts how many contexts are created, in total and at the same time */
  protected static class ContextCreationProbe {

    protected AtomicInteger created = new AtomicInteger();
    protected AtomicInteger concurrentCreations = new AtomicInteger();
    protected AtomicInteger maxConcurrentCreations = new AtomicInteger();
    protected RuntimeException failure;

    protected void createContext() {
      int concurrent = concurrentCreations.incrementAndGet();
      maxConcurrentCreations.accumulateAndGet(concurrent, Math::max);
      try {
        // give the other threads the time to interfere
        Thread.sleep(50);
        if (failure != null) {
          throw failure;
        }
        created.incrementAndGet();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        concurrentCreations.decrementAndGet();
      }
    }
  }

  /** bindings that create their context when they are accessed for the first time, like the bindings of GraalJS */
  protected static class LazyContextBindings extends SimpleBindings {

    protected final ContextCreationProbe probe;
    protected boolean contextCreated = false;

    protected LazyContextBindings(ContextCreationProbe probe) {
      this.probe = probe;
    }

    protected void requireContext() {
      if (!contextCreated) {
        probe.createContext();
        contextCreated = true;
      }
    }

    @Override
    public Object put(String name, Object value) {
      requireContext();
      return super.put(name, value);
    }

    @Override
    public Object get(Object key) {
      requireContext();
      return super.get(key);
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
      requireContext();
      return super.entrySet();
    }
  }

  /** a script engine that passes for a GraalJS script engine and hands out {@link LazyContextBindings} */
  protected static class SharedScriptEngine extends AbstractScriptEngine implements AutoCloseable {

    protected final ContextCreationProbe probe;
    protected volatile boolean closed = false;

    protected SharedScriptEngine(ContextCreationProbe probe) {
      this.probe = probe;
      setBindings(new LazyContextBindings(probe), ScriptContext.ENGINE_SCOPE);
    }

    @Override
    public Bindings createBindings() {
      return new LazyContextBindings(probe);
    }

    @Override
    public ScriptEngineFactory getFactory() {
      ScriptEngineFactory factory = mock(ScriptEngineFactory.class);
      when(factory.getEngineName()).thenReturn(GraalJsScriptSession.GRAAL_JS_ENGINE_NAME);
      return factory;
    }

    @Override
    public Object eval(String script, ScriptContext context) {
      return null;
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) {
      return null;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

}
