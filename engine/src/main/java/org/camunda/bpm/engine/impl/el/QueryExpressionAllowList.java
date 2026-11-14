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

import java.beans.FeatureDescriptor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.impl.juel.AstBoolean;
import org.camunda.bpm.impl.juel.AstBracket;
import org.camunda.bpm.impl.juel.AstDot;
import org.camunda.bpm.impl.juel.AstEval;
import org.camunda.bpm.impl.juel.AstFunction;
import org.camunda.bpm.impl.juel.AstIdentifier;
import org.camunda.bpm.impl.juel.AstMethod;
import org.camunda.bpm.impl.juel.AstNull;
import org.camunda.bpm.impl.juel.AstNumber;
import org.camunda.bpm.impl.juel.AstString;
import org.camunda.bpm.impl.juel.AstText;
import org.camunda.bpm.impl.juel.AstUnary;
import org.camunda.bpm.impl.juel.Bindings;
import org.camunda.bpm.impl.juel.Builder;
import org.camunda.bpm.impl.juel.Node;
import org.camunda.bpm.impl.juel.Tree;
import org.camunda.bpm.impl.juel.TreeBuilderException;
import org.camunda.bpm.impl.juel.jakarta.el.ELContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELException;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;
import org.camunda.bpm.impl.juel.jakarta.el.ExpressionFactory;
import org.camunda.bpm.impl.juel.jakarta.el.FunctionMapper;
import org.joda.time.DateTime;

/**
 * The default {@link QueryExpressionValidator}: accepts a query expression only
 * if it is built exclusively from allow-listed parts, and evaluates accepted
 * expressions itself without giving them access to anything else.
 *
 * <h2>Accepted expressions</h2>
 *
 * An expression is parsed into its syntax tree and accepted only if the tree
 * has one of the following shapes; everything else is rejected, including
 * expressions that cannot be parsed:
 *
 * <pre>
 * expression := literal text without any ${...} or #{...}
 *             | '${' value '}'
 * value      := literal
 *             | function '(' arguments ')'            function is allow-listed
 *             | value '.' method '(' arguments ')'    method is allow-listed
 *             | value '[' number ']'                  element of a list or an array
 *             | bean                                  bean is allow-listed
 *             | value '.' property                    property is allow-listed
 * arguments  := ( value ( ',' value )* )?
 * literal    := string | number | '-' number | 'true' | 'false' | 'null'
 * </pre>
 *
 * In particular there are no operators, no string concatenation, no
 * conditional expressions, no parentheses, no deferred <code>#{...}</code>
 * expressions, no text around an expression and not more than one expression.
 *
 * <h2>Allow-list</h2>
 *
 * <ul>
 *   <li>Functions: {@link #DEFAULT_FUNCTIONS}, called without arguments, plus
 *   the additionally configured names.</li>
 *   <li>Methods: {@link #DEFAULT_DATE_TIME_METHODS}, but only as a chain that
 *   starts at <code>dateTime()</code> (for example
 *   <code>${dateTime().plusDays(1).withTimeAtStartOfDay()}</code>), plus the
 *   additionally configured names. An additionally configured method name is
 *   accepted on any value, so it must be safe whatever object it is invoked
 *   on.</li>
 *   <li>Beans and properties: none, unless configured.</li>
 * </ul>
 *
 * <h2>Evaluation</h2>
 *
 * The syntax tree that passed the check is the one that is evaluated; the
 * expression text is never handed to another parser. The evaluation uses a
 * function mapper and a resolver that enforce the allow-list a second time,
 * independently of the syntax check: functions that are not allow-listed do
 * not exist, identifiers resolve to allow-listed beans only, and only
 * allow-listed methods and properties are reachable. Process variables, mocks
 * and the other beans of the process engine are not visible.
 */
public class QueryExpressionAllowList implements QueryExpressionValidator {

  public static final String DATE_TIME_FUNCTION = DateTimeFunctions.DATE_TIME;
  public static final String TO_DATE_METHOD = "toDate";

  /** the functions every query expression may call; none of them takes arguments */
  public static final Set<String> DEFAULT_FUNCTIONS = unmodifiableSet(
      CommandContextFunctions.CURRENT_USER,
      CommandContextFunctions.CURRENT_USER_GROUPS,
      DateTimeFunctions.NOW,
      DateTimeFunctions.DATE_TIME);

