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
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;

import org.camunda.bpm.engine.history.HistoricVariableUpdate;
import org.camunda.bpm.engine.impl.scripting.engine.DefaultScriptEngineResolver;
import org.camunda.bpm.engine.impl.scripting.engine.ScriptEngineResolver;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaScript;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Asserts which variables of a script that is evaluated by GraalJS are stored
 * as process variables, if the process engine is configured to store the
 * variables of scripts.
 */
public class ScriptTaskGraalJsAutoStoreTest extends AbstractScriptTaskTest {

  protected static final String JAVASCRIPT = "javascript";

  protected ScriptEngineResolver defaultScriptEngineResolver;
  protected Map<Object, Object> defaultBeans;
  protected ContextProbe probe;

  @Before
  public void setUp() {
    defaultScriptEngineResolver = processEngineConfiguration.getScriptEngineResolver();
    defaultBeans = processEngineConfiguration.getBeans();

    probe = new ContextProbe();
    Map<Object, Object> beans = new HashMap<>();
    beans.put("probe", probe);
    processEngineConfiguration.setBeans(beans);

    processEngineConfiguration.setAutoStoreScriptVariables(true);
  }

  @After
  public void resetConfiguration() {
    processEngineConfiguration.setAutoStoreScriptVariables(false);
    processEngineConfiguration.setBeans(defaultBeans);
    processEngineConfiguration.setScriptEngineResolver(defaultScriptEngineResolver);
  }

  @Test
  public void shouldNotStoreScriptVariablesIfDisabled() {
    // given
    processEngineConfiguration.setAutoStoreScriptVariables(false);
    deployProcess(JAVASCRIPT, "var declared = 1; undeclared = 2; x = x + 1; execution.setVariable('explicit', 3);");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("x", 1));

