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
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.camunda.bpm.dmn.engine.impl.DefaultDmnEngineConfiguration;
import org.camunda.bpm.dmn.engine.impl.DmnExpressionImpl;
import org.camunda.bpm.dmn.engine.impl.evaluation.ExpressionEvaluationHandler;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.Before;
import org.junit.Test;

/**
 * The script engine of a GraalJS expression is closed after the evaluation.
 * JavaScript objects and arrays returned by the expression must still be
 * usable afterwards.
 */
public class GraalJsExpressionResultTest {

  protected static final String LANGUAGE = "javascript";

  protected ExpressionEvaluationHandler expressionEvaluationHandler;

  @Before
  public void setUp() {
    DefaultDmnEngineConfiguration configuration = new DefaultDmnEngineConfiguration();
    configuration.init();

    expressionEvaluationHandler = new ExpressionEvaluationHandler(configuration);
  }

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
    Object result = evaluate("[1, 'two', {three: 3}, x]");

    // then
    assertThat(result).isExactlyInstanceOf(ArrayList.class);
    List<Object> list = (List<Object>) result;
    assertThat(list).hasSize(4).startsWith(1, "two").endsWith(41);
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
    Object result = evaluate("(function() { var o = {name: 'o'}; o.self = o; o.list = [o, o]; return o; })()");

    // then
    Map<String, Object> map = (Map<String, Object>) result;
    assertThat(map).containsOnlyKeys("name", "self", "list");
    assertThat(map.get("self")).isSameAs(map);
    List<Object> list = (List<Object>) map.get("list");
    assertThat(list).hasSize(2);
    assertThat(list.get(0)).isSameAs(map);
    assertThat(list.get(1)).isSameAs(map);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnUsableObjectsOfOtherTypes() {
    // when
    Object mapObject = evaluate("new Map([['k', {v: 1}]])");
    Object classInstance = evaluate("(function() { class A { constructor() { this.x = 1; } m() { return 2; } }; return new A(); })()");
    Object typedArray = evaluate("new Uint8Array([1, 2])");

    // then
    assertThat(mapObject).isExactlyInstanceOf(LinkedHashMap.class);
    assertThat(((Map<String, Map<String, Object>>) mapObject).get("k")).containsExactly(entry("v", 1));
    assertThat(classInstance).isExactlyInstanceOf(LinkedHashMap.class);
    assertThat((Map<String, Object>) classInstance).containsExactly(entry("x", 1));
    assertThat(typedArray).isEqualTo(Arrays.asList(1, 2));
  }

  @Test
  public void shouldReturnOtherValuesUnchanged() {
    assertThat(evaluate("x + 1")).isEqualTo(42);
    assertThat(evaluate("'a' + 'b'")).isEqualTo("ab");
    assertThat(evaluate("x > 1")).isEqualTo(true);
    assertThat(evaluate("null")).isNull();
    assertThat(evaluate("undefined")).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldNotFailForValuesBoundToScriptEngine() {
    // when the result cannot exist without the script engine
    Object function = evaluate("(function(a) { return a + 1; })");
    Object regExp = evaluate("/ab+c/");
    Object object = evaluate("({a: 1, f: function() { return 1; }})");

    // then the evaluation succeeds, the values are returned as they are
    assertThat(function).isInstanceOf(Function.class);
    assertThat(regExp).isNotNull().isNotExactlyInstanceOf(LinkedHashMap.class);

    // and they cannot be used
    assertThatThrownBy(() -> ((Function<Object, Object>) function).apply(new Object[] { 1 }))
      .isInstanceOf(IllegalStateException.class);

    // but the other properties of an object that refers to such a value can
    assertThat(object).isExactlyInstanceOf(LinkedHashMap.class);
    Map<String, Object> map = (Map<String, Object>) object;
    assertThat(map).containsOnlyKeys("a", "f").containsEntry("a", 1);
    assertThat(map.get("f")).isInstanceOf(Function.class);
  }

  protected Object evaluate(String text) {
    DmnExpressionImpl expression = new DmnExpressionImpl();
    expression.setExpression(text);
    expression.setExpressionLanguage(LANGUAGE);
    return expressionEvaluationHandler.evaluateExpression(LANGUAGE, expression,
        Variables.createVariables().putValue("x", 41).asVariableContext());
  }

}
