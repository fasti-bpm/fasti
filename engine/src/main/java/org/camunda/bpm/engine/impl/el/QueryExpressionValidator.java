/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.el;

import org.camunda.bpm.engine.BadUserRequestException;

/**
 * Decides whether an expression that is used as a query criterion (for example
 * <code>taskAssigneeExpression("${currentUser()}")</code>) may be evaluated.
 *
 * <p>Query expressions can be submitted by every user who may run a query or
 * create a filter. The validator is asked before such an expression is
 * accepted (when a filter is saved) and again before it is evaluated (when a
 * query or filter is executed), so an implementation must reject everything
 * it does not positively know to be harmless.</p>
 *
 * <p>The validator in use is configured with
 * <code>ProcessEngineConfigurationImpl#setQueryExpressionValidator</code>; it
 * is only consulted while <code>queryExpressionAllowListEnabled</code> is
 * <code>true</code>. The default is {@link QueryExpressionAllowList}. Expressions
 * accepted by any other implementation are evaluated by the expression manager
 * of the process engine without further restrictions, so a custom validator
 * should be at least as strict as the default one.</p>
 */
public interface QueryExpressionValidator {

  /**
   * @param expression the expression as submitted, may be <code>null</code>
   * @throws BadUserRequestException if the expression must not be evaluated
   */
  void validate(String expression);

}
