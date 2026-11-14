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
package org.camunda.bpm.engine.impl.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.impl.juel.Tree;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class QueryExpressionAllowListTest {

  public static long MUTABLE_FIELD = 0;

  protected static final String CLASS_NAME = QueryExpressionAllowListTest.class.getName();

  protected static final String[] ACCEPTED_EXPRESSIONS = {
      // text without an expression
      "",
      "kermit",
      "a text with spaces, $ signs and { braces }",
      // literals
      "${'kermit'}",
      "${ 'John%' }",
      "${\"kermit\"}",
      "${true}",
      "${false}",
      "${null}",
      "${42}",
      "${-42}",
      "${4.2}",
      // built-in functions
      "${currentUser()}",
      "${ currentUser() }",
      "${currentUser ( ) }",
      "${\n\tcurrentUser()\r\n}",
      "${currentUserGroups()}",
      "${currentUserGroups()[0]}",
      "${now()}",
      "${ now() }",
      "${dateTime()}",
      "${ dateTime() }",
      // built-in methods chained to dateTime()
      "${dateTime().plusYears(1)}",
      "${dateTime().plusMonths(2)}",
      "${ dateTime().plusWeeks(2) }",
      "${dateTime().plusDays(1)}",
      "${dateTime().plusDays(-1)}",
      "${dateTime().plusHours(1)}",
      "${dateTime().plusMinutes(1)}",
      "${dateTime().plusSeconds(1)}",
      "${dateTime().plusMillis(1)}",
      "${dateTime().minusYears(1)}",
      "${dateTime().minusMonths(1)}",
      "${dateTime().minusWeeks(1)}",
      "${dateTime().minusDays(1)}",
      "${dateTime().minusHours(1)}",
      "${dateTime().minusMinutes(1)}",
      "${dateTime().minusSeconds(10)}",
      "${dateTime().minusMillis(1)}",
      "${dateTime().withYear(2030)}",
      "${dateTime().withMonthOfYear(1)}",
      "${dateTime().withDayOfMonth(1)}",
      "${dateTime().withDayOfWeek(1)}",
      "${dateTime().withDayOfYear(1)}",
      "${dateTime().withHourOfDay(0)}",
      "${dateTime().withMinuteOfHour(0)}",
      "${dateTime().withSecondOfMinute(0)}",
      "${dateTime().withMillisOfSecond(0)}",
      "${dateTime().withMillisOfDay(0)}",
      "${dateTime().withMillis(0)}",
      "${dateTime().withDate(2030, 1, 1)}",
      "${dateTime().withTime(0, 0, 0, 0)}",
      "${dateTime().withTimeAtStartOfDay()}",
      "${dateTime().plusDays(4).withTimeAtStartOfDay()}",
      "${dateTime() . plusDays( 4 ) . withTimeAtStartOfDay( )}",
      "${dateTime().toDate()}",
      "${dateTime().plusDays(1).minusHours(2).toDate()}",
  };

  protected static final String[] REJECTED_EXPRESSIONS = {
      // not parseable
      "${",
      "${}",
      "${currentUser()",
      "${currentUser(}",
      "${${currentUser()}}",
      "${currentUser() /* comment */}",
      "${currentUser() // comment\n}",
      "${currentUser(); now()}",
      // deferred expressions
      "#{now()}",
      "#{currentUser()}",
      "#{''.getClass()}",
      // more than one expression, text around an expression
      "${currentUser()}${currentUser()}",
      "${currentUser()}${''.getClass()}",
      "${currentUser()}#{now()}",
      "prefix${currentUser()}",
      "${currentUser()}suffix",
      " ${currentUser()}",
      "${currentUser()} ",
      "${currentUser()}\n",
      "$${currentUser()}",
      // methods on the result of built-in functions
      "${currentUser().getClass()}",
      "${currentUser().getClass().forName('java.lang.Runtime')}",
      "${currentUser().getClass().forName('" + CLASS_NAME + "').getField('MUTABLE_FIELD').setLong(null, 42)}",
      "${currentUser().toString()}",
      "${currentUser().concat('x')}",
      "${currentUser().plusDays(1)}",
      "${currentUserGroups().getClass()}",
      "${currentUserGroups().get(0)}",
      "${currentUserGroups().add('camunda-admin')}",
      "${currentUserGroups().stream()}",
      "${currentUserGroups()[0].getClass()}",
      "${now().getClass()}",
      "${now().getClass().forName('java.lang.Runtime').getMethod('getRuntime').invoke(null).exec('calc')}",
      "${now().getTime()}",
      "${now().setTime(0)}",
      "${now().plusDays(1)}",
      "${now().toDate()}",
      // methods of DateTime that are not allow-listed
      "${dateTime().getClass()}",
      "${dateTime().getClass().getClassLoader()}",
      "${dateTime().plusDays(1).getClass()}",
      "${dateTime().getChronology()}",
      "${dateTime().getZone()}",
      "${dateTime().withZone(null)}",
      "${dateTime().toString()}",
      "${dateTime().toString('yyyy')}",
      "${dateTime().toGregorianCalendar()}",
      "${dateTime().dayOfMonth()}",
      "${dateTime().PlusDays(1)}",
      "${dateTime().plusdays(1)}",
      // nothing can be chained to toDate()
      "${dateTime().toDate().getClass()}",
      "${dateTime().toDate().plusDays(1)}",
      "${dateTime().toDate().toDate()}",
      "${dateTime().toDate().setTime(0)}",
      // built-in methods on anything but a date-time
      "${'x'.plusDays(1)}",
      "${1.plusDays(1)}",
      "${null.plusDays(1)}",
      "${'x'.toDate()}",
      // properties and dynamic access
      "${dateTime().class}",
      "${dateTime().millis}",
      "${dateTime()['class']}",
      "${dateTime()['plusDays'](1)}",
      "${dateTime()['getClass']()}",
      "${currentUser().class}",
      "${currentUser().bytes}",
      "${currentUser()['class']}",
      "${currentUser()['getClass']()}",
      "${currentUserGroups().class}",
      "${currentUserGroups()['class']}",
      "${currentUserGroups()[currentUser()]}",
      "${currentUserGroups()[now()]}",
      "${currentUserGroups()[-1]}",
      "${currentUserGroups()[0 + 1]}",
      // methods on literals
      "${''.getClass()}",
      "${''.getClass().forName('java.lang.Runtime')}",
      "${''.getClass().forName('" + CLASS_NAME + "').getField('MUTABLE_FIELD').setLong(null, 42)}",
      "${\"\".getClass()}",
      "${'%'.concat(currentUser())}",
      "${'a'.concat('b')}",
      "${'a'.length()}",
      "${1.getClass()}",
      "${true.getClass()}",
      "${null.getClass()}",
      // variables and beans
      "${execution}",
      "${task}",
      "${processEngine}",
      "${taskService}",
      "${myBean}",
      "${myBean.name}",
      "${myBean['name']}",
      "${myBean.getName()}",
      "${myBean.getClass()}",
      "${execution.setVariable('foo', 'bar')}",
      "${taskService.createTaskQuery().list()}",
      "${currentUser}",
      "${now}",
      "${dateTime}",
      "${dateTime.plusDays(1)}",
      // operators
      "${'a' + 'b'}",
      "${1 + 1}",
      "${1 - 1}",
      "${1/0}",
      "${2 * 2}",
      "${1 % 2}",
      "${-currentUser()}",
      "${--1}",
      "${-'1'}",
      "${!true}",
      "${not true}",
      "${empty currentUser()}",
      "${currentUser() == 'kermit'}",
      "${currentUser() eq 'kermit'}",
      "${1 < 2}",
      "${true && true}",
      "${true or false}",
      "${true ? currentUser() : now()}",
      "${true ? ''.getClass() : null}",
      "${(currentUser())}",
      "${(dateTime()).plusDays(1)}",
      "${(''.getClass())}",
      // functions that are not allow-listed
      "${foo()}",
      "${getClass()}",
      "${fn:currentUser()}",
      "${ns:foo('bar')}",
      "${CurrentUser()}",
      "${currentuser()}",
      "${notAllowed()}",
      // arguments of built-in functions and methods
      "${currentUser(1)}",
      "${currentUser(''.getClass())}",
      "${now('x')}",
      "${dateTime(1)}",
      "${dateTime(now())}",
      "${dateTime().plusDays(''.getClass())}",
      "${dateTime().plusDays(myBean)}",
      "${dateTime().plusDays(foo())}",
      "${dateTime().plusDays(1 + 1)}",
      "${dateTime().plusDays((1))}",
      "${dateTime().plusDays(currentUser().length())}",
      "${dateTime().withTime(0, 0, 0, ''.getClass())}",
      // syntax of later versions of the expression language
      "${x -> x}",
      "${(x -> x.getClass())('')}",
      "${[1, 2, 3]}",
      "${{'a': 'b'}}",
      "${x = 5}",
      "${currentUser() += 'x'}",
      "${T(java.lang.Runtime).getRuntime()}",
      "${''.class.forName('java.lang.Runtime')}",
      "${java.lang.Runtime.getRuntime()}",
  };

  protected JuelExpressionManager expressionManager;
  protected QueryExpressionAllowList allowList;

  @Before
  public void setUp() {
    Map<Object, Object> beans = new HashMap<>();
    beans.put("myBean", new MyBean());
    beans.put("otherBean", new MyBean());

    expressionManager = new JuelExpressionManager(beans);
    expressionManager.addFunction(CommandContextFunctions.CURRENT_USER,
        ReflectUtil.getMethod(CommandContextFunctions.class, CommandContextFunctions.CURRENT_USER));
    expressionManager.addFunction(CommandContextFunctions.CURRENT_USER_GROUPS,
        ReflectUtil.getMethod(CommandContextFunctions.class, CommandContextFunctions.CURRENT_USER_GROUPS));
    expressionManager.addFunction(DateTimeFunctions.NOW,
        ReflectUtil.getMethod(DateTimeFunctions.class, DateTimeFunctions.NOW));
    expressionManager.addFunction(DateTimeFunctions.DATE_TIME,
        ReflectUtil.getMethod(DateTimeFunctions.class, DateTimeFunctions.DATE_TIME));
    expressionManager.addFunction("notAllowed", getFunction("notAllowed"));
    expressionManager.addFunction("groupsOf", getFunction("groupsOf", String.class));
    expressionManager.addFunction("ns:foo", getFunction("foo", String.class));

    allowList = new QueryExpressionAllowList();

    ClockUtil.setCurrentTime(new Date(1427547759000L));
    MUTABLE_FIELD = 0;
  }

  @After
  public void tearDown() {
    ClockUtil.reset();
  }

  // syntax check /////////////////////////////////////////////////////////////

  @Test
  public void shouldAcceptAllowListedExpressions() {
    for (String expression : ACCEPTED_EXPRESSIONS) {
      assertThatCode(() -> allowList.validate(expression))
        .as(expression)
        .doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldEvaluateAllowListedExpressions() {
    for (String expression : ACCEPTED_EXPRESSIONS) {
      if (expression.contains("[0]")) {
        // there is no authenticated user whose groups could be accessed
        continue;
      }
      assertThatCode(() -> allowList.evaluate(expression, expressionManager))
        .as(expression)
        .doesNotThrowAnyException();
    }
  }

  @Test
  public void shouldRejectOtherExpressions() {
    for (String expression : REJECTED_EXPRESSIONS) {
      assertThatThrownBy(() -> allowList.validate(expression))
        .as(expression)
        .isInstanceOf(BadUserRequestException.class)
        .hasMessageContaining("is not allowed");
    }
  }

  @Test
  public void shouldNotEvaluateOtherExpressions() {
    for (String expression : REJECTED_EXPRESSIONS) {
      assertThatThrownBy(() -> allowList.evaluate(expression, expressionManager))
        .as(expression)
        .isInstanceOf(BadUserRequestException.class);
    }
    assertThat(MUTABLE_FIELD).isZero();
  }

  @Test
  public void shouldRejectNull() {
    assertThatThrownBy(() -> allowList.validate(null))
      .isInstanceOf(BadUserRequestException.class);
    assertThatThrownBy(() -> allowList.evaluate(null, expressionManager))
      .isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldRejectDeeplyNestedExpression() {
    StringBuilder expression = new StringBuilder("${");
    for (int i = 0; i < 100000; i++) {
      expression.append("(");
    }

    assertThatThrownBy(() -> allowList.validate(expression.toString()))
      .isInstanceOf(BadUserRequestException.class);
  }

  // evaluation ///////////////////////////////////////////////////////////////

  @Test
  public void shouldEvaluateText() {
    assertThat(allowList.evaluate("kermit", expressionManager)).isEqualTo("kermit");
    assertThat(allowList.evaluate("", expressionManager)).isEqualTo("");
  }

  @Test
  public void shouldNotEvaluateEscapedExpression() {
    // an escaped expression is text
    assertThat(allowList.evaluate("\\${''.getClass()}", expressionManager)).isEqualTo("${''.getClass()}");
    assertThat(allowList.evaluate("\\#{''.getClass()}", expressionManager)).isEqualTo("#{''.getClass()}");
  }

  @Test
  public void shouldNotEvaluateExpressionBehindBackslashes() {
    String expression = "${''.getClass().forName('" + CLASS_NAME + "').getField('MUTABLE_FIELD').setLong(null, 42)}";

    for (String prefix : new String[] { "\\", "\\\\", "\\\\\\", "$", "$\\", "#", "{", "$$" }) {
      Object value;
      try {
        value = allowList.evaluate(prefix + expression, expressionManager);
      } catch (BadUserRequestException e) {
        continue;
      }
      // not rejected, so it must have been treated as text
      assertThat(value).as(prefix).isInstanceOf(String.class);
      assertThat((String) value).as(prefix).endsWith(expression);
    }
    assertThat(MUTABLE_FIELD).isZero();
  }

  @Test
  public void shouldNotEvaluateExpressionInStringLiteral() {
    assertThat(allowList.evaluate("${'${\\'\\'.getClass()}'}", expressionManager)).isEqualTo("${''.getClass()}");
    assertThat(allowList.evaluate("${'#{currentUser()}'}", expressionManager)).isEqualTo("#{currentUser()}");
  }

  @Test
  public void shouldEvaluateLiterals() {
    assertThat(allowList.evaluate("${'kermit'}", expressionManager)).isEqualTo("kermit");
    assertThat(allowList.evaluate("${-42}", expressionManager)).isEqualTo(-42L);
    assertThat(allowList.evaluate("${true}", expressionManager)).isEqualTo(true);
    assertThat(allowList.evaluate("${null}", expressionManager)).isNull();
  }

  @Test
  public void shouldEvaluateBuiltInFunctions() {
    DateTime now = new DateTime(ClockUtil.getCurrentTime());

    assertThat(allowList.evaluate("${currentUser()}", expressionManager)).isNull();
    assertThat(allowList.evaluate("${currentUserGroups()}", expressionManager)).isNull();
    assertThat(allowList.evaluate("${now()}", expressionManager)).isEqualTo(now.toDate());
    assertThat(allowList.evaluate("${dateTime()}", expressionManager)).isEqualTo(now);
    assertThat(allowList.evaluate("${dateTime().plusDays(4).withTimeAtStartOfDay()}", expressionManager))
      .isEqualTo(now.plusDays(4).withTimeAtStartOfDay());
    assertThat(allowList.evaluate("${dateTime().plusDays(-1)}", expressionManager)).isEqualTo(now.minusDays(1));
    assertThat(allowList.evaluate("${dateTime().withTime(1, 2, 3, 4)}", expressionManager)).isEqualTo(now.withTime(1, 2, 3, 4));
    assertThat(allowList.evaluate("${dateTime().minusSeconds(10).toDate()}", expressionManager))
      .isEqualTo(now.minusSeconds(10).toDate());
  }

  @Test
  public void shouldCoverMethodsOfDateTimeOnly() throws Exception {
    for (String name : QueryExpressionAllowList.DEFAULT_DATE_TIME_METHODS) {
      boolean found = false;
      for (Method method : DateTime.class.getMethods()) {
        if (!method.getName().equals(name)) {
          continue;
        }
        boolean numbersOnly = Arrays.stream(method.getParameterTypes()).allMatch(type -> type == int.class || type == long.class);
        if (!numbersOnly) {
          continue;
        }
        found = true;
        if (QueryExpressionAllowList.TO_DATE_METHOD.equals(name)) {
          assertThat(method.getReturnType()).as(name).isEqualTo(Date.class);
        } else {
          assertThat(method.getReturnType()).as(name).isEqualTo(DateTime.class);
        }
      }
      assertThat(found).as(name).isTrue();
    }
  }

  @Test
  public void shouldFailForOtherExpressionManager() {
    ExpressionManager otherExpressionManager = new ExpressionManager() {
      @Override
      public Expression createExpression(String expression) {
        throw new AssertionError("must not be called");
      }

      @Override
      public void addFunction(String name, Method function) {
      }
    };

    assertThatThrownBy(() -> allowList.evaluate("${currentUser()}", otherExpressionManager))
      .isInstanceOf(ProcessEngineException.class)
      .isNotInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("QueryExpressionValidator");
  }

  // the restrictions of the evaluation do not depend on the syntax check /////

  @Test
  public void shouldNotResolveBeanDuringEvaluation() {
    assertEvaluationRefused("${myBean}", "The variable or bean 'myBean' is not allowed");
    assertEvaluationRefused("${myBean.getName()}", "The variable or bean 'myBean' is not allowed");
  }

  @Test
  public void shouldNotResolveFunctionDuringEvaluation() {
    assertEvaluationRefused("${notAllowed()}", "notAllowed");
    assertEvaluationRefused("${ns:foo('bar')}", "ns:foo");
    assertThat(MUTABLE_FIELD).isZero();
  }

  @Test
  public void shouldNotInvokeMethodDuringEvaluation() {
    assertEvaluationRefused("${''.getClass()}", "The method 'getClass' of java.lang.String is not allowed");
    assertEvaluationRefused("${''.getClass().forName('" + CLASS_NAME + "').getField('MUTABLE_FIELD').setLong(null, 42)}",
        "The method 'getClass' of java.lang.String is not allowed");
    assertEvaluationRefused("${dateTime().getClass()}", "The method 'getClass' of org.joda.time.DateTime is not allowed");
    assertEvaluationRefused("${dateTime().toDate().getClass()}", "The method 'getClass' of java.util.Date is not allowed");
    assertEvaluationRefused("${now().toDate()}", "The method 'toDate' of java.util.Date is not allowed");
    assertEvaluationRefused("${'x'.plusDays(1)}", "The method 'plusDays' of java.lang.String is not allowed");
    assertThat(MUTABLE_FIELD).isZero();
  }

  @Test
  public void shouldNotReadPropertyDuringEvaluation() {
    assertEvaluationRefused("${dateTime().class}", "The property 'class' of org.joda.time.DateTime is not allowed");
    assertEvaluationRefused("${dateTime()['class']}", "The property 'class' of org.joda.time.DateTime is not allowed");

    // an index is accepted by the syntax check whatever it is applied to
    assertThatThrownBy(() -> allowList.evaluate("${'x'[0]}", expressionManager))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("The property '0' of java.lang.String is not allowed");
  }

  // extending the allow-list /////////////////////////////////////////////////

  @Test
  public void shouldAcceptAdditionalFunction() {
    allowList = new QueryExpressionAllowList(Arrays.asList("groupsOf", "ns:foo"), null, null);

    assertThat(allowList.evaluate("${groupsOf('kermit')}", expressionManager)).isEqualTo(Arrays.asList("kermit-1", "kermit-2"));
    assertThat(allowList.evaluate("${groupsOf('kermit')[1]}", expressionManager)).isEqualTo("kermit-2");
    assertThat(allowList.evaluate("${groupsOf(ns:foo('bar'))[0]}", expressionManager)).isEqualTo("foo-bar-1");

    // arguments are subject to the allow-list as well
    assertRejected("${groupsOf(''.getClass())}");
    assertRejected("${groupsOf(myBean)}");
    assertRejected("${groupsOf(notAllowed())}");
    assertRejected("${groupsOf('a' += 'b')}");
    // and so is the result
    assertRejected("${groupsOf('kermit').getClass()}");
    assertRejected("${groupsOf('kermit').size()}");
    assertRejected("${notAllowed()}");
  }

  @Test
  public void shouldAcceptAdditionalMethod() {
    allowList = new QueryExpressionAllowList(Arrays.asList("concat", "toUpperCase"), null, null);

    assertThat(allowList.evaluate("${'%'.concat('kermit')}", expressionManager)).isEqualTo("%kermit");
    assertThat(allowList.evaluate("${'%'.concat('kermit').toUpperCase()}", expressionManager)).isEqualTo("%KERMIT");
    allowList.validate("${'%'.concat(currentUser())}");
    allowList.validate("${currentUser().concat('%')}");

    assertRejected("${'%'.concat('kermit').getClass()}");
    assertRejected("${'%'.concat(''.getClass())}");
    assertRejected("${'%'.toLowerCase()}");
  }

  @Test
  public void shouldAcceptBuiltInMethodOnResultOfAdditionalFunction() {
    expressionManager.addFunction("startOfWeek", getFunction("startOfWeek"));
    allowList = new QueryExpressionAllowList(Arrays.asList("startOfWeek", "groupsOf"), null, null);

    DateTime startOfWeek = new DateTime(ClockUtil.getCurrentTime()).withDayOfWeek(1).withTimeAtStartOfDay();
    assertThat(allowList.evaluate("${startOfWeek().plusWeeks(1)}", expressionManager)).isEqualTo(startOfWeek.plusWeeks(1));

    // the syntax check cannot know the type of a custom value, the evaluation does
    assertThatThrownBy(() -> allowList.evaluate("${groupsOf('kermit').plusDays(1)}", expressionManager))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("The method 'plusDays' of java.util.Arrays$ArrayList is not allowed");
  }

  @Test
  public void shouldAcceptBeanWithPropertyAndMethod() {
    allowList = new QueryExpressionAllowList(Collections.singleton("getGroups"), Collections.singleton("myBean"), Collections.singleton("name"));

    assertThat(allowList.evaluate("${myBean}", expressionManager)).isInstanceOf(MyBean.class);
    assertThat(allowList.evaluate("${myBean.name}", expressionManager)).isEqualTo("my bean");
    assertThat(allowList.evaluate("${myBean.getGroups()}", expressionManager)).isEqualTo(Arrays.asList("a", "b"));
    assertThat(allowList.evaluate("${myBean.getGroups()[0]}", expressionManager)).isEqualTo("a");

    assertRejected("${otherBean}");
    assertRejected("${otherBean.name}");
    assertRejected("${myBean.secret}");
    assertRejected("${myBean.class}");
    assertRejected("${myBean['name']}");
    assertRejected("${myBean.getName()}");
    assertRejected("${myBean.getClass()}");
    assertRejected("${myBean.name.getClass()}");
    assertRejected("${myBean.name.bytes}");
    assertRejected("${execution}");
  }

  @Test
  public void shouldNotInvokeMethodOnClassEvenIfAllowListed() {
    allowList = new QueryExpressionAllowList(Arrays.asList("getClass", "getName", "forName", "getClassLoader"), null, Collections.singleton("name"));

    assertThat(allowList.evaluate("${''.getClass()}", expressionManager)).isEqualTo(String.class);
    assertThatThrownBy(() -> allowList.evaluate("${''.getClass().getName()}", expressionManager))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("The method 'getName' of java.lang.Class is not allowed");
    assertThatThrownBy(() -> allowList.evaluate("${''.getClass().forName('java.lang.Runtime')}", expressionManager))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("The method 'forName' of java.lang.Class is not allowed");
    assertThatThrownBy(() -> allowList.evaluate("${''.getClass().name}", expressionManager))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("The property 'name' of java.lang.Class is not allowed");
  }

  @Test
  public void shouldParseNames() {
    assertThat(QueryExpressionAllowList.parseNames(null)).isEmpty();
    assertThat(QueryExpressionAllowList.parseNames("")).isEmpty();
    assertThat(QueryExpressionAllowList.parseNames(" , ,")).isEmpty();
    assertThat(QueryExpressionAllowList.parseNames("concat")).containsExactly("concat");
    assertThat(QueryExpressionAllowList.parseNames(" concat , ns:foo,,concat\n,toUpperCase ")).containsExactly("concat", "ns:foo", "toUpperCase");
  }

  // helper ///////////////////////////////////////////////////////////////////

  protected void assertRejected(String expression) {
    assertThatThrownBy(() -> allowList.evaluate(expression, expressionManager))
      .as(expression)
      .isInstanceOf(BadUserRequestException.class);
  }

  /**
   * Evaluates an expression that the syntax check rejects as if that check
   * had a gap.
   */
  protected void assertEvaluationRefused(String expression, String message) {
    assertRejected(expression);

    QueryExpressionAllowList withoutSyntaxCheck = new QueryExpressionAllowList() {
      @Override
      protected Tree parse(String expression) {
        return treeBuilder.build(expression);
      }
    };

    assertThatThrownBy(() -> withoutSyntaxCheck.evaluate(expression, expressionManager))
      .as(expression)
      .isInstanceOf(ProcessEngineException.class)
      .isNotInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(message);
  }

  protected static Method getFunction(String name, Class<?>... parameterTypes) {
    try {
      return QueryExpressionAllowListTest.class.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  public static String notAllowed() {
    MUTABLE_FIELD = 42;
    return "notAllowed";
  }

  public static String foo(String argument) {
    MUTABLE_FIELD = 42;
    return "foo-" + argument;
  }

  public static List<String> groupsOf(String user) {
    return Arrays.asList(user + "-1", user + "-2");
  }

  public static DateTime startOfWeek() {
    return new DateTime(ClockUtil.getCurrentTime()).withDayOfWeek(1).withTimeAtStartOfDay();
  }

  public static class MyBean {

    public String getName() {
      return "my bean";
    }

    public String getSecret() {
      return "secret";
    }

    public List<String> getGroups() {
      return Arrays.asList("a", "b");
    }
  }

}