  /**
   * the methods that may be chained to <code>dateTime()</code>. All of them are
   * methods of {@link DateTime} that take numbers only and return another
   * {@link DateTime}, except for <code>toDate</code>, which ends the chain.
   */
  public static final Set<String> DEFAULT_DATE_TIME_METHODS = unmodifiableSet(
      "plusYears", "plusMonths", "plusWeeks", "plusDays",
      "plusHours", "plusMinutes", "plusSeconds", "plusMillis",
      "minusYears", "minusMonths", "minusWeeks", "minusDays",
      "minusHours", "minusMinutes", "minusSeconds", "minusMillis",
      "withYear", "withWeekyear", "withMonthOfYear", "withWeekOfWeekyear",
      "withDayOfYear", "withDayOfMonth", "withDayOfWeek",
      "withHourOfDay", "withMinuteOfHour", "withSecondOfMinute",
      "withMillisOfSecond", "withMillisOfDay", "withMillis",
      "withDate", "withTime", "withTimeAtStartOfDay",
      TO_DATE_METHOD);

  /** what the syntax check knows about the value of a part of an expression */
  protected enum ValueKind {
    /** a literal, or the result of a built-in function or method that is not a date-time */
    PLAIN,
    /** the result of <code>dateTime()</code> or of a built-in method chained to it */
    DATE_TIME,
    /** the result of an additionally configured function, method, bean or property */
    CUSTOM
  }

  protected final Set<String> additionalFunctions;
  protected final Set<String> beans;
  protected final Set<String> properties;

  /** stateless; accepts at least the syntax the expression factory of the engine accepts */
  protected final Builder treeBuilder = new Builder(Builder.Feature.METHOD_INVOCATIONS, Builder.Feature.VARARGS);

  /**
   * An allow-list that consists of the built-in functions and methods only.
   */
  public QueryExpressionAllowList() {
    this(null, null, null);
  }

  /**
   * @param additionalFunctions names of functions and of chained methods that are
   *        allowed in addition to the built-in ones, may be <code>null</code>
   * @param beans names of the beans that may be referenced, may be <code>null</code>
   * @param properties names of the properties that may be read, may be <code>null</code>
   */
  public QueryExpressionAllowList(Collection<String> additionalFunctions, Collection<String> beans, Collection<String> properties) {
    this.additionalFunctions = copyOf(additionalFunctions);
    this.beans = copyOf(beans);
    this.properties = copyOf(properties);
  }

  /**
   * Splits a comma-separated list of names as used by the properties of the
   * process engine configuration; blank entries are ignored.
   */
  public static Set<String> parseNames(String commaSeparatedNames) {
    if (commaSeparatedNames == null) {
      return Collections.emptySet();
    }
    return copyOf(Arrays.asList(commaSeparatedNames.split(",")));
  }

  public Set<String> getAdditionalFunctions() {
    return additionalFunctions;
  }

  public Set<String> getBeans() {
    return beans;
  }

  public Set<String> getProperties() {
    return properties;
  }

  // syntax check /////////////////////////////////////////////////////////////

  @Override
  public void validate(String expression) {
    parse(expression);
  }

  /**
   * @return the syntax tree of the expression, which is guaranteed to consist
   *         of allow-listed parts only
   * @throws BadUserRequestException if the expression is not accepted
   */
  protected Tree parse(String expression) {
    if (expression == null) {
      throw new BadUserRequestException("A query expression must not be null.");
    }

    Tree tree;
    try {
      tree = treeBuilder.build(expression);
    } catch (TreeBuilderException | StackOverflowError e) {
      // an expression that cannot be parsed cannot be checked
      throw rejected(expression, "it is not a valid expression");
    }

    Node root = tree.getRoot();
    if (root instanceof AstText) {
      // plain text, nothing is evaluated
      return tree;
    }
    if (!(root instanceof AstEval) || ((AstEval) root).isDeferred() || root.getCardinality() != 1) {
      throw rejected(expression, "it must consist of exactly one '${...}' expression without any surrounding text");
    }

    try {
      checkValue(expression, root.getChild(0));
    } catch (StackOverflowError e) {
      throw rejected(expression, "it is nested too deeply");
    }
    return tree;
  }