    // then only the variable that the script has set explicitly is stored
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnly(entry("x", 1), entry("explicit", 3));
  }

  @Test
  public void shouldStoreGlobalVariablesOfScript() {
    // given
    deployProcess(JAVASCRIPT,
        "var declared = 1;"
      + "undeclared = 'a';"
      + "this.property = true;"
      + "var uninitialized;"
      + "let scoped = 2;"
      + "const constant = 3;"
      + "(function() { var local = 4; })();");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnly(entry("declared", 1), entry("undeclared", "a"), entry("property", true), entry("uninitialized", null));
  }

  @Test
  public void shouldStoreNothingForScriptWithoutVariables() {
    // given
    deployProcess(JAVASCRIPT, "probe.record(); 1 + 1");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("x", 1));

    // then
    assertThat(runtimeService.getVariables(processInstance.getId())).containsOnly(entry("x", 1));
    assertThat(probe.contexts).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldStoreJavaScriptValuesAsJavaValues() {
    // given
    deployProcess(JAVASCRIPT,
        "var object = {a: 1, list: ['x', 2]};"
      + "var array = [1, {b: true}];"
      + "var date = new Date(86400000);"
      + "var number = 1.5;");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the variables do not depend on the script engine, which is closed
    Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
    assertThat(variables).containsOnlyKeys("object", "array", "date", "number");
    assertThat(variables.get("object")).isExactlyInstanceOf(LinkedHashMap.class);
    assertThat((Map<String, Object>) variables.get("object"))
      .containsExactly(entry("a", 1), entry("list", Arrays.asList("x", 2)));
    assertThat(variables.get("array")).isExactlyInstanceOf(ArrayList.class)
      .isEqualTo(Arrays.asList(1, Collections.singletonMap("b", true)));
    assertThat(variables.get("date")).isEqualTo(new Date(86400000));
    assertThat(variables.get("number")).isEqualTo(1.5);
  }

  @Test
  public void shouldNotStoreValuesThatDependOnScriptEngine() {
    // given
    deployProcess(JAVASCRIPT,
        "probe.record();"
      + "function declaredFunction() { return 1; }"
      + "var assignedFunction = function() { return 2; };"
      + "var arrow = () => 3;"
      + "var symbol = Symbol('s');"
      + "var regExp = /x/;"
      + "var objectWithFunction = {a: 1, f: function() {}};"
      + "var arrayWithFunction = [1, [function() {}]];"
      + "var stored = declaredFunction() + assignedFunction() + arrow();");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertThat(runtimeService.getVariables(processInstance.getId())).containsOnly(entry("stored", 6));
    assertThat(probe.contexts).hasSize(1);
  }

  @Test
  public void shouldNotStoreNamesThatAreProvidedToScripts() {
    // given a script that replaces what the process engine and GraalJS provide
    deployProcess(JAVASCRIPT,
        "execution.setVariable('explicit', 1);"
      + "execution = 'replaced';"
      + "print = 'replaced';"
      + "this['polyglot.context'] = 'replaced';"
      + "this['polyglot.js.allowIO'] = true;"
      + "probe = 'replaced';");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then only the names that are not reserved are stored
    Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
    assertThat(variables).containsOnlyKeys("probe", "explicit");
    assertThat(variables).containsEntry("probe", "replaced").containsEntry("explicit", 1);
  }

  @Test
  public void shouldNotStoreVariablesThatAreNotAssigned() {
    // given variables of types that JavaScript does not have
    VariableMap variables = Variables.createVariables()
      .putValue("aLong", 5L)
      .putValue("aShort", (short) 6)
      .putValue("aDouble", 7.0d)
      .putValue("aString", "a")
      .putValue("aDate", new Date(0))
      .putValue("aList", new ArrayList<>(Arrays.asList(1, 2)))
      .putValue("aNull", null);
    deployProcess(JAVASCRIPT, "var sum = aLong + aShort + aDouble + aString.length + aList.size() + aDate.getTime();");

    // when a script reads them
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess", variables);

    // then they keep their value and type
    VariableMap storedVariables = runtimeService.getVariablesTyped(processInstance.getId());
    assertThat(storedVariables).containsOnlyKeys("aLong", "aShort", "aDouble", "aString", "aDate", "aList", "aNull", "sum");
    for (String name : variables.keySet()) {
      Object value = variables.get(name);
      assertThat(storedVariables.get(name)).as(name).isEqualTo(value);
      if (value != null) {
        assertThat(storedVariables.get(name)).as(name).isExactlyInstanceOf(value.getClass());
      }
    }
    assertThat(((Number) storedVariables.get("sum")).intValue()).isEqualTo(21);

    // and are not written again
    List<String> updatedVariables = new ArrayList<>();
    historyService.createHistoricDetailQuery().variableUpdates().processInstanceId(processInstance.getId()).list()
      .forEach(detail -> updatedVariables.add(((HistoricVariableUpdate) detail).getVariableName()));
    assertThat(updatedVariables).containsExactlyInAnyOrder("aLong", "aShort", "aDouble", "aString", "aDate", "aList", "aNull", "sum");
  }

  @Test
  public void shouldStoreVariablesThatAreAssigned() {
    // given
    deployProcess(JAVASCRIPT, "counter = counter + 1; name = 'b'; var flag = false; removed = null;");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables()
          .putValue("counter", 1)
          .putValue("name", "a")
          .putValue("flag", true)
          .putValue("removed", "value")
          .putValue("untouched", "same"));

    // then
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnly(entry("counter", 2), entry("name", "b"), entry("flag", false), entry("removed", null), entry("untouched", "same"));
  }

  @Test
  public void shouldKeepVariablesThatAreSetExplicitly() {
    // given a script that sets variables which have the name of a script variable
    deployProcess(JAVASCRIPT,
        "var created = {a: 1};"
      + "execution.setVariable('created', 'explicit');"
      + "existing = existing + 1;"
      + "execution.setVariable('existing', 'explicit');"
      + "var other = 2;");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables().putValue("existing", 1));

    // then the explicit values are stored
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnly(entry("created", "explicit"), entry("existing", "explicit"), entry("other", 2));
  }

  @Test
  public void shouldStoreVariablesOfScriptOfListener() {
    // given a script task with an execution listener that is a script as well
    BpmnModelInstance process = createProcess(JAVASCRIPT, "var fromTask = (typeof listenerVariable) + fromListener;");
    CamundaScript listenerScript = process.newInstance(CamundaScript.class);
    listenerScript.setCamundaScriptFormat(JAVASCRIPT);
    listenerScript.setTextContent("var fromListener = 1; let listenerVariable = 2;");
    CamundaExecutionListener listener = process.newInstance(CamundaExecutionListener.class);
    listener.setCamundaEvent("start");
    listener.setCamundaScript(listenerScript);
    process.getModelElementsByType(ScriptTask.class).iterator().next().builder().addExtensionElement(listener);
    deployProcess(process);

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the variable of the listener is stored and available to the script of the task
    assertThat(runtimeService.getVariables(processInstance.getId()))
      .containsOnly(entry("fromListener", 1), entry("fromTask", "undefined1"));
  }

  @Test
  public void shouldStoreScriptVariablesIfScriptEngineIsCached() {
    // given a resolver that hands out the same script engine for every evaluation
    processEngineConfiguration.setScriptEngineResolver(
        new DefaultScriptEngineResolver(defaultScriptEngineResolver.getScriptEngineManager()) {
          @Override
          protected boolean isCachable(ScriptEngine scriptEngine) {
            return true;
          }
        });
    deployProcess(JAVASCRIPT, "probe.record(); var next = (typeof previous) + x; var previous = x;");

    for (int i = 0; i < 3; i++) {
      // when
      ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess",
          Variables.createVariables().putValue("x", i));

      // then the variables of the evaluation are stored and no variables of the evaluations before
      assertThat(runtimeService.getVariables(processInstance.getId()))
        .containsOnly(entry("x", i), entry("next", "undefined" + i), entry("previous", i));
    }
    assertThat(probe.contexts).hasSize(3).doesNotHaveDuplicates();
  }

  public static class ContextProbe {

    public final List<Context> contexts = new ArrayList<>();

    public void record() {
      contexts.add(Context.getCurrent());
    }
  }

}
