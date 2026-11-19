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
package org.camunda.bpm.engine.impl;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNull;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.impl.QueryValidators.AdhocQueryValidator;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.el.ExpressionManager;
import org.camunda.bpm.engine.impl.el.QueryExpressionAllowList;
import org.camunda.bpm.engine.impl.el.QueryExpressionValidator;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.util.ImmutablePair;
import org.camunda.bpm.engine.impl.util.QueryMaxResultsLimitUtil;
import org.camunda.bpm.engine.query.Query;
import org.camunda.bpm.engine.query.QueryProperty;
import org.joda.time.DateTime;


/**
 * Abstract superclass for all query types.
 *
 * @author Joram Barrez
 */
public abstract class AbstractQuery<T extends Query<?,?>, U> extends ListQueryParameterObject implements Command<Object>, Query<T,U>, Serializable {

  private static final long serialVersionUID = 1L;

  public static final String SORTORDER_ASC = "asc";
  public static final String SORTORDER_DESC = "desc";

  protected enum ResultType {
    LIST, LIST_PAGE, LIST_IDS, LIST_DEPLOYMENT_ID_MAPPINGS, SINGLE_RESULT, COUNT
  }
  protected transient CommandExecutor commandExecutor;

  protected ResultType resultType;

  protected Map<String, String> expressions = new HashMap<>();

  protected Set<Validator<AbstractQuery<?, ?>>> validators = new HashSet<>();

  protected boolean maxResultsLimitEnabled;

  protected AbstractQuery() {
  }

  protected AbstractQuery(CommandExecutor commandExecutor) {
    this.commandExecutor = commandExecutor;

    // all queries that are created with a dedicated command executor
    // are treated as adhoc queries (i.e. queries not created in the context
    // of a command)
    addValidator(AdhocQueryValidator.<AbstractQuery<?, ?>>get());
  }

  public AbstractQuery<T, U> setCommandExecutor(CommandExecutor commandExecutor) {
    this.commandExecutor = commandExecutor;
    return this;
  }

  public T orderBy(QueryProperty property) {
    return orderBy(new QueryOrderingProperty(null, property));
  }

  @SuppressWarnings("unchecked")
  public T orderBy(QueryOrderingProperty orderProperty) {
    this.orderingProperties.add(orderProperty);
    return (T) this;
  }

  public T asc() {
    return direction(Direction.ASCENDING);
  }

  public T desc() {
    return direction(Direction.DESCENDING);
  }

  @SuppressWarnings("unchecked")
  public T direction(Direction direction) {
    QueryOrderingProperty currentOrderingProperty = null;

    if (!orderingProperties.isEmpty()) {
      currentOrderingProperty = orderingProperties.get(orderingProperties.size() - 1);
    }

    ensureNotNull(NotValidException.class, "You should call any of the orderBy methods first before specifying a direction", "currentOrderingProperty", currentOrderingProperty);

    if (currentOrderingProperty.getDirection() != null) {
      ensureNull(NotValidException.class, "Invalid query: can specify only one direction desc() or asc() for an ordering constraint", "direction", direction);
    }

    currentOrderingProperty.setDirection(direction);
    return (T) this;
  }

  protected void checkQueryOk() {

    for (QueryOrderingProperty orderingProperty : orderingProperties) {
      ensureNotNull(NotValidException.class, "Invalid query: call asc() or desc() after using orderByXX()", "direction", orderingProperty.getDirection());
    }

  }

  @SuppressWarnings("unchecked")
  public U singleResult() {
    this.resultType = ResultType.SINGLE_RESULT;
    return (U) executeResult(resultType);
  }

  @SuppressWarnings("unchecked")
  public List<U> list() {
    this.resultType = ResultType.LIST;
    return (List<U>) executeResult(resultType);
  }

  @SuppressWarnings("unchecked")
  public List<U> listPage(int firstResult, int maxResults) {
    this.firstResult = firstResult;
    this.maxResults = maxResults;
    this.resultType = ResultType.LIST_PAGE;
    return (List<U>) executeResult(resultType);
  }

  public Object executeResult(ResultType resultType) {

    if (commandExecutor != null) {
      if (!maxResultsLimitEnabled) {
        maxResultsLimitEnabled = Context.getCommandContext() == null;
      }

      return commandExecutor.execute(this);
    }

    switch (resultType) {
      case SINGLE_RESULT:
        return executeSingleResult(Context.getCommandContext());
      case LIST_PAGE:
      case LIST:
        return evaluateExpressionsAndExecuteList(Context.getCommandContext(), null);
      default:
        throw new ProcessEngineException("Unknown result type!");
    }
  }

