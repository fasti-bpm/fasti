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
 * the predicate of a partial (filtered) index, its access method and, per key column, where NULL
 * comes in ascending order, as far as the catalog of the database tells.
 */
public class ExistingIndex {

  protected final String table;
  protected final String name;
  protected final List<String> columns = new ArrayList<>();
  protected final List<Boolean> nullsFirst = new ArrayList<>();
  protected boolean usable = true;
  protected String predicate;
  protected String accessMethod;

  public ExistingIndex(String table, String name) {
    this.table = table.toUpperCase(Locale.ROOT);
    this.name = name;
  }

  public ExistingIndex addColumn(String column) {
    return addColumn(column, null);
  }

  /**
   * @param nullsFirst whether NULL comes first when the index is read in ascending order of this
   *        key column (PostgreSQL: {@code NULLS FIRST}, or {@code DESC NULLS LAST}); {@code null}
   *        if the catalog does not tell
   */
  public ExistingIndex addColumn(String column, Boolean nullsFirst) {
    columns.add(column == null ? null : column.toUpperCase(Locale.ROOT));
    this.nullsFirst.add(nullsFirst);
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

  /** The access method (PostgreSQL: btree, hash, gin, brin, ...), or {@code null} if not known. */
  public ExistingIndex accessMethod(String accessMethod) {
    this.accessMethod = accessMethod == null ? null : accessMethod.toLowerCase(Locale.ROOT);
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

  /** Per key column, whether NULL comes first in ascending order; an element is {@code null} if not known. */
  public List<Boolean> getNullsFirst() {
    return Collections.unmodifiableList(nullsFirst);
  }

  public boolean isUsable() {
    return usable;
  }

  public String getPredicate() {
    return predicate;
  }

  public String getAccessMethod() {
    return accessMethod;
  }

  /**
   * Predicates are compared without case, white space, parentheses and type casts, which is how
   * PostgreSQL prints {@code RETRIES_ > 0 and SUSPENSION_STATE_ = 1} back:
   * {@code ((retries_ > 0) AND (suspension_state_ = 1))}. The terms of a conjunction are sorted,
   * so that {@code SUSPENSION_STATE_ = 1 and RETRIES_ > 0} compares equal.
   */
  public static String normalizePredicate(String predicate) {
    if (predicate == null) {
      return null;
    }
    String spaced = predicate.toUpperCase(Locale.ROOT).replaceAll("::[A-Z_]+(\\s+(VARYING|PRECISION))?", "")
        .replaceAll("[()\\[\\]\"`]", " ");
    List<String> terms = new ArrayList<>();
    for (String term : spaced.trim().split("\\s+AND\\s+")) {
      String compact = term.replaceAll("\\s", "");
      if (!compact.isEmpty()) {
        terms.add(compact);
      }
    }
    if (terms.isEmpty()) {
      return null;
    }
    Collections.sort(terms);
    return String.join(" AND ", terms);
  }

  @Override
  public String toString() {
    return name + " on " + table + " " + columns + (predicate != null ? " where " + predicate : "")
        + (accessMethod != null ? " using " + accessMethod : "") + (usable ? "" : " (unusable)");
  }
}
