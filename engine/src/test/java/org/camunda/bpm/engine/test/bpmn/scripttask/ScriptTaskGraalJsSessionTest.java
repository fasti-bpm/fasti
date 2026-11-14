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
package org.camunda.bpm.engine.test.bpmn.scripttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.camunda.bpm.engine.ScriptEvaluationException;
import org.camunda.bpm.engine.delegate.VariableScope;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.scripting.ExecutableScript;
import org.camunda.bpm.engine.impl.scripting.engine.DefaultScriptEngineResolver;
import org.camunda.bpm.engine.impl.scripting.engine.Resolver;
import org.camunda.bpm.engine.impl.scripting.engine.ResolverFactory;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptBindings;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptEngineResolver;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptingEngines;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaScript;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Asserts which script engines are closed after the evaluation of a script,
 * that all other script engines are used like before and that the variables
 * of a process cannot configure a GraalJS script engine.
 */
public class ScriptTaskGraalJsSessionTest extends AbstractScriptTaskTest {

  protected static final String JAVASCRIPT = "javascript";
  protected static final String OTHER_LANGUAGE = "other";

  protected ScriptEngineResolver defaultScriptEngineResolver;
  protected Map<Object, Object> defaultBeans;
  protected List<ResolverFactory> resolverFactories;
  protected ResolverFactory additionalResolverFactory;

  protected ContextProbe probe;
  protected String deploymentId;

  @Before
  public void setUp() {
    defaultScriptEngineResolver = processEngineConfiguration.getScriptEngineResolver();
    defaultBeans = processEngineConfiguration.getBeans();
    resolverFactories = processEngineConfiguration.getScriptingEngines().getScriptBindingsFactory().getResolverFactories();

    probe = new ContextProbe();
    Map<Object, Object> beans = new HashMap<>();
    beans.put("probe", probe);
    processEngineConfiguration.setBeans(beans);
  }

  @After
  public void resetConfiguration() {
    if (deploymentId != null) {
      repositoryService.deleteDeployment(deploymentId, true);
    }
    if (additionalResolverFactory != null) {
      resolverFactories.remove(additionalResolverFactory);
    }
    processEngineConfiguration.setAutoStoreScriptVariables(false);
    processEngineConfiguration.setConfigureScriptEngineHostAccess(true);
    processEngineConfiguration.setBeans(defaultBeans);
    processEngineConfiguration.setScriptEngineResolver(defaultScriptEngineResolver);
  }

  // script engines of other languages //////////////////////////////////////////

  @Test
  public void shouldPassScriptBindingsToOtherScriptEngine() {
    // given a script engine that is neither cachable nor closeable
    List<RecordingScriptEngine> scriptEngines = new ArrayList<>();
    useScriptEngineResolver(new TestScriptEngineResolver() {
      @Override
      public ScriptEngine getScriptEngine(String language, boolean resolveFromCache) {
        scriptEngines.add(new RecordingScriptEngine(OTHER_LANGUAGE));
        return scriptEngines.get(scriptEngines.size() - 1);
      }
    });
    deployProcess(OTHER_LANGUAGE, "execution");

    // when
    runtimeService.startProcessInstanceByKey("testProcess");
    runtimeService.startProcessInstanceByKey("testProcess");

    // then every evaluation got a new script engine and bindings that resolve the variables on demand
    assertThat(scriptEngines).hasSize(2);
    for (RecordingScriptEngine scriptEngine : scriptEngines) {
      assertThat(scriptEngine.bindings).hasSize(1);
      assertThat(scriptEngine.bindings.get(0)).isInstanceOf(ScriptBindings.class);
      assertThat(scriptEngine.results.get(0)).isInstanceOf(ExecutionEntity.class);
    }
  }

  @Test
  public void shouldNotCloseScriptEngineOfOtherLanguage() {
    // given a script engine that can be closed but is not provided by GraalJS
    CloseableScriptEngine scriptEngine = new CloseableScriptEngine(OTHER_LANGUAGE);
    useScriptEngineResolver(new TestScriptEngineResolver() {
      @Override
      public ScriptEngine getScriptEngine(String language, boolean resolveFromCache) {
        return scriptEngine;
      }
    });
    deployProcess(OTHER_LANGUAGE, "execution");

    // when
    runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertThat(scriptEngine.closeCount).isZero();
    assertThat(scriptEngine.bindings.get(0)).isInstanceOf(ScriptBindings.class);
  }