  public long count() {
    this.resultType = ResultType.COUNT;
    if (commandExecutor!=null) {
      return (Long) commandExecutor.execute(this);
    }
    return evaluateExpressionsAndExecuteCount(Context.getCommandContext());
  }

  @SuppressWarnings("unchecked")
  public List<U> unlimitedList() {
    this.resultType = ResultType.LIST;
    if (commandExecutor != null) {
      return (List<U>) commandExecutor.execute(this);
    }
    return evaluateExpressionsAndExecuteList(Context.getCommandContext(), null);
  }

  public Object execute(CommandContext commandContext) {
    if (resultType==ResultType.LIST) {
      return evaluateExpressionsAndExecuteList(commandContext, null);
    } else if (resultType==ResultType.SINGLE_RESULT) {
      return executeSingleResult(commandContext);
    } else if (resultType==ResultType.LIST_PAGE) {
      return evaluateExpressionsAndExecuteList(commandContext, null);
    } else if (resultType == ResultType.LIST_IDS) {
      return evaluateExpressionsAndExecuteIdsList(commandContext);
    } else if (resultType == ResultType.LIST_DEPLOYMENT_ID_MAPPINGS) {
      return evaluateExpressionsAndExecuteDeploymentIdMappingsList(commandContext);
    } else {
      return evaluateExpressionsAndExecuteCount(commandContext);
    }
  }

  public long evaluateExpressionsAndExecuteCount(CommandContext commandContext) {
    validate();
    evaluateExpressions();
    return !hasExcludingConditions() ? executeCount(commandContext) : 0l;
  }

  public abstract long executeCount(CommandContext commandContext);

  public List<U> evaluateExpressionsAndExecuteList(CommandContext commandContext, Page page) {
    checkMaxResultsLimit();
    validate();
    evaluateExpressions();
    return !hasExcludingConditions() ? executeList(commandContext, page) : new ArrayList<>();
  }

  /**
   * Whether or not the query has excluding conditions. If the query has excluding conditions,
   * (e.g. task due date before and after are excluding), the SQL query is avoided and a default result is
   * returned. The returned result is the same as if the SQL was executed and there were no entries.
   *
   * @return {@code true} if the query does have excluding conditions, {@code false} otherwise
   */
  protected boolean hasExcludingConditions() {
    return false;
  }

  /**
   * Executes the actual query to retrieve the list of results.
   * @param page used if the results must be paged. If null, no paging will be applied.
   */
  public abstract List<U> executeList(CommandContext commandContext, Page page);

  public U executeSingleResult(CommandContext commandContext) {
    disableMaxResultsLimit();
    List<U> results = evaluateExpressionsAndExecuteList(commandContext, new Page(0, 2));
    if (results.size() == 1) {
      return results.get(0);
    } else if (results.size() > 1) {
     throw new ProcessEngineException("Query return "+results.size()+" results instead of max 1");
    }
    return null;
  }

  public Map<String, String> getExpressions() {
    return expressions;
  }

  public void setExpressions(Map<String, String> expressions) {
    this.expressions = expressions;
  }

  public void addExpression(String key, String expression) {
    this.expressions.put(key, expression);
  }

  protected void evaluateExpressions() {
    // we cannot iterate directly on the entry set cause the expressions
    // are removed by the setter methods during the iteration
    ArrayList<Map.Entry<String, String>> entries = new ArrayList<>(expressions.entrySet());

    for (Map.Entry<String, String> entry : entries) {
      String methodName = entry.getKey();
      String expression = entry.getValue();

      // must be repeated here: this is the only place every query passes
      // before its expressions are evaluated, whatever it was created from
      ensureExpressionAllowed(methodName, expression);

      Object value;

      try {
        value = evaluateExpression(expression);
      }
      catch (BadUserRequestException e) {
        throw e;
      }
      catch (ProcessEngineException e) {
        throw new ProcessEngineException("Unable to resolve expression '" + expression + "' for method '" + methodName + "' on class '" + getClass().getCanonicalName() + "'", e);
      }

      // automatically convert DateTime to date
      if (value instanceof DateTime) {
        value = ((DateTime) value).toDate();
      }

      try {
        applyExpressionValue(methodName, value);
      } catch (InvocationTargetException e) {
        throw new ProcessEngineException("Unable to invoke method '" + methodName + "' on class '" + getClass().getCanonicalName() + "'", e);
      } catch (IllegalAccessException e) {
        throw new ProcessEngineException("Unable to access method '" + methodName + "' on class '" + getClass().getCanonicalName() + "'", e);
      }
    }
  }

