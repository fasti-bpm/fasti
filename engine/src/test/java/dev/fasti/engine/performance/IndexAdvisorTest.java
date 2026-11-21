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
package dev.fasti.engine.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.fasti.engine.performance.impl.ExistingIndex;
import dev.fasti.engine.performance.impl.IndexAdvisor;
import dev.fasti.engine.performance.impl.IndexCatalog;
import dev.fasti.engine.performance.impl.RecommendedIndex;
import org.junit.Test;

public class IndexAdvisorTest {

  protected static final RecommendedIndex ASSIGNEE_DUE =
      new RecommendedIndex("FASTI_IDX_TASK_ASSIGNEE_DUE", "ACT_RU_TASK", "ASSIGNEE_", "DUE_DATE_");

  @Test
  public void shouldMatchByLeadingKeyColumnsWhateverTheName() {
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "ANY_NAME", "ASSIGNEE_", "DUE_DATE_"))).isTrue();
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("act_ru_task", "dba_idx", "assignee_", "due_date_", "create_time_"))).isTrue();
  }

  @Test
  public void shouldNotMatchOtherOrderShorterKeyOrOtherTable() {
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "I", "DUE_DATE_", "ASSIGNEE_"))).isFalse();
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "I", "ASSIGNEE_"))).isFalse();
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "I", "TENANT_ID_", "ASSIGNEE_", "DUE_DATE_"))).isFalse();
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_HI_TASKINST", "I", "ASSIGNEE_", "DUE_DATE_"))).isFalse();
  }

  @Test
  public void shouldNotMatchAnExpressionKey() {
    // a key part that is an expression has no column name
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "I", null, "DUE_DATE_"))).isFalse();
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "I", "ASSIGNEE_", null))).isFalse();
  }

  @Test
  public void shouldIgnoreUnusableIndexes() {
    assertThat(ASSIGNEE_DUE.isSatisfiedBy(index("ACT_RU_TASK", "I", "ASSIGNEE_", "DUE_DATE_").usable(false))).isFalse();
  }

  @Test
  public void shouldAcceptOnlyTheDeclaredPartialPredicate() {
    RecommendedIndex plain = new RecommendedIndex("FASTI_IDX_JOB_DUEDATE", "ACT_RU_JOB", "DUEDATE_");
    RecommendedIndex partial = plain.withPartialPredicate("RETRIES_ > 0 and SUSPENSION_STATE_ = 1");
    ExistingIndex asPostgresPrintsIt = index("ACT_RU_JOB", "fasti_idx_job_duedate", "duedate_")
        .predicate("((retries_ > 0) AND (suspension_state_ = 1))");
    ExistingIndex otherRows = index("ACT_RU_JOB", "I", "DUEDATE_").predicate("(retries_ = 0)");

    assertThat(partial.isSatisfiedBy(asPostgresPrintsIt)).isTrue();
    // the same rows with the terms the other way round
    assertThat(partial.isSatisfiedBy(index("ACT_RU_JOB", "I", "DUEDATE_")
        .predicate("((suspension_state_ = 1) AND (retries_ > 0))"))).isTrue();
    assertThat(partial.isSatisfiedBy(index("ACT_RU_JOB", "FULL", "DUEDATE_"))).isTrue();
    assertThat(partial.isSatisfiedBy(otherRows)).isFalse();
    // a filtered index never serves a recommendation without predicate
    assertThat(plain.isSatisfiedBy(asPostgresPrintsIt)).isFalse();
  }

  @Test
  public void shouldSortTheTermsOfAPredicate() {
    assertThat(ExistingIndex.normalizePredicate("((retries_ > 0) AND (suspension_state_ = 1))"))
        .isEqualTo(ExistingIndex.normalizePredicate("SUSPENSION_STATE_ = 1 and RETRIES_ > 0"))
        .isEqualTo("RETRIES_>0 AND SUSPENSION_STATE_=1");
    // a column name that contains AND is not split
    assertThat(ExistingIndex.normalizePredicate("(handler_type_)::text = 'x'::text")).isEqualTo("HANDLER_TYPE_='X'");
    assertThat(ExistingIndex.normalizePredicate(" ")).isNull();
  }

  @Test
  public void shouldLeadWithTheSuspensionStateWhereThereIsNoPartialIndex() {
    for (String databaseType : Arrays.asList(IndexCatalog.H2, IndexCatalog.MYSQL, IndexCatalog.ORACLE, IndexCatalog.MSSQL,
        IndexCatalog.DB2)) {
      RecommendedIndex job = recommended(databaseType, IndexAdvisor.JOB_DUE_DATE);
      assertThat(job.getColumns()).as(databaseType).containsExactly("SUSPENSION_STATE_", "DUEDATE_", "RETRIES_");
      assertThat(job.isSatisfiedBy(index("ACT_RU_JOB", "I", "SUSPENSION_STATE_", "DUEDATE_", "RETRIES_"))).isTrue();
      // the index of the first variant of the scripts reads every failed and suspended job
      assertThat(job.isSatisfiedBy(index("ACT_RU_JOB", "I", "DUEDATE_"))).isFalse();
      assertThat(job.isSatisfiedBy(index("ACT_RU_JOB", "I", "SUSPENSION_STATE_", "DUEDATE_"))).isFalse();
    }
    RecommendedIndex postgres = recommended(IndexCatalog.POSTGRES, IndexAdvisor.JOB_DUE_DATE);
    assertThat(postgres.getColumns()).containsExactly("DUEDATE_");
    assertThat(postgres.getPartialPredicate()).isNotNull();
  }

  @Test
  public void shouldAcceptOnlyBtreeIndexesWhereTheCatalogTellsTheAccessMethod() {
    RecommendedIndex due = recommended(IndexCatalog.POSTGRES, "FASTI_IDX_TASK_DUE");

    assertThat(due.isSatisfiedBy(index("ACT_RU_TASK", "I", "DUE_DATE_").accessMethod("btree"))).isTrue();
    // equality only: no range of due dates, no order
    assertThat(due.isSatisfiedBy(index("ACT_RU_TASK", "I", "DUE_DATE_").accessMethod("hash"))).isFalse();
    assertThat(due.isSatisfiedBy(index("ACT_RU_TASK", "I", "DUE_DATE_").accessMethod("BRIN"))).isFalse();
    // databases whose catalog query does not tell
    assertThat(due.isSatisfiedBy(index("ACT_RU_TASK", "I", "DUE_DATE_"))).isTrue();
  }

  @Test
  public void shouldRequireNullsFirstOnPostgresOnlyWhereAcquisitionOrdersByDueDateWithNulls() {
    RecommendedIndex byDueDate = IndexAdvisor.recommendedIndexes(IndexCatalog.POSTGRES, true).get(0);
    RecommendedIndex byDefault = IndexAdvisor.recommendedIndexes(IndexCatalog.POSTGRES, false).get(0);
    String predicate = "((retries_ > 0) AND (suspension_state_ = 1))";
    ExistingIndex ascending = new ExistingIndex("ACT_RU_JOB", "I").addColumn("duedate_", false).predicate(predicate).accessMethod("btree");
    ExistingIndex nullsFirst = new ExistingIndex("ACT_RU_JOB", "I").addColumn("duedate_", true).predicate(predicate).accessMethod("btree");

    assertThat(byDueDate.getName()).isEqualTo(IndexAdvisor.JOB_DUE_DATE);
    assertThat(byDueDate.toString()).contains("(DUEDATE_ NULLS FIRST)").contains("where RETRIES_ > 0 and SUSPENSION_STATE_ = 1");
    assertThat(byDueDate.isSatisfiedBy(nullsFirst)).isTrue();
    // an ascending index lets the planner walk it in due date order when nothing is due
    assertThat(byDueDate.isSatisfiedBy(ascending)).isFalse();
    assertThat(byDefault.isSatisfiedBy(ascending)).isTrue();
    assertThat(byDefault.isSatisfiedBy(nullsFirst)).isTrue();
    // only the first column has to be NULLS FIRST; unknown order (other databases) counts
    assertThat(byDueDate.isSatisfiedBy(index("ACT_RU_JOB", "I", "DUEDATE_").predicate(predicate))).isTrue();
  }

  @Test
  public void shouldReportMissingIndexesOfKnownTablesOnly() {
    List<RecommendedIndex> recommended = IndexAdvisor.recommendedIndexes(IndexCatalog.POSTGRES);
    Map<String, List<ExistingIndex>> existing = new HashMap<>();
    existing.put("ACT_RU_TASK", Arrays.asList(index("ACT_RU_TASK", "PK", "ID_"), index("ACT_RU_TASK", "X", "DUE_DATE_")));
    existing.put("ACT_RU_JOB", Collections.singletonList(index("ACT_RU_JOB", "PK", "ID_")));
    // ACT_ID_INFO: nothing known, so nothing to say

    List<String> missing = IndexAdvisor.missing(recommended, existing).stream().map(RecommendedIndex::getName).collect(Collectors.toList());

    assertThat(missing).containsExactly("FASTI_IDX_JOB_DUEDATE", "FASTI_IDX_TASK_ASSIGNEE_DUE");
    assertThat(IndexAdvisor.missing(recommended, Collections.emptyMap())).isEmpty();
  }

  @Test
  public void shouldRecommendTheSameIndexesForEveryDatabase() {
    List<String> names = names(IndexCatalog.POSTGRES);
    assertThat(names).isNotEmpty().allMatch(name -> name.startsWith("FASTI_IDX_"));
    for (String databaseType : Arrays.asList(IndexCatalog.H2, IndexCatalog.MYSQL, IndexCatalog.ORACLE, IndexCatalog.MSSQL, IndexCatalog.DB2)) {
      assertThat(names(databaseType)).isEqualTo(names);
    }
  }

  @Test
  public void shouldTakeSchemaAndNamePrefixFromTheTablePrefix() {
    IndexCatalog schemaPrefix = new IndexCatalog(IndexCatalog.POSTGRES, null, "bpm.");
    assertThat(schemaPrefix.getSchema()).isEqualTo("bpm");
    assertThat(schemaPrefix.getNamePrefix()).isEmpty();

    IndexCatalog namePrefix = new IndexCatalog(IndexCatalog.POSTGRES, "engine", "pre_");
    assertThat(namePrefix.getSchema()).isEqualTo("engine");
    assertThat(namePrefix.getNamePrefix()).isEqualTo("PRE_");

    IndexCatalog catalogAndSchema = new IndexCatalog(IndexCatalog.MSSQL, null, "db.dbo.");
    assertThat(catalogAndSchema.getSchema()).isEqualTo("dbo");
    // the catalog views of that database are read
    assertThat(catalogAndSchema.getCatalog()).isEqualTo("db");
    assertThat(schemaPrefix.getCatalog()).isNull();

    assertThat(new IndexCatalog(IndexCatalog.H2, null, null).getSchema()).isNull();
  }

  @Test
  public void shouldCheckWhatTheScriptsCreateAndDropEverythingTheyMention() throws IOException {
    for (String databaseType : Arrays.asList(IndexCatalog.H2, IndexCatalog.MYSQL, IndexCatalog.POSTGRES, IndexCatalog.ORACLE,
        IndexCatalog.MSSQL, IndexCatalog.DB2)) {
      List<String> create = lines("fasti." + databaseType + ".create.perf.indexes.sql");
      List<String> drop = lines("fasti." + databaseType + ".drop.perf.indexes.sql");
      Set<String> active = new TreeSet<>();
      Set<String> mentioned = new TreeSet<>();
      for (String line : create) {
        Matcher matcher = CREATED.matcher(line);
        if (matcher.find()) {
          mentioned.add(matcher.group(1));
          if (!line.trim().startsWith("--")) {
            active.add(matcher.group(1));
          }
        }
      }
      Set<String> dropped = new TreeSet<>();
      for (String line : drop) {
        Matcher matcher = DROPPED.matcher(line);
        if (matcher.find() && !line.trim().startsWith("--")) {
          dropped.add(matcher.group(1));
        }
      }
      assertThat(active).as(databaseType).containsExactlyInAnyOrderElementsOf(names(databaseType));
      assertThat(dropped).as(databaseType).containsExactlyInAnyOrderElementsOf(mentioned);
    }
  }

  protected static final Pattern CREATED = Pattern.compile("(?i)create index (?:concurrently )?(?:if not exists )?(FASTI_IDX_[A-Z_]+)");
  protected static final Pattern DROPPED = Pattern.compile("(?i)drop index (?:concurrently )?(?:if exists )?(FASTI_IDX_[A-Z_]+)");

  protected static List<String> lines(String script) throws IOException {
    String resource = "dev/fasti/engine/performance/db/optional/" + script;
    try (InputStream stream = IndexAdvisorTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertThat(stream).as(resource).isNotNull();
      return Arrays.asList(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n"));
    }
  }

  protected static List<String> names(String databaseType) {
    return IndexAdvisor.recommendedIndexes(databaseType).stream().map(RecommendedIndex::getName).collect(Collectors.toList());
  }

  protected static RecommendedIndex recommended(String databaseType, String name) {
    return IndexAdvisor.recommendedIndexes(databaseType).stream().filter(index -> index.getName().equals(name)).findFirst().get();
  }

  protected static ExistingIndex index(String table, String name, String... columns) {
    ExistingIndex index = new ExistingIndex(table, name);
    for (String column : columns) {
      index.addColumn(column);
    }
    return index;
  }
}