  protected ValueKind checkValue(String expression, Node node) {
    if (node instanceof AstString || node instanceof AstNumber || node instanceof AstBoolean || node instanceof AstNull) {
      return ValueKind.PLAIN;
    }
    if (node instanceof AstUnary) {
      if (((AstUnary) node).getOperator() == AstUnary.NEG && node.getChild(0) instanceof AstNumber) {
        return ValueKind.PLAIN;
      }
      throw rejected(expression, "operators are not allowed");
    }
    if (node instanceof AstFunction) {
      return checkFunction(expression, (AstFunction) node);
    }
    if (node instanceof AstMethod) {
      return checkMethod(expression, (AstMethod) node);
    }
    if (node instanceof AstIdentifier) {
      String name = ((AstIdentifier) node).getName();
      if (!beans.contains(name)) {
        throw rejected(expression, "the variable or bean '" + name + "' is not allowed");
      }
      return ValueKind.CUSTOM;
    }
    if (node instanceof AstDot) {
      String name = ((AstDot) node).getPropertyName();
      if (!properties.contains(name)) {
        throw rejected(expression, "the property '" + name + "' is not allowed");
      }
      checkValue(expression, node.getChild(0));
      return ValueKind.CUSTOM;
    }
    if (node instanceof AstBracket) {
      if (!(node.getChild(1) instanceof AstNumber)) {
        throw rejected(expression, "only a number is allowed in '[...]'");
      }
      ValueKind base = checkValue(expression, node.getChild(0));
      return base == ValueKind.CUSTOM ? ValueKind.CUSTOM : ValueKind.PLAIN;
    }
    throw rejected(expression, "it uses a construct that is not allowed in query expressions");
  }

  protected ValueKind checkFunction(String expression, AstFunction function) {
    String name = function.getName();
    if (DEFAULT_FUNCTIONS.contains(name)) {
      if (function.getParamCount() != 0) {
        throw rejected(expression, "the function '" + name + "' does not take arguments");
      }
      return DATE_TIME_FUNCTION.equals(name) ? ValueKind.DATE_TIME : ValueKind.PLAIN;
    }
    if (additionalFunctions.contains(name)) {
      checkArguments(expression, function.getChild(0), function.getParamCount());
      return ValueKind.CUSTOM;
    }
    throw rejected(expression, "the function '" + name + "' is not allowed");
  }

  protected ValueKind checkMethod(String expression, AstMethod method) {
    Node property = method.getChild(0);
    if (!(property instanceof AstDot)) {
      throw rejected(expression, "a method must be called by its name");
    }
    String name = ((AstDot) property).getPropertyName();
    ValueKind base = checkValue(expression, property.getChild(0));

    Node arguments = method.getChild(1);
    checkArguments(expression, arguments, arguments.getCardinality());

    if (additionalFunctions.contains(name)) {
      return ValueKind.CUSTOM;
    }
    if (DEFAULT_DATE_TIME_METHODS.contains(name) && base != ValueKind.PLAIN) {
      return TO_DATE_METHOD.equals(name) ? ValueKind.PLAIN : ValueKind.DATE_TIME;
    }
    throw rejected(expression, "the method '" + name + "' is not allowed here");
  }

  protected void checkArguments(String expression, Node arguments, int count) {
    for (int i = 0; i < count; i++) {
      checkValue(expression, arguments.getChild(i));
    }
  }

  protected BadUserRequestException rejected(String expression, String reason) {
    return new BadUserRequestException("The query expression '" + expression + "' is not allowed: " + reason
        + ". Query expressions are restricted to an allow-list, which can be extended"
        + " in the process engine configuration.");
  }

  // evaluation ///////////////////////////////////////////////////////////////