  /**
   * Sets the criterion of the given method to the value of its expression.
   */
  protected void applyExpressionValue(String methodName, Object value)
      throws InvocationTargetException, IllegalAccessException {
    getMethod(methodName).invoke(this, value);
  }

  /**
   * Evaluates a query expression. Unless the allow-list for query expressions
   * is switched off, the expression never reaches the expression manager of
   * the process engine without having been accepted by the configured
   * {@link QueryExpressionValidator}; with the default validator it is
   * evaluated in isolation by {@link QueryExpressionAllowList}.
   */
  protected Object evaluateExpression(String expression) {
    ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
    ExpressionManager expressionManager = configuration.getExpressionManager();

    if (configuration.isQueryExpressionAllowListEnabled()) {
      QueryExpressionValidator validator = configuration.getQueryExpressionValidator();
      if (validator instanceof QueryExpressionAllowList) {
        return ((QueryExpressionAllowList) validator).evaluate(expression, expressionManager);
      }
      ensureNotNull(BadUserRequestException.class,
          "Query expressions cannot be evaluated without a query expression validator", "queryExpressionValidator", validator);
      validator.validate(expression);
    }

    return expressionManager
        .createExpression(expression)
        .getValue(null);
  }

  /**
   * Ensures that all expressions of this query may be evaluated: every
   * expression must belong to a criterion that supports expressions (see
   * {@link #getExpressionKeys()}) and, unless the allow-list for query
   * expressions is switched off, must be accepted by the configured
   * {@link QueryExpressionValidator}.
   *
   * @throws BadUserRequestException if an expression is not allowed
   */
  public void ensureExpressionsAllowed() {
    for (Map.Entry<String, String> entry : new ArrayList<>(expressions.entrySet())) {
      ensureExpressionAllowed(entry.getKey(), entry.getValue());
    }
  }

  protected void ensureExpressionAllowed(String key, String expression) {
    if (!getExpressionKeys().contains(key)) {
      throw new BadUserRequestException("The criterion '" + key + "' of query type '" + getClass().getSimpleName()
          + "' cannot be set by an expression.");
    }

    ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
    if (configuration.isQueryExpressionAllowListEnabled()) {
      QueryExpressionValidator validator = configuration.getQueryExpressionValidator();
      ensureNotNull(BadUserRequestException.class,
          "Query expressions cannot be used without a query expression validator", "queryExpressionValidator", validator);
      validator.validate(expression);
    }
  }

  /**
   * Returns the names of the criteria of this query that can be set by an
   * expression. The name of such a criterion is the name of the method that
   * is invoked with the value of the expression; no other method is ever
   * invoked on behalf of an expression, in particular not because a stored
   * query names it.
   */
  protected Set<String> getExpressionKeys() {
    return Collections.emptySet();
  }

  protected Method getMethod(String methodName) {
    for (Method method : getClass().getDeclaredMethods()) {
      if (method.getName().equals(methodName)) {
        return method;
      }
    }
    throw new ProcessEngineException("Unable to find method '" + methodName + "' on class '" + getClass().getCanonicalName() + "'");
  }

  public T extend(T extendingQuery) {
    throw new ProcessEngineException("Extending of query type '" + extendingQuery.getClass().getCanonicalName() + "' currently not supported");
  }

  protected void mergeOrdering(AbstractQuery<?, ?> extendedQuery, AbstractQuery<?, ?> extendingQuery) {
    extendedQuery.orderingProperties = this.orderingProperties;
    if (extendingQuery.orderingProperties != null) {
       if (extendedQuery.orderingProperties == null) {
         extendedQuery.orderingProperties = extendingQuery.orderingProperties;
       }
       else {
         extendedQuery.orderingProperties.addAll(extendingQuery.orderingProperties);
       }
    }
  }

  protected void mergeExpressions(AbstractQuery<?, ?> extendedQuery, AbstractQuery<?, ?> extendingQuery) {
    Map<String, String> mergedExpressions = new HashMap<>(extendingQuery.getExpressions());
    for (Map.Entry<String, String> entry : this.getExpressions().entrySet()) {
      if (!mergedExpressions.containsKey(entry.getKey())) {
        mergedExpressions.put(entry.getKey(), entry.getValue());
      }
    }
    extendedQuery.setExpressions(mergedExpressions);
  }

