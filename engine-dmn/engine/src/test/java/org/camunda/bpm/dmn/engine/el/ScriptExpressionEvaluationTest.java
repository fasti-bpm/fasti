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
package org.camunda.bpm.dmn.engine.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.camunda.bpm.dmn.engine.impl.DefaultDmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.impl.DmnExpressionImpl;
import org.camunda.bpm.dmn.engine.impl.el.DefaultScriptEngineResolver;
import org.camunda.bpm.dmn.engine.impl.el.VariableContextScriptBindings;
import org.camunda.bpm.dmn.engine.impl.evaluation.ExpressionEvaluationHandler;
import org.camunda.bpm.dmn.engine.impl.spi.el.DmnScriptEngineResolver;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.context.VariableContext;
import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Asserts which script engines are closed after the evaluation of an
 * expression and that all other script engines are used like before.
 */
public class ScriptExpressionEvaluationTest {

  protected static final String JAVASCRIPT = "javascript";
  protected static final String GROOVY = "groovy";

  // script engines of other languages //////////////////////////////////////////

  @Test
  public void shouldCacheScriptEngineAndCompiledScriptOfOtherLanguage() {
    // given
    DefaultScriptEngineResolver scriptEngineResolver = new DefaultScriptEngineResolver();
    ExpressionEvaluationHandler handler = createHandler(scriptEngineResolver);
    DmnExpressionImpl expression = createExpression(GROOVY, "x + 1");

    // when
    Object firstResult = handler.evaluateExpression(GROOVY, expression, variables("x", 1));
    Object secondResult = handler.evaluateExpression(GROOVY, expression, variables("x", 2));

    // then
    assertThat(firstResult).isEqualTo(2);
    assertThat(secondResult).isEqualTo(3);
    assertThat(expression.getCachedCompiledScript()).isNotNull();
    assertThat(scriptEngineResolver.getScriptEngineForLanguage(GROOVY))
      .isSameAs(scriptEngineResolver.getScriptEngineForLanguage(GROOVY))
      .isSameAs(expression.getCachedCompiledScript().getEngine());
  }

  @Test
  public void shouldNotCacheGraalJsScriptEngine() {
    // given
    DefaultScriptEngineResolver scriptEngineResolver = new DefaultScriptEngineResolver();

    // when
    ScriptEngine first = scriptEngineResolver.getScriptEngineForLanguage(JAVASCRIPT);
    ScriptEngine second = scriptEngineResolver.getScriptEngineForLanguage(JAVASCRIPT);

    // then
    assertThat(first).isNotNull().isNotSameAs(second);
  }

  @Test
  public void shouldPassWrappedBindingsToOtherScriptEngine() {
    // given a script engine that is neither compilable nor closeable
    RecordingScriptEngine scriptEngine = new RecordingScriptEngine("other");
    ExpressionEvaluationHandler handler = createHandler(language -> scriptEngine);
    DmnExpressionImpl expression = createExpression("other", "x");

    // when
    Object firstResult = handler.evaluateExpression("other", expression, variables("x", 1));
    Object secondResult = handler.evaluateExpression("other", expression, variables("x", 2));

    // then the variables are resolved by the bindings on demand
    assertThat(firstResult).isEqualTo(1);
    assertThat(secondResult).isEqualTo(2);
    assertThat(scriptEngine.bindings).hasSize(2);
    assertThat(scriptEngine.bindings.get(0)).isInstanceOf(VariableContextScriptBindings.class);
    assertThat(scriptEngine.bindings.get(0).get("variableContext")).isInstanceOf(VariableContext.class);
  }

  @Test
  public void shouldNotCloseScriptEngineOfOtherLanguage() {
    // given a script engine that can be closed but is not provided by GraalJS
    CloseableScriptEngine scriptEngine = new CloseableScriptEngine("other");
    ExpressionEvaluationHandler handler = createHandler(language -> scriptEngine);
    DmnExpressionImpl expression = createExpression("other", "x");

    // when
    Object result = handler.evaluateExpression("other", expression, variables("x", 1));

    // then
    assertThat(result).isEqualTo(1);
    assertThat(scriptEngine.closeCount).isZero();
    assertThat(scriptEngine.bindings.get(0)).isInstanceOf(VariableContextScriptBindings.class);
  }