  @Test
  public void shouldNotCloseScriptEngineThatOnlyHasNameOfGraalJs() {
    // given a script engine that cannot be closed
    RecordingScriptEngine scriptEngine = new RecordingScriptEngine(ScriptingEngines.GRAAL_JS_SCRIPT_ENGINE_NAME);
    useScriptEngineResolver(new TestScriptEngineResolver() {
      @Override
      public ScriptEngine getScriptEngine(String language, boolean resolveFromCache) {
        return scriptEngine;
      }
    });
    deployProcess(JAVASCRIPT, "execution");

    // when
    runtimeService.startProcessInstanceByKey("testProcess");

    // then it is used like the script engine of any other language
    assertThat(scriptEngine.bindings.get(0)).isInstanceOf(ScriptBindings.class);
  }

  @Test
  public void shouldAutoStoreScriptVariablesOfOtherLanguage() {
    // given
    processEngineConfiguration.setAutoStoreScriptVariables(true);
    deployProcess("groovy", "sum = x + 1");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("x", 1));

    // then
    assertThat(runtimeService.getVariable(processInstance.getId(), "sum")).isEqualTo(2);
  }

  @Test
  public void shouldCacheScriptEngineOfOtherLanguage() {
    // given
    ScriptingEngines scriptingEngines = processEngineConfiguration.getScriptingEngines();
    deployProcess("groovy", "execution.setVariable('sum', x + 1)");

    // when
    runtimeService.startProcessInstanceByKey("testProcess", Variables.createVariables().putValue("x", 1));
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("x", 2));

    // then
    assertThat(runtimeService.getVariable(processInstance.getId(), "sum")).isEqualTo(3);
    ScriptEngine first = processEngineConfiguration.getCommandExecutorTxRequired()
        .execute(commandContext -> scriptingEngines.getScriptEngineForLanguage("groovy"));
    ScriptEngine second = processEngineConfiguration.getCommandExecutorTxRequired()
        .execute(commandContext -> scriptingEngines.getScriptEngineForLanguage("groovy"));
    assertThat(first).isSameAs(second);
  }

  // GraalJS ////////////////////////////////////////////////////////////////////

  @Test
  public void shouldProvideVariablesAndBeans() {
    // given
    deployProcess(JAVASCRIPT, "probe.record(); execution.setVariable('result', x + name + (typeof unknown));");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("x", 1).putValue("name", "a"));

    // then
    assertThat(runtimeService.getVariable(processInstance.getId(), "result")).isEqualTo("1aundefined");
    assertThat(probe.contexts).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldStoreJavaScriptValuesSetAsVariables() {
    // given a script that sets objects of its language as variables
    deployProcess(JAVASCRIPT,
        "execution.setVariable('object', {a: 1, list: ['x', 2]});"
      + "execution.setVariableLocal('array', [1, {b: true}]);"
      + "execution.setVariables({nested: {c: 3}, number: 4});");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the variables are stored as Java collections that do not depend on the script engine
    Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
    assertThat(variables).containsOnlyKeys("object", "array", "nested", "number");
    assertThat(variables.get("object")).isExactlyInstanceOf(LinkedHashMap.class);
    assertThat((Map<String, Object>) variables.get("object"))
      .containsExactly(entry("a", 1), entry("list", Arrays.asList("x", 2)));
    assertThat(variables.get("array")).isExactlyInstanceOf(ArrayList.class)
      .isEqualTo(Arrays.asList(1, Collections.singletonMap("b", true)));
    assertThat(variables.get("nested")).isEqualTo(Collections.singletonMap("c", 3));
    assertThat(variables.get("number")).isEqualTo(4);
  }

  @Test
  public void shouldCloseScriptEngineIfBindingsCannotBeCreated() {
    // given a name that fails to be resolved
    RecordingScriptEngineResolver scriptEngineResolver = new RecordingScriptEngineResolver();
    useScriptEngineResolver(scriptEngineResolver);
    additionalResolverFactory = new FailingResolverFactory();
    resolverFactories.add(additionalResolverFactory);
    deployProcess(JAVASCRIPT, "1 + 1");

    // when
    assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("testProcess"))
      .hasMessageContaining("expected failure");

    // then
    assertThat(scriptEngineResolver.engines).hasSize(1);
    assertThatThrownBy(() -> scriptEngineResolver.engines.get(0).eval("1 + 1"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldEvaluateScriptsOfNestedInvocationsInSeparateContexts() {
    // given a script task with an execution listener that is a script as well
    BpmnModelInstance process = createProcess(JAVASCRIPT,
        "probe.record(); execution.setVariable('task', typeof listenerVariable + fromListener);");
    CamundaScript listenerScript = process.newInstance(CamundaScript.class);
    listenerScript.setCamundaScriptFormat(JAVASCRIPT);
    listenerScript.setTextContent("probe.record(); var listenerVariable = 1; execution.setVariable('fromListener', 'x');");
    CamundaExecutionListener listener = process.newInstance(CamundaExecutionListener.class);
    listener.setCamundaEvent("start");
    listener.setCamundaScript(listenerScript);
    process.getModelElementsByType(ScriptTask.class).iterator().next().builder().addExtensionElement(listener);
    deployProcess(process);

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertThat(runtimeService.getVariable(processInstance.getId(), "task")).isEqualTo("undefinedx");
    assertThat(probe.contexts).hasSize(2).doesNotHaveDuplicates();
    for (Context context : probe.contexts) {
      assertClosed(context);
    }
  }

  @Test
  public void shouldEvaluateConditions() {
    // given scripts that are not the script of a script task
    String process =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
      + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
      + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
      + "    targetNamespace=\"http://camunda.org/schema/1.0/bpmn\">"
      + "  <process id=\"testProcess\" isExecutable=\"true\">"
      + "    <startEvent id=\"start\" />"
      + "    <sequenceFlow sourceRef=\"start\" targetRef=\"gateway\" />"
      + "    <exclusiveGateway id=\"gateway\" />"
      + "    <sequenceFlow sourceRef=\"gateway\" targetRef=\"big\">"
      + "      <conditionExpression xsi:type=\"tFormalExpression\" language=\"javascript\">probe.record(); x &gt; 10</conditionExpression>"
      + "    </sequenceFlow>"
      + "    <sequenceFlow sourceRef=\"gateway\" targetRef=\"small\">"
      + "      <conditionExpression xsi:type=\"tFormalExpression\" language=\"javascript\">probe.record(); x &lt;= 10</conditionExpression>"
      + "    </sequenceFlow>"
      + "    <userTask id=\"big\" />"
      + "    <userTask id=\"small\" />"
      + "  </process>"
      + "</definitions>";
    deploymentId = repositoryService.createDeployment().addString("testProcess.bpmn", process).deploy().getId();

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("x", 11));

    // then
    assertThat(taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult().getTaskDefinitionKey())
      .isEqualTo("big");
    assertThat(probe.contexts).isNotEmpty().doesNotHaveDuplicates();
    for (Context context : probe.contexts) {
      assertClosed(context);
    }
  }

  @Test
  public void shouldEvaluateWithScriptEngineResolverThatCachesScriptEngine() {
    // given a resolver that caches the GraalJS script engine
    CachingScriptEngineResolver scriptEngineResolver = new CachingScriptEngineResolver();
    useScriptEngineResolver(scriptEngineResolver);
    deployProcess(JAVASCRIPT, "probe.record(); execution.setVariable('sum', x + 1);");

    // when
    for (int i = 0; i < 4; i++) {
      ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
          Variables.createVariables().putValue("x", i));

      // then
      assertThat(runtimeService.getVariable(processInstance.getId(), "sum")).isEqualTo(i + 1);
    }

    // and all evaluations got the same script engine
    assertThat(scriptEngineResolver.engines).hasSize(4);
    assertThat(scriptEngineResolver.engines.stream().distinct()).hasSize(1);

    // but a context of their own that is closed now
    assertThat(probe.contexts).hasSize(4).doesNotHaveDuplicates();
    for (Context context : probe.contexts) {
      assertClosed(context);
    }
  }

  // variables that are named like options of GraalJS ///////////////////////////

  @Test
  public void shouldNotRaisePrivilegesByVariables() {
    // given scripts that may not access Java
    processEngineConfiguration.setConfigureScriptEngineHostAccess(false);
    useScriptEngineResolver(new TestScriptEngineResolver(new ScriptEngineManager()));
    Map<String, Object> variables = optionVariables();

    // when a script does not need access, then it can be evaluated
    assertThat(evaluate("x + 1", variables)).isEqualTo(2);

    // when a script needs access, then it fails
    assertThatThrownBy(() -> evaluate("execution.getId()", variables))
      .isInstanceOf(ScriptEvaluationException.class);
    assertThatThrownBy(() -> evaluate("Java.type('java.lang.System').getProperty('user.name')", variables))
      .isInstanceOf(ScriptEvaluationException.class);
    assertThatThrownBy(() -> evaluate("load('http://localhost:1/script.js')", variables))
      .isInstanceOf(ScriptEvaluationException.class);
  }

  @Test
  public void shouldNotRaisePrivilegesByVariablesIfScriptEngineIsCached() {
    // given scripts that may not access Java
    processEngineConfiguration.setConfigureScriptEngineHostAccess(false);
    useScriptEngineResolver(new CachingScriptEngineResolver(new ScriptEngineManager()));
    Map<String, Object> variables = optionVariables();

    for (int i = 0; i < 3; i++) {
      // when, then
      assertThat(evaluate("x + 1", variables)).isEqualTo(2);
      assertThatThrownBy(() -> evaluate("Java.type('java.lang.System').getProperty('user.name')", variables))
        .isInstanceOf(ScriptEvaluationException.class);
    }
  }

  @Test
  public void shouldIgnoreVariablesNamedLikeOptions() {
    // given
    deployProcess(JAVASCRIPT, "execution.setVariable('sum', x + 1);");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess", optionVariables());

    // then the script is evaluated and the variables are untouched
    assertThat(runtimeService.getVariable(processInstance.getId(), "sum")).isEqualTo(2);
    assertThat(runtimeService.getVariable(processInstance.getId(), "polyglot.js.allowAllAccess")).isEqualTo(true);
    assertThat(runtimeService.getVariable(processInstance.getId(), "polyglot.context")).isEqualTo("not a context");
  }

  @Test
  public void shouldNotResolveReservedKeysByScriptBindings() {
    // given bindings like they are passed to the script engines of other languages
    Map<String, Object> variables = optionVariables();
    processEngineConfiguration.setAutoStoreScriptVariables(true);
    String processInstanceId = startProcessInstance(variables);

    processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      ExecutionEntity execution = commandContext.getExecutionManager().findExecutionById(processInstanceId);
      Bindings bindings = processEngineConfiguration.getScriptingEngines().createBindings(execution);

      // then
      assertThat(bindings.containsKey("x")).isTrue();
      assertThat(bindings.get("x")).isEqualTo(1);
      assertThat(bindings.keySet()).contains("x", "execution").noneMatch(key -> key.startsWith("polyglot."));
      for (String name : variables.keySet()) {
        if (!"x".equals(name)) {
          assertThat(bindings.containsKey(name)).isFalse();
          assertThat(bindings.get(name)).isNull();
        }
      }

      // and a reserved key that is put by a script engine is not stored as variable
      bindings.put("polyglot.context", "context");
      assertThat(bindings.get("polyglot.context")).isEqualTo("context");
      assertThat(execution.getVariable("polyglot.context")).isEqualTo("not a context");
      return null;
    });
  }

  @Test
  public void shouldNotRaisePrivilegesIfGraalJsIsGivenScriptBindings() {
    // given a GraalJS script engine that is used with bindings that wrap its own bindings,
    // which is still possible for callers of the scripting environment
    processEngineConfiguration.setConfigureScriptEngineHostAccess(false);
    processEngineConfiguration.setAutoStoreScriptVariables(true);
    useScriptEngineResolver(new TestScriptEngineResolver(new ScriptEngineManager()));
    String processInstanceId = startProcessInstance(optionVariables());

    processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      ExecutionEntity execution = commandContext.getExecutionManager().findExecutionById(processInstanceId);
      ScriptingEngines scriptingEngines = processEngineConfiguration.getScriptingEngines();
      ScriptEngine scriptEngine = scriptingEngines.getScriptEngineForLanguage(JAVASCRIPT);
      try {
        Bindings bindings = scriptingEngines.createBindings(scriptEngine, execution);

        // when, then
        assertThat(processEngineConfiguration.getScriptingEnvironment()
            .execute(createScript("x + 1"), execution, bindings, scriptEngine)).isEqualTo(2);
        assertThatThrownBy(() -> processEngineConfiguration.getScriptingEnvironment()
            .execute(createScript("Java.type('java.lang.System').getProperty('user.name')"), execution, bindings, scriptEngine))
          .isInstanceOf(ScriptEvaluationException.class);
      } finally {
        close(scriptEngine);
      }
      return null;
    });
  }

  // helper /////////////////////////////////////////////////////////////////////

  protected Map<String, Object> optionVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("polyglot.js.allowAllAccess", true);
    variables.put("polyglot.js.allowHostAccess", true);
    variables.put("polyglot.js.allowHostClassLookup", true);
    variables.put("polyglot.js.allowIO", true);
    variables.put("polyglot.js.allowNativeAccess", true);
    variables.put("polyglot.js.allowCreateThread", true);
    variables.put("polyglot.js.allowHostClassLoading", true);
    variables.put("polyglot.js.nashorn-compat", true);
    variables.put("polyglot.js.unknownOption", true);
    variables.put("polyglot.context", "not a context");
    variables.put("x", 1);
    return variables;
  }

  protected String startProcessInstance(Map<String, Object> variables) {
    deployProcess(Bpmn.createExecutableProcess("testProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done());
    return runtimeService.startProcessInstanceByKey("testProcess", variables).getId();
  }

  protected Object evaluate(String scriptText, Map<String, Object> variables) {
    String processInstanceId = startProcessInstance(variables);

    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      ExecutionEntity execution = commandContext.getExecutionManager().findExecutionById(processInstanceId);
      return processEngineConfiguration.getScriptingEnvironment().execute(createScript(scriptText), execution);
    });
  }

  protected ExecutableScript createScript(String scriptText) {
    return processEngineConfiguration.getScriptFactory().createScriptFromSource(JAVASCRIPT, scriptText);
  }

  protected void useScriptEngineResolver(ScriptEngineResolver scriptEngineResolver) {
    processEngineConfiguration.setScriptEngineResolver(scriptEngineResolver);
  }

  protected void assertClosed(Context context) {
    assertThatThrownBy(() -> context.eval("js", "1 + 1")).isInstanceOf(IllegalStateException.class);
  }

  protected void close(ScriptEngine scriptEngine) {
    try {
      ((AutoCloseable) scriptEngine).close();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static class ContextProbe {

    public final List<Context> contexts = new ArrayList<>();

    public void record() {
      contexts.add(Context.getCurrent());
    }
  }

  /**
   * Note that all script engines of a script engine manager share one polyglot
   * engine, which does not accept contexts that differ in their access to
   * Java. A test that restricts the access needs a script engine manager of
   * its own.
   */
  protected class TestScriptEngineResolver extends DefaultScriptEngineResolver {

    public TestScriptEngineResolver() {
      this(defaultScriptEngineResolver.getScriptEngineManager());
    }

    public TestScriptEngineResolver(ScriptEngineManager scriptEngineManager) {
      super(scriptEngineManager);
    }
  }

  protected class RecordingScriptEngineResolver extends TestScriptEngineResolver {

    protected final List<ScriptEngine> engines = new ArrayList<>();

    public RecordingScriptEngineResolver() {
      super();
    }

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

  /**
   * Caches all script engines, also the ones that do not declare to be thread-safe.
   */
  protected class CachingScriptEngineResolver extends RecordingScriptEngineResolver {

    public CachingScriptEngineResolver() {
      super();
    }

    public CachingScriptEngineResolver(ScriptEngineManager scriptEngineManager) {
      super(scriptEngineManager);
    }

    @Override
    protected boolean isCachable(ScriptEngine scriptEngine) {
      return true;
    }
  }

  protected static class FailingResolverFactory implements ResolverFactory {

    @Override
    public Resolver createResolver(VariableScope variableScope) {
      return new Resolver() {

        @Override
        public boolean containsKey(Object key) {
          return "failing".equals(key);
        }

        @Override
        public Object get(Object key) {
          throw new IllegalStateException("expected failure");
        }

        @Override
        public Set<String> keySet() {
          return Collections.singleton("failing");
        }
      };
    }
  }

  /**
   * A script engine that returns the value of the variable that is named by
   * the script.
   */
  protected static class RecordingScriptEngine extends AbstractScriptEngine {

    protected final String engineName;
    protected final List<Bindings> bindings = new ArrayList<>();
    protected final List<Object> results = new ArrayList<>();

    public RecordingScriptEngine(String engineName) {
      this.engineName = engineName;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
      Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
      bindings.add(engineBindings);
      results.add(engineBindings.get(script));
      return null;
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
      Mockito.when(factory.getNames()).thenReturn(Arrays.asList(engineName));
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