  public void validate() {
    for (Validator<AbstractQuery<?, ?>> validator : validators) {
      validate(validator);
    }
  }

  /**
   * Applies the given validator to this query and to every query nested in it
   * (see {@link #getNestedQueries()}), however deep the nesting is. A nested
   * query is a part of the query it belongs to and therefore must never be
   * subject to weaker rules than the query that is actually executed, stored
   * or used to extend another query.
   */
  public void validate(Validator<AbstractQuery<?, ?>> validator) {
    Set<AbstractQuery<?, ?>> validatedQueries = Collections.newSetFromMap(new IdentityHashMap<>());
    validate(validator, validatedQueries);
  }

  protected final void validate(Validator<AbstractQuery<?, ?>> validator, Set<AbstractQuery<?, ?>> validatedQueries) {
    if (!validatedQueries.add(this)) {
      // nested queries may refer back to the query they belong to
      return;
    }

    validator.validate(this);

    List<? extends AbstractQuery<?, ?>> nestedQueries = getNestedQueries();
    if (nestedQueries != null) {
      // iterate on a copy, the list may be shared with the nested queries
      for (AbstractQuery<?, ?> nestedQuery : new ArrayList<>(nestedQueries)) {
        if (nestedQuery != null) {
          nestedQuery.validate(validator, validatedQueries);
        }
      }
    }
  }

  /**
   * Returns the queries that are a part of this query, e.g. the queries created by
   * <code>or()</code> or attached as OR queries. Query types that support nested
   * queries must return them here so that they are validated together with this
   * query; the returned list may contain this query itself.
   */
  protected List<? extends AbstractQuery<?, ?>> getNestedQueries() {
    return Collections.emptyList();
  }

  public void addValidator(Validator<AbstractQuery<?, ?>> validator) {
    validators.add(validator);
  }

  public void removeValidator(Validator<AbstractQuery<?, ?>> validator) {
    validators.remove(validator);
  }

  @SuppressWarnings("unchecked")
  public List<String> listIds() {
    this.resultType = ResultType.LIST_IDS;
    List<String> ids = null;
    if (commandExecutor != null) {
      ids = (List<String>) commandExecutor.execute(this);
    } else {
      ids = evaluateExpressionsAndExecuteIdsList(Context.getCommandContext());
    }

    if (ids != null) {
      QueryMaxResultsLimitUtil.checkMaxResultsLimit(ids.size());
    }

    return ids;
  }

  @SuppressWarnings("unchecked")
  public List<ImmutablePair<String, String>> listDeploymentIdMappings() {
    this.resultType = ResultType.LIST_DEPLOYMENT_ID_MAPPINGS;
    List<ImmutablePair<String, String>> ids = null;
    if (commandExecutor != null) {
      ids = (List<ImmutablePair<String, String>>) commandExecutor.execute(this);
    } else {
      ids = evaluateExpressionsAndExecuteDeploymentIdMappingsList(Context.getCommandContext());
    }

    if (ids != null) {
      QueryMaxResultsLimitUtil.checkMaxResultsLimit(ids.size());
    }

    return ids;
  }

  public List<String> evaluateExpressionsAndExecuteIdsList(CommandContext commandContext) {
    validate();
    evaluateExpressions();
    return !hasExcludingConditions() ? executeIdsList(commandContext) : new ArrayList<>();
  }

  public List<String> executeIdsList(CommandContext commandContext) {
    throw new UnsupportedOperationException("executeIdsList not supported by " + getClass().getCanonicalName());
  }

  public List<ImmutablePair<String, String>> evaluateExpressionsAndExecuteDeploymentIdMappingsList(CommandContext commandContext) {
    validate();
    evaluateExpressions();
    return !hasExcludingConditions() ? executeDeploymentIdMappingsList(commandContext) : new ArrayList<>();
  }

  public List<ImmutablePair<String, String>> executeDeploymentIdMappingsList(CommandContext commandContext) {
    throw new UnsupportedOperationException("executeDeploymentIdMappingsList not supported by " + getClass().getCanonicalName());
  }

  protected void checkMaxResultsLimit() {
    if (maxResultsLimitEnabled) {
      QueryMaxResultsLimitUtil.checkMaxResultsLimit(maxResults);
    }
  }

  public void enableMaxResultsLimit() {
    maxResultsLimitEnabled = true;
  }

  public void disableMaxResultsLimit() {
    maxResultsLimitEnabled = false;
  }

}