  @Test
  public void shouldNotCloseScriptEngineThatOnlyHasNameOfGraalJs() {
    // given a script engine that cannot be closed
    RecordingScriptEngine scriptEngine = new RecordingScriptEngine("Graal.js");
    ExpressionEvaluationHandler handler = createHandler(language -> scriptEngine);
    DmnExpressionImpl expression = createExpression(JAVASCRIPT, "x");

    // when
    Object result = handler.evaluateExpression(JAVASCRIPT, expression, variables("x", 1));

    // then it is used like the script engine of any other language
    assertThat(result).isEqualTo(1);
    assertThat(scriptEngine.bindings.get(0)).isInstanceOf(VariableContextScriptBindings.class);
  }

  // GraalJS ////////////////////////////////////////////////////////////////////

  @Test
  public void shouldCloseScriptEngineIfVariableCannotBeResolved() {
    // given
    List<ScriptEngine> scriptEngines = new ArrayList<>();
    ExpressionEvaluationHandler handler = createHandler(language -> {
      ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName(language);
      scriptEngines.add(scriptEngine);
      return scriptEngine;
    });
    VariableContext variableContext = Mockito.mock(VariableContext.class);
    Mockito.when(variableContext.keySet()).thenReturn(Collections.singleton("x"));
    Mockito.when(variableContext.resolve("x")).thenThrow(new IllegalStateException("expected failure"));

    // when
    assertThatThrownBy(() -> handler.evaluateExpression(JAVASCRIPT, createExpression(JAVASCRIPT, "1 + 1"), variableContext))
      .hasMessageContaining("expected failure");

    // then
    assertThat(scriptEngines).hasSize(1);
    assertThatThrownBy(() -> scriptEngines.get(0).eval("1 + 1")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldEvaluateWithScriptEngineResolverThatCachesScriptEngine() {
    // given a resolver that always provides the same script engine
    ScriptEngine scriptEngine = createGraalJsScriptEngine(true);
    ExpressionEvaluationHandler handler = createHandler(language -> scriptEngine);
    DmnExpressionImpl expression = createExpression(JAVASCRIPT, "probe.record(); x + 1");
    ContextProbe probe = new ContextProbe();

    // when
    for (int i = 0; i < 5; i++) {
      Object result = handler.evaluateExpression(JAVASCRIPT, expression,
          Variables.createVariables().putValue("x", i).putValue("probe", probe).asVariableContext());
      assertThat(result).isEqualTo(i + 1);
    }

    // then every evaluation had a context of its own that is closed now
    assertThat(probe.contexts).hasSize(5).doesNotHaveDuplicates();
    for (Context context : probe.contexts) {
      assertThatThrownBy(() -> context.eval("js", "1 + 1")).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  public void shouldEvaluateConcurrentlyWithScriptEngineResolverThatCachesScriptEngine() throws Exception {
    // given
    ScriptEngine scriptEngine = createGraalJsScriptEngine(true);
    ExpressionEvaluationHandler handler = createHandler(language -> scriptEngine);
    DmnExpressionImpl expression = createExpression(JAVASCRIPT, "probe.record(); var y = x; y * 2");
    ContextProbe probe = new ContextProbe();

    // when
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<Object>> results = new ArrayList<>();
      for (int i = 0; i < 40; i++) {
        final int x = i;
        results.add(executor.submit((Callable<Object>) () -> handler.evaluateExpression(JAVASCRIPT, expression,
            Variables.createVariables().putValue("x", x).putValue("probe", probe).asVariableContext())));
      }

      // then
      for (int i = 0; i < 40; i++) {
        assertThat(results.get(i).get()).isEqualTo(i * 2);
      }
    } finally {
      executor.shutdownNow();
    }
    assertThat(probe.contexts).hasSize(40).doesNotHaveDuplicates();
    for (Context context : probe.contexts) {
      assertThatThrownBy(() -> context.eval("js", "1 + 1")).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  public void shouldNotRaisePrivilegesByVariables() {
    // given a script engine that may not access Java
    ExpressionEvaluationHandler handler = createHandler(new DefaultScriptEngineResolver());
    DmnExpressionImpl harmless = createExpression(JAVASCRIPT, "x + 1");
    DmnExpressionImpl privileged = createExpression(JAVASCRIPT, "Java.type('java.lang.System').getProperty('user.name')");
    VariableMap variables = Variables.createVariables()
      .putValue("polyglot.js.allowAllAccess", true)
      .putValue("polyglot.js.allowHostAccess", true)
      .putValue("polyglot.js.allowHostClassLookup", true)
      .putValue("polyglot.js.allowIO", true)
      .putValue("polyglot.js.allowNativeAccess", true)
      .putValue("polyglot.js.allowCreateThread", true)
      .putValue("polyglot.js.allowHostClassLoading", true)
      .putValue("polyglot.js.nashorn-compat", true)
      .putValue("polyglot.js.unknownOption", true)
      .putValue("polyglot.context", "not a context")
      .putValue("x", 1);

    // when variables are named like options of GraalJS, then they are ignored
    assertThat(handler.evaluateExpression(JAVASCRIPT, harmless, variables.asVariableContext())).isEqualTo(2);
    assertThatThrownBy(() -> handler.evaluateExpression(JAVASCRIPT, privileged, variables.asVariableContext()))
      .hasStackTraceContaining("Java");

    // and do not affect later evaluations either
    assertThatThrownBy(() -> handler.evaluateExpression(JAVASCRIPT, privileged, variables("x", 1)))
      .hasStackTraceContaining("Java");
  }

  @Test
  public void shouldNotRaisePrivilegesOfCachedScriptEngineByVariables() {
    // given a resolver that always provides the same script engine, which may not access Java
    ScriptEngine scriptEngine = createGraalJsScriptEngine(false);
    ExpressionEvaluationHandler handler = createHandler(language -> scriptEngine);
    DmnExpressionImpl privileged = createExpression(JAVASCRIPT, "Java.type('java.lang.System').getProperty('user.name')");
    VariableContext variables = Variables.createVariables()
      .putValue("polyglot.js.allowAllAccess", true)
      .putValue("polyglot.js.allowHostAccess", true)
      .putValue("polyglot.js.allowHostClassLookup", true)
      .asVariableContext();

    for (int i = 0; i < 3; i++) {
      // when, then
      assertThatThrownBy(() -> handler.evaluateExpression(JAVASCRIPT, privileged, variables))
        .hasStackTraceContaining("Java");
    }
  }

  @Test
  public void shouldNotResolveReservedKeysFromVariableContext() throws ScriptException {
    // given bindings like they are passed to the script engines of other languages
    VariableContext variableContext = Variables.createVariables()
      .putValue("polyglot.js.allowAllAccess", true)
      .putValue("polyglot.js.allowHostClassLookup", true)
      .putValue("polyglot.context", "not a context")
      .putValue("x", 1)
      .asVariableContext();
    VariableContextScriptBindings bindings = VariableContextScriptBindings.wrap(new SimpleBindings(), variableContext);

    // then
    assertThat(bindings.containsKey("x")).isTrue();
    assertThat(bindings.get("x")).isEqualTo(1);
    assertThat(bindings.containsKey("polyglot.js.allowAllAccess")).isFalse();
    assertThat(bindings.get("polyglot.js.allowAllAccess")).isNull();
    assertThat(bindings.get("polyglot.context")).isNull();
    assertThat(bindings.keySet()).containsExactly("x");

    // but keys that were put into the bindings by the script engine are kept
    bindings.put("polyglot.context", "context");
    assertThat(bindings.get("polyglot.context")).isEqualTo("context");
    assertThat(bindings.keySet()).containsExactlyInAnyOrder("x", "polyglot.context");
  }

  @Test
  public void shouldNotRaisePrivilegesIfGraalJsIsGivenWrappedBindings() throws Exception {
    // given a GraalJS script engine that is used directly with the bindings of the DMN engine
    ScriptEngine scriptEngine = createGraalJsScriptEngine(false);
    try {
      VariableContext variableContext = Variables.createVariables()
        .putValue("polyglot.js.allowAllAccess", true)
        .putValue("polyglot.js.allowHostAccess", true)
        .putValue("polyglot.js.allowHostClassLookup", true)
        .putValue("x", 1)
        .asVariableContext();
      Bindings bindings = VariableContextScriptBindings.wrap(scriptEngine.createBindings(), variableContext);

      // when, then
      assertThat(scriptEngine.eval("x + 1", bindings)).isEqualTo(2);
      assertThatThrownBy(() -> scriptEngine.eval("Java.type('java.lang.System').getProperty('user.name')", bindings))
        .isInstanceOf(ScriptException.class);
    } finally {
      ((AutoCloseable) scriptEngine).close();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnUsableSet() {
    // given
    ExpressionEvaluationHandler handler = createHandler(new DefaultScriptEngineResolver());

    // when
    Object set = handler.evaluateExpression(JAVASCRIPT, createExpression(JAVASCRIPT, "new Set([1, 'two', {three: 3}, 1])"), variables("x", 1));
    Object nested = handler.evaluateExpression(JAVASCRIPT, createExpression(JAVASCRIPT, "({s: new Set([x]), e: new Set()})"), variables("x", 1));

    // then
    assertThat(set).isExactlyInstanceOf(LinkedHashSet.class);
    assertThat((Set<Object>) set).hasSize(3).contains(1, "two", Collections.singletonMap("three", 3));
    assertThat(((Map<String, Object>) nested).get("s")).isEqualTo(new LinkedHashSet<>(Arrays.asList(1)));
    assertThat(((Map<String, Object>) nested).get("e")).isEqualTo(new LinkedHashSet<>());
  }

  @Test
  public void shouldNotFailForDeeplyNestedResult() {
    // given
    ExpressionEvaluationHandler handler = createHandler(new DefaultScriptEngineResolver());
    DmnExpressionImpl expression = createExpression(JAVASCRIPT,
        "(function() { var root = {}; var o = root; for (var i = 0; i < 100000; i++) { o.n = {}; o = o.n; } return root; })()");

    // when
    Object result = handler.evaluateExpression(JAVASCRIPT, expression, variables("x", 1));

    // then the evaluation succeeds, whether the result could be copied or not
    assertThat(result).isInstanceOf(Map.class);
  }

  // helper /////////////////////////////////////////////////////////////////////

  protected ExpressionEvaluationHandler createHandler(DmnScriptEngineResolver scriptEngineResolver) {
    DefaultDmnEngineConfiguration configuration = new DefaultDmnEngineConfiguration();
    configuration.setScriptEngineResolver(scriptEngineResolver);
    configuration.init();
    return new ExpressionEvaluationHandler(configuration);
  }

  protected ScriptEngine createGraalJsScriptEngine(boolean allowHostAccess) {
    ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName(JAVASCRIPT);
    if (allowHostAccess) {
      scriptEngine.getContext().setAttribute("polyglot.js.allowHostAccess", true, ScriptContext.ENGINE_SCOPE);
    }
    return scriptEngine;
  }

  protected DmnExpressionImpl createExpression(String language, String text) {
    DmnExpressionImpl expression = new DmnExpressionImpl();
    expression.setExpression(text);
    expression.setExpressionLanguage(language);
    return expression;
  }

  protected VariableContext variables(String name, Object value) {
    return Variables.createVariables().putValue(name, value).asVariableContext();
  }

  public static class ContextProbe {

    public final List<Context> contexts = Collections.synchronizedList(new ArrayList<Context>());

    public void record() {
      contexts.add(Context.getCurrent());
    }
  }

  /**
   * A script engine that returns the value of the variable that is named by
   * the script.
   */
  protected static class RecordingScriptEngine extends AbstractScriptEngine {

    protected final String engineName;
    protected final List<Bindings> bindings = new ArrayList<>();

    public RecordingScriptEngine(String engineName) {
      this.engineName = engineName;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
      Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
      bindings.add(engineBindings);
      return engineBindings.get(script);
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
      throw new UnsupportedOperationException();
    }

    @Override
    public Bindings createBindings() {
      return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
      ScriptEngineFactory factory = Mockito.mock(ScriptEngineFactory.class);
      Mockito.when(factory.getEngineName()).thenReturn(engineName);
      Mockito.when(factory.getLanguageName()).thenReturn(engineName);
      return factory;
    }
  }

  protected static class CloseableScriptEngine extends RecordingScriptEngine implements AutoCloseable {

    protected int closeCount = 0;

    public CloseableScriptEngine(String engineName) {
      super(engineName);
    }

    @Override
    public void close() {
      closeCount++;
    }
  }

}
