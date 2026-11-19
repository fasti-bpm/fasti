/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
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
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.scripting.ExecutableScript;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Test;

/**
 * The script engine of a GraalJS script is closed after the evaluation.
 * JavaScript objects and arrays returned by the script must still be usable
 * afterwards.
 */
public class ScriptTaskGraalJsResultTest extends AbstractScriptTaskTest {

  protected static final String LANGUAGE = "javascript";

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnUsableObjectLiteral() {
    // when
    Object result = evaluate("({a: 1, b: 'x', c: true, d: null, e: 1.5})");

    // then
    assertThat(result).isExactlyInstanceOf(LinkedHashMap.class);
    assertThat((Map<String, Object>) result).containsExactly(
        entry("a", 1), entry("b", "x"), entry("c", true), entry("d", null), entry("e", 1.5));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnUsableNestedObject() {
    // when
    Object result = evaluate("({n: {c: [1, 2, {d: true}]}, list: [[1], {}]})");

    // then
    assertThat(result).isExactlyInstanceOf(LinkedHashMap.class);
    Map<String, Object> map = (Map<String, Object>) result;
    assertThat(map).containsOnlyKeys("n", "list");

    Map<String, Object> nested = (Map<String, Object>) map.get("n");
    assertThat(nested).isExactlyInstanceOf(LinkedHashMap.class).containsOnlyKeys("c");

    List<Object> nestedList = (List<Object>) nested.get("c");
    assertThat(nestedList).isExactlyInstanceOf(ArrayList.class).hasSize(3).startsWith(1, 2);
    assertThat((Map<String, Object>) nestedList.get(2)).containsExactly(entry("d", true));

    List<Object> list = (List<Object>) map.get("list");
    assertThat(list).hasSize(2);
    assertThat(list.get(0)).isEqualTo(Arrays.asList(1));
    assertThat((Map<String, Object>) list.get(1)).isExactlyInstanceOf(LinkedHashMap.class).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnUsableArray() {
    // when
    Object result = evaluate("[1, 'two', {three: 3}]");

    // then
    assertThat(result).isExactlyInstanceOf(ArrayList.class);
    List<Object> list = (List<Object>) result;
    assertThat(list).hasSize(3).startsWith(1, "two");
    assertThat((Map<String, Object>) list.get(2)).containsExactly(entry("three", 3));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnDate() {
    // when
    Object date = evaluate("new Date(1000)");
    Object object = evaluate("({d: new Date(2000)})");

    // then
    assertThat(date).isEqualTo(new Date(1000));
    assertThat((Map<String, Object>) object).containsExactly(entry("d", new Date(2000)));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnUsableObjectWithCycle() {
    // when
    Object result = evaluate("(function() { var o = {name: 'o'}; o.self = o; o.list = [o]; return o; })()");

    // then
    Map<String, Object> map = (Map<String, Object>) result;
    assertThat(map).containsOnlyKeys("name", "self", "list");
    assertThat(map.get("self")).isSameAs(map);
    assertThat(((List<Object>) map.get("list")).get(0)).isSameAs(map);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnJavaObjectsUnchanged() {
    // when
    Object list = evaluate("(function() { var l = new (Java.type('java.util.ArrayList'))(); l.add('a'); return l; })()");
    Object object = evaluate("({e: execution})");

    // then
    assertThat(list).isExactlyInstanceOf(ArrayList.class).isEqualTo(Arrays.asList("a"));
    assertThat(((Map<String, Object>) object).get("e")).isInstanceOf(ExecutionEntity.class);
    assertThat(evaluate("1 + 2")).isEqualTo(3);
    assertThat(evaluate("'a' + 'b'")).isEqualTo("ab");
    assertThat(evaluate("undefined")).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotFailForValuesBoundToScriptEngine() {
    // when the result cannot exist without the script engine
    Object function = evaluate("(function(a) { return a + 1; })");
    Object object = evaluate("({a: 1, f: function() { return 1; }})");

    // then the evaluation succeeds and the function is returned as it is
    assertThat(function).isInstanceOf(Function.class);

    // and the other properties of an object that refers to a function can be used
    assertThat(object).isExactlyInstanceOf(LinkedHashMap.class);
    assertThat((Map<String, Object>) object).containsOnlyKeys("a", "f").containsEntry("a", 1);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldStoreObjectLiteralAsResultVariable() {
    // given
    deployProcessWithResultVariable("({a: 1, n: {b: ['x', 2]}})");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then
    Object result = runtimeService.getVariable(processInstance.getId(), "result");
    assertThat(result).isInstanceOf(Map.class);
    Map<String, Object> map = (Map<String, Object>) result;
    assertThat(map).containsOnlyKeys("a", "n").containsEntry("a", 1);
    assertThat((Map<String, Object>) map.get("n")).containsExactly(entry("b", Arrays.asList("x", 2)));
  }

  @Test
  public void shouldStoreArrayAsResultVariable() {
    // given
    deployProcessWithResultVariable("[1, 'two', [3]]");

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then
    Object result = runtimeService.getVariable(processInstance.getId(), "result");
    assertThat(result).isEqualTo(Arrays.asList(1, "two", Arrays.asList(3)));
  }

  protected void deployProcessWithResultVariable(String scriptText) {
    deployProcess(Bpmn.createExecutableProcess("testProcess")
      .startEvent()
      .scriptTask()
        .scriptFormat(LANGUAGE)
        .scriptText(scriptText)
        .camundaResultVariable("result")
      .userTask()
      .endEvent()
      .done());
  }

  /**
   * Evaluates the script like the engine does for a script that is not a
   * script task, e.g. the script of an input parameter of a connector, and
   * returns the result after the command has finished.
   */
  protected Object evaluate(String scriptText) {
    deployProcess(Bpmn.createExecutableProcess("testProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done());
    String processInstanceId = runtimeService.startProcessInstanceByKey("testProcess").getId();

    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      ExecutionEntity execution = commandContext.getExecutionManager().findExecutionById(processInstanceId);
      ExecutableScript script = processEngineConfiguration.getScriptFactory().createScriptFromSource(LANGUAGE, scriptText);
      return processEngineConfiguration.getScriptingEnvironment().execute(script, execution);
    });
  }

}
