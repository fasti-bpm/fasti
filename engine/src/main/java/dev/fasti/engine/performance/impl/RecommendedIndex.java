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

  protected final String name;
  protected final String table;
  protected final List<String> columns;
  protected final boolean nullsIndexed;
  protected final String partialPredicate;

  public RecommendedIndex(String name, String table, String... columns) {
    this(name, table, Arrays.asList(columns), false, null);
  }

  protected RecommendedIndex(String name, String table, List<String> columns, boolean nullsIndexed, String partialPredicate) {
    this.name = name;
    this.table = table.toUpperCase(Locale.ROOT);
    List<String> upper = new ArrayList<>();
    for (String column : columns) {
      upper.add(column.toUpperCase(Locale.ROOT));
    }
    this.columns = Collections.unmodifiableList(upper);
    this.nullsIndexed = nullsIndexed;
    this.partialPredicate = ExistingIndex.normalizePredicate(partialPredicate);
  }

  /**
   * The index has to contain the rows in which the recommended columns are all NULL. Oracle leaves
   * a row out of an index when all its key columns are NULL, so there the index needs a further
   * key part that is never NULL: the constant of the scripts (a generated SYS_NC column in the
   * catalog) or the primary key column ID_.
   */
  public RecommendedIndex withNullsIndexed() {
    return new RecommendedIndex(name, table, columns, true, partialPredicate);
  }

  /**
   * A partial index with exactly this predicate counts as well (the PostgreSQL script creates
   * one). Partial indexes with any other predicate never count.
   */
  public RecommendedIndex withPartialPredicate(String predicate) {
    return new RecommendedIndex(name, table, columns, nullsIndexed, predicate);
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

  public boolean isNullsIndexed() {
    return nullsIndexed;
  }

  public String getPartialPredicate() {
    return partialPredicate;
  }

  /**
   * Whether the given index serves this recommendation: it is usable, its key columns start with
   * the recommended columns in the same order, and it is not restricted to other rows.
   */
  public boolean isSatisfiedBy(ExistingIndex index) {
    if (!index.isUsable() || !table.equals(index.getTable())) {
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
    if (nullsIndexed) {
      for (String further : existing.subList(columns.size(), existing.size())) {
        if (further != null && (further.startsWith("SYS_NC") || "ID_".equals(further))) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return name + " on " + table + " (" + String.join(", ", columns) + ")";
  }
}
