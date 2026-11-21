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
package dev.fasti.engine.workload.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import dev.fasti.engine.workload.TaskLoad;
import org.junit.Test;

/** How the grouped rows of the database are matched to the ids asked for. */
public class TaskWorkloadInOrderTest {

  static TaskLoadEntity row(String id, long open, Date oldest) {
    TaskLoadEntity row = new TaskLoadEntity(id);
    row.setOpen(open);
    row.setUnassigned(open);
    row.setOverdue(0L);
    row.setOldest(oldest);
    return row;
  }

  @Test
  public void shouldMatchExactlyFirst() {
    List<TaskLoad> loads = TaskWorkloadQueryImpl.inOrder(Arrays.asList("b", "a", "c"),
        Arrays.asList(row("a", 1, null), row("b", 2, null)));

    assertThat(loads).extracting(TaskLoad::getId).containsExactly("b", "a", "c");
    assertThat(loads).extracting(TaskLoad::getOpen).containsExactly(2L, 1L, 0L);
  }

  @Test
  public void shouldTakeTheRowAnInsensitiveCollationMatchedUnderTheIdAskedFor() {
    List<TaskLoad> loads = TaskWorkloadQueryImpl.inOrder(Arrays.asList("accounting", "jose", "sales"),
        Arrays.asList(row("Accounting", 3, null), row("José", 2, null), row("sales  ", 1, null)));

    assertThat(loads).extracting(TaskLoad::getId).containsExactly("accounting", "jose", "sales");
    assertThat(loads).extracting(TaskLoad::getOpen).containsExactly(3L, 2L, 1L);
  }

  @Test
  public void shouldNotLendARowThatAnotherIdMatchedExactly() {
    // a case-sensitive database returns only the row of the id that matched; "SALES" has no tasks
    List<TaskLoad> loads = TaskWorkloadQueryImpl.inOrder(Arrays.asList("SALES", "Sales"),
        Arrays.asList(row("Sales", 4, null)));

    assertThat(loads).extracting(TaskLoad::getOpen).containsExactly(0L, 4L);
  }

  @Test
  public void shouldAddRowsThatShareTheirKey() {
    Date older = new Date(1_000);
    Date newer = new Date(2_000);
    List<TaskLoad> loads = TaskWorkloadQueryImpl.inOrder(Arrays.asList("team"),
        Arrays.asList(row("Team", 1, newer), row("TEAM", 2, older)));

    assertThat(loads.get(0).getId()).isEqualTo("team");
    assertThat(loads.get(0).getOpen()).isEqualTo(3);
    assertThat(loads.get(0).getUnassigned()).isEqualTo(3);
    assertThat(loads.get(0).getOldest()).isEqualTo(older);
  }
}
