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
package dev.fasti.engine.performance.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * An index as the database catalog describes it: the ordered key columns (included columns are
 * left out; a key part that is an expression is {@code null}), whether the database can use it,
 * and the predicate of a partial (filtered) index.
 */
public class ExistingIndex {

  protected final String table;
  protected final String name;
  protected final List<String> columns = new ArrayList<>();
  protected boolean usable = true;
  protected String predicate;

  public ExistingIndex(String table, String name) {
    this.table = table.toUpperCase(Locale.ROOT);
    this.name = name;
  }

  public ExistingIndex addColumn(String column) {
    columns.add(column == null ? null : column.toUpperCase(Locale.ROOT));
    return this;
  }

  public ExistingIndex usable(boolean usable) {
    this.usable = usable;
    return this;
  }

  public ExistingIndex predicate(String predicate) {
    this.predicate = normalizePredicate(predicate);
    return this;
  }

  public String getTable() {
    return table;
  }

  public String getName() {
    return name;
  }

  public List<String> getColumns() {
    return Collections.unmodifiableList(columns);
  }

  public boolean isUsable() {
    return usable;
  }

  public String getPredicate() {
    return predicate;
  }

  /**
   * Predicates are compared without case, white space, parentheses and type casts, which is how
   * PostgreSQL prints {@code RETRIES_ > 0 and SUSPENSION_STATE_ = 1} back:
   * {@code ((retries_ > 0) AND (suspension_state_ = 1))}.
   */
  public static String normalizePredicate(String predicate) {
    if (predicate == null) {
      return null;
    }
    String normalized = predicate.toUpperCase(Locale.ROOT).replaceAll("::[A-Z_]+", "").replaceAll("[\\s()\\[\\]\"`]", "");
    return normalized.isEmpty() ? null : normalized;
  }

  @Override
  public String toString() {
    return name + " on " + table + " " + columns + (predicate != null ? " where " + predicate : "") + (usable ? "" : " (unusable)");
  }
}
