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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * An optional index of the scripts {@code sql/optional/<database>_fasti_perf_indexes_create.sql}:
 * the table, the ordered key columns an existing index has to start with to count as present, and
 * the name the script gives it (used in messages only, never for matching).
 */
public class RecommendedIndex {

  /** The only access method that serves ranges and order, where the catalog tells one (PostgreSQL). */
  public static final String BTREE = "btree";

  protected final String name;
  protected final String table;
  protected final List<String> columns;
  protected final boolean nullsFirst;
  protected final String partialPredicate;
  /** The predicate as written, for messages. */
  protected final String predicateText;

  public RecommendedIndex(String name, String table, String... columns) {
    this(name, table, Arrays.asList(columns), false, null);
  }

  protected RecommendedIndex(String name, String table, List<String> columns, boolean nullsFirst, String partialPredicate) {
    this.name = name;
    this.table = table.toUpperCase(Locale.ROOT);
    List<String> upper = new ArrayList<>();
    for (String column : columns) {
      upper.add(column.toUpperCase(Locale.ROOT));
    }
    this.columns = Collections.unmodifiableList(upper);
    this.nullsFirst = nullsFirst;
    this.partialPredicate = ExistingIndex.normalizePredicate(partialPredicate);
    this.predicateText = partialPredicate;
  }

  /**
   * The index must not be able to return the first column in ascending order with NULL last
   * (PostgreSQL: it is {@code NULLS FIRST}, or {@code DESC NULLS LAST}). Required where the
   * planner would otherwise walk the whole index in that order (job acquisition by due date on
   * PostgreSQL, see docs/performance.md). Checked only where the catalog tells the order.
   */
  public RecommendedIndex withNullsFirst() {
    return new RecommendedIndex(name, table, columns, true, predicateText);
  }

  /**
   * A partial index with exactly this predicate counts as well (the PostgreSQL script creates
   * one). Partial indexes with any other predicate never count.
   */
  public RecommendedIndex withPartialPredicate(String predicate) {
    return new RecommendedIndex(name, table, columns, nullsFirst, predicate);
  }

  public String getName() {
    return name;
  }

  public String getTable() {
    return table;
  }

  public List<String> getColumns() {
    return columns;
  }

  public boolean isNullsFirst() {
    return nullsFirst;
  }

  public String getPartialPredicate() {
    return partialPredicate;
  }

  /**
   * Whether the given index serves this recommendation: it is usable, a B-tree where the catalog
   * tells the access method, its key columns start with the recommended columns in the same
   * order, it is not restricted to other rows, and NULL comes first where that is required.
   */
  public boolean isSatisfiedBy(ExistingIndex index) {
    if (!index.isUsable() || !table.equals(index.getTable())) {
      return false;
    }
    if (index.getAccessMethod() != null && !BTREE.equals(index.getAccessMethod())) {
      return false;
    }
    if (index.getPredicate() != null && !index.getPredicate().equals(partialPredicate)) {
      return false;
    }
    List<String> existing = index.getColumns();
    if (existing.size() < columns.size()) {
      return false;
    }
    for (int i = 0; i < columns.size(); i++) {
      if (!columns.get(i).equals(existing.get(i))) {
        return false;
      }
    }
    if (nullsFirst) {
      Boolean first = index.getNullsFirst().isEmpty() ? null : index.getNullsFirst().get(0);
      return first == null || first;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder(name).append(" on ").append(table).append(" (");
    for (int i = 0; i < columns.size(); i++) {
      text.append(i == 0 ? "" : ", ").append(columns.get(i)).append(i == 0 && nullsFirst ? " NULLS FIRST" : "");
    }
    text.append(")");
    if (partialPredicate != null) {
      text.append(" where ").append(predicateText);
    }
    return text.toString();
  }
}
