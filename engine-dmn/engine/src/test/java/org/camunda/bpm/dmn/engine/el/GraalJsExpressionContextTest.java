/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.camunda.bpm.dmn.engine.impl.DefaultDmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.impl.DmnExpressionImpl;
import org.camunda.bpm.dmn.engine.impl.evaluation.ExpressionEvaluationHandler;
import org.camunda.bpm.dmn.engine.impl.spi.el.DmnScriptEngineResolver;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.context.VariableContext;
import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

/**
 * Regression test for the GraalJS polyglot context leak: a script expression
 * evaluated by a non-cachable script engine must run in the polyglot context
 * of that engine, and the engine must be closed afterwards.
 */
public class GraalJsExpressionContextTest {

  protected static final String LANGUAGE = "javascript";

  protected RecordingScriptEngineResolver scriptEngineResolver;
  protected ExpressionEvaluationHandler expressionEvaluationHandler;
  protected ContextProbe probe;

  @Before
  public void setUp() {
    scriptEngineResolver = new RecordingScriptEngineResolver();
    probe = new ContextProbe();

    DefaultDmnEngineConfiguration configuration = new DefaultDmnEngineConfiguration();
    configuration.setScriptEngineResolver(scriptEngineResolver);
    configuration.init();

    expressionEvaluationHandler = new ExpressionEvaluationHandler(configuration);
  }

  @Test
  public void shouldEvaluateExpressionInContextOfScriptEngine() {
    // given
    DmnExpressionImpl expression = createExpression("probe.record(); x + 1");

    // when
    Object result = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression, variables(41));

    // then the expression was evaluated by exactly one new script engine
    assertThat(result).isEqualTo(42);
    assertThat(scriptEngineResolver.engines).hasSize(1);
    assertThat(probe.contexts).hasSize(1);

    // and it ran in the polyglot context of that engine, not in an additional one
    GraalJSScriptEngine scriptEngine = (GraalJSScriptEngine) scriptEngineResolver.engines.get(0);
    assertThat(probe.contexts.get(0)).isEqualTo(scriptEngine.getPolyglotContext());
  }

  @Test
  public void shouldCloseScriptEngineAfterEvaluation() {
    // given
    DmnExpressionImpl expression = createExpression("probe.record(); x + 1");

    // when
    Object firstResult = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression, variables(1));
    Object secondResult = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression, variables(2));

    // then every evaluation used its own script engine and polyglot context
    assertThat(firstResult).isEqualTo(2);
    assertThat(secondResult).isEqualTo(3);
    assertThat(scriptEngineResolver.engines).hasSize(2).doesNotHaveDuplicates();
    assertThat(probe.contexts).hasSize(2).doesNotHaveDuplicates();

    // and all of them are closed
    for (Context context : probe.contexts) {
      assertThatThrownBy(() -> context.eval("js", "1 + 1"))
        .isInstanceOf(IllegalStateException.class);
    }

    // and no compiled script bound to one of the closed engines is cached
    assertThat(expression.getCachedCompiledScript()).isNull();
  }

  @Test
  public void shouldCloseScriptEngineAfterFailedEvaluation() {
    // given
    DmnExpressionImpl expression = createExpression("probe.record(); throw 'expected failure';");

    // when
    assertThatThrownBy(() -> expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression, variables(1)))
      .hasStackTraceContaining("expected failure");

    // then
    assertThat(probe.contexts).hasSize(1);
    assertThatThrownBy(() -> probe.contexts.get(0).eval("js", "1 + 1"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldProvideVariableContext() {
    // given
    DmnExpressionImpl expression = createExpression("variableContext.resolve('x').getValue()");

    // when
    Object result = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression, variables(7));

    // then
    assertThat(result).isEqualTo(7);
  }

  @Test
  public void shouldNotUseVariableAsScriptEngineOption() {
    // given a variable named like a GraalJS context option
    DmnExpressionImpl expression = createExpression("x + 1");
    VariableContext variableContext = Variables.createVariables()
      .putValue("polyglot.js.allowAllAccess", true)
      .putValue("x", 1)
      .asVariableContext();

    // when
    Object result = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression, variableContext);

    // then
    assertThat(result).isEqualTo(2);
  }

  @Test
  public void shouldEvaluateRepeatedlyWithDefaultScriptEngineResolver() {
    // given
    DefaultDmnEngineConfiguration configuration = new DefaultDmnEngineConfiguration();
    configuration.init();
    expressionEvaluationHandler = new ExpressionEvaluationHandler(configuration);
    DmnExpressionImpl expression = createExpression("x + 1");

    // when
    Object firstResult = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression,
        Variables.createVariables().putValue("x", 1).asVariableContext());
    Object secondResult = expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression,
        Variables.createVariables().putValue("x", 2).asVariableContext());

    // then
    assertThat(firstResult).isEqualTo(2);
    assertThat(secondResult).isEqualTo(3);
  }

  protected VariableContext variables(int x) {
    return Variables.createVariables()
      .putValue("x", x)
      .putValue("probe", probe)
      .asVariableContext();
  }

  protected DmnExpressionImpl createExpression(String text) {
    DmnExpressionImpl expression = new DmnExpressionImpl();
    expression.setExpression(text);
    expression.setExpressionLanguage(LANGUAGE);
    return expression;
  }

  public static class ContextProbe {

    public final List<Context> contexts = new ArrayList<>();

    public void record() {
      contexts.add(Context.getCurrent());
    }
  }

  /**
   * Provides a new script engine for every evaluation, like the process engine
   * does for script engines that are not cachable.
   */
  protected static class RecordingScriptEngineResolver implements DmnScriptEngineResolver {

    protected final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    protected final List<ScriptEngine> engines = new ArrayList<>();

    @Override
    public ScriptEngine getScriptEngineForLanguage(String language) {
      ScriptEngine scriptEngine = scriptEngineManager.getEngineByName(language);
      // allow the script to call the probe
      scriptEngine.getContext().setAttribute("polyglot.js.allowHostAccess", true, ScriptContext.ENGINE_SCOPE);
      engines.add(scriptEngine);
      return scriptEngine;
    }
  }

}