  /**
   * Checks the expression and evaluates it with access to the allow-listed
   * functions, methods, beans and properties only.
   *
   * @throws BadUserRequestException if the expression is not accepted
   * @throws ProcessEngineException if the evaluation fails
   */
  public Object evaluate(String expression, ExpressionManager expressionManager) {
    Tree tree = parse(expression);

    if (!(expressionManager instanceof JuelExpressionManager)) {
      // the expression would be interpreted by something this class knows nothing about
      throw new ProcessEngineException("The query expression '" + expression + "' cannot be evaluated: the query"
          + " expression allow-list supports " + JuelExpressionManager.class.getSimpleName() + " only. Configure a "
          + QueryExpressionValidator.class.getSimpleName() + " that matches the expression manager in use.");
    }
    JuelExpressionManager juelExpressionManager = (JuelExpressionManager) expressionManager;
    juelExpressionManager.ensureInitialized();

    FunctionMapper functionMapper = new AllowListFunctionMapper(juelExpressionManager.functionMapper);
    ELContext elContext = new ProcessEngineElContext(functionMapper, new AllowListElResolver(juelExpressionManager.elResolver));
    elContext.putContext(ExpressionFactory.class, juelExpressionManager.expressionFactory);

    try {
      Bindings bindings = tree.bind(functionMapper, null);
      return tree.getRoot().getValue(bindings, elContext, Object.class);
    } catch (ELException e) {
      throw new ProcessEngineException("Error while evaluating query expression '" + expression + "'. Cause: " + e.getMessage(), e);
    }
  }

  protected boolean isFunctionAllowed(String name) {
    return DEFAULT_FUNCTIONS.contains(name) || additionalFunctions.contains(name);
  }

  protected boolean isMethodAllowed(Object base, String name) {
    if (isReflectionEntryPoint(base)) {
      return false;
    }
    return additionalFunctions.contains(name)
        || (DEFAULT_DATE_TIME_METHODS.contains(name) && base instanceof DateTime);
  }

  protected boolean isPropertyAllowed(Object base, Object property) {
    if (isReflectionEntryPoint(base)) {
      return false;
    }
    if (property instanceof Number) {
      return base instanceof List || base.getClass().isArray();
    }
    return property instanceof String && properties.contains(property);
  }

  /**
   * Objects that give access to arbitrary code are never used as the base of a
   * method call or property access, whatever the allow-list contains.
   */
  protected boolean isReflectionEntryPoint(Object base) {
    return base instanceof Class
        || base instanceof ClassLoader
        || base instanceof Member
        || base instanceof Runtime
        || base instanceof ProcessBuilder
        || base instanceof Process;
  }

  /** resolves allow-listed functions only */
  protected class AllowListFunctionMapper extends FunctionMapper {

    protected final FunctionMapper delegate;

    public AllowListFunctionMapper(FunctionMapper delegate) {
      this.delegate = delegate;
    }

    @Override
    public Method resolveFunction(String prefix, String localName) {
      String name = prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
      if (!isFunctionAllowed(name)) {
        return null;
      }
      return delegate.resolveFunction(prefix, localName);
    }
  }

  /** resolves allow-listed beans, properties and methods only and refuses everything else */
  protected class AllowListElResolver extends ELResolver {

    protected final ELResolver delegate;

    public AllowListElResolver(ELResolver delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
      if (base == null) {
        if (!(property instanceof String) || !beans.contains(property)) {
          throw new ELException("The variable or bean '" + property + "' is not allowed in query expressions");
        }
      } else if (!isPropertyAllowed(base, property)) {
        throw new ELException("The property '" + property + "' of " + base.getClass().getName()
            + " is not allowed in query expressions");
      }
      return delegate.getValue(context, base, property);
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
      if (base == null || !(method instanceof String) || !isMethodAllowed(base, (String) method)) {
        throw new ELException("The method '" + method + "' of " + (base == null ? null : base.getClass().getName())
            + " is not allowed in query expressions");
      }
      return delegate.invoke(context, base, method, paramTypes, params);
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
      throw new ELException("Query expressions cannot assign values");
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
      return true;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
      return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
      return null;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
      return null;
    }
  }

  protected static Set<String> unmodifiableSet(String... names) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(names)));
  }

  protected static Set<String> copyOf(Collection<String> names) {
    Set<String> copy = new LinkedHashSet<>();
    if (names != null) {
      for (String name : names) {
        if (name != null && !name.trim().isEmpty()) {
          copy.add(name.trim());
        }
      }
    }
    return Collections.unmodifiableSet(copy);
  }

}
