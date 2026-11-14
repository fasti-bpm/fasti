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
package org.camunda.bpm.engine.test.bpmn.scripttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.camunda.bpm.engine.impl.scripting.engine.DefaultScriptEngineResolver;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptEngineResolver;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

/**
 * Regression test for the GraalJS polyglot context leak: every script
 * evaluation must run in the polyglot context that belongs to the (non-cached)
 * script engine created for it, and that engine must be closed afterwards.
 */
public class ScriptTaskGraalJsContextTest extends AbstractScriptTaskTest {

  protected static final String SCRIPT = "probe.record(); execution.setVariable('sum', 1 + 2);";

  protected ScriptEngineResolver defaultScriptEngineResolver;
  protected Map<Object, Object> defaultBeans;

  protected RecordingScriptEngineResolver scriptEngineResolver;
  protected ContextProbe probe;

  @Before
  public void setUp() {
    defaultScriptEngineResolver = processEngineConfiguration.getScriptEngineResolver();
    defaultBeans = processEngineConfiguration.getBeans();

    scriptEngineResolver = new RecordingScriptEngineResolver(defaultScriptEngineResolver.getScriptEngineManager());
    processEngineConfiguration.setScriptEngineResolver(scriptEngineResolver);

    probe = new ContextProbe();
    Map<Object, Object> beans = new HashMap<>();
    beans.put("probe", probe);
    processEngineConfiguration.setBeans(beans);
  }

  @After
  public void resetConfiguration() {
    processEngineConfiguration.setAutoStoreScriptVariables(false);
    processEngineConfiguration.setBeans(defaultBeans);
    processEngineConfiguration.setScriptEngineResolver(defaultScriptEngineResolver);
  }

  @Test
  public void shouldEvaluateScriptInContextOfScriptEngine() {
    // given
    deployProcess("javascript", SCRIPT);

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the script was evaluated by exactly one new script engine
    assertThat(runtimeService.getVariable(processInstance.getId(), "sum")).isEqualTo(3);
    assertThat(scriptEngineResolver.engines).hasSize(1);
    assertThat(probe.contexts).hasSize(1);

    // and it ran in the polyglot context of that engine, not in an additional one
    GraalJSScriptEngine scriptEngine = (GraalJSScriptEngine) scriptEngineResolver.engines.get(0);
    assertThat(probe.contexts.get(0)).isEqualTo(scriptEngine.getPolyglotContext());
  }

  @Test
  public void shouldCloseScriptEngineAfterEvaluation() {
    // given
    deployProcess("javascript", SCRIPT);

    // when
    runtimeService.startProcessInstanceByKey("testProcess");
    runtimeService.startProcessInstanceByKey("testProcess");

    // then every evaluation used its own script engine and polyglot context
    assertThat(scriptEngineResolver.engines).hasSize(2).doesNotHaveDuplicates();
    assertThat(probe.contexts).hasSize(2).doesNotHaveDuplicates();

    // and all of them are closed
    for (Context context : probe.contexts) {
      assertThatThrownBy(() -> context.eval("js", "1 + 1"))
        .isInstanceOf(IllegalStateException.class);
    }
    for (ScriptEngine scriptEngine : scriptEngineResolver.engines) {
      assertThatThrownBy(() -> scriptEngine.eval("1 + 1"))
        .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  public void shouldCloseScriptEngineAfterFailedEvaluation() {
    // given
    deployProcess("javascript", "probe.record(); throw 'expected failure';");

    // when
    assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("testProcess"))
      .hasMessageContaining("expected failure");

    // then
    assertThat(probe.contexts).hasSize(1);
    assertThatThrownBy(() -> probe.contexts.get(0).eval("js", "1 + 1"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldAutoStoreScriptVariables() {
    // given
    processEngineConfiguration.setAutoStoreScriptVariables(true);
    deployProcess("javascript", "var declared = 1; undeclared = 2; execution.setVariable('explicit', 3);");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the variables of the script are stored like the explicitly set variable
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnlyKeys("declared", "undeclared", "explicit")
      .containsEntry("declared", 1)
      .containsEntry("undeclared", 2)
      .containsEntry("explicit", 3);
  }

  @Test
  public void shouldNotAutoStoreScriptVariablesIfDisabled() {
    // given
    processEngineConfiguration.setAutoStoreScriptVariables(false);
    deployProcess("javascript", "var declared = 1; undeclared = 2; execution.setVariable('explicit', 3);");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then only explicitly set variables are stored
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnlyKeys("explicit")
      .containsEntry("explicit", 3);
  }

  public static class ContextProbe {

    public final List<Context> contexts = new ArrayList<>();

    public void record() {
      contexts.add(Context.getCurrent());
    }
  }

  protected static class RecordingScriptEngineResolver extends DefaultScriptEngineResolver {

    protected final List<ScriptEngine> engines = new ArrayList<>();

    public RecordingScriptEngineResolver(ScriptEngineManager scriptEngineManager) {
      super(scriptEngineManager);
    }

    @Override
    public ScriptEngine getScriptEngine(String language, boolean resolveFromCache) {
      ScriptEngine scriptEngine = super.getScriptEngine(language, resolveFromCache);
      engines.add(scriptEngine);
      return scriptEngine;
    }
  }

}
