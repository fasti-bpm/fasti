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
package dev.fasti.engine.workload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * On a database that compares text without regard to case (H2 with {@code IGNORECASE}, like the
 * default collations of SQL Server, MySQL and MariaDB) a task query for {@code sales} also selects
 * the tasks offered to {@code Sales}; the workload counts them under the id that was asked for.
 */
public class TaskWorkloadCollationTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-workload-ignorecase;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1");
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("workload-ignorecase");
  });

  @Rule
  public ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);

  protected List<String> taskIds = new ArrayList<>();

  @After
  public void tearDown() {
    for (String taskId : taskIds) {
      engineRule.getTaskService().deleteTask(taskId, true);
    }
  }

  String task(String assignee, String... groups) {
    TaskService taskService = engineRule.getTaskService();
    Task task = taskService.newTask();
    taskService.saveTask(task);
    taskIds.add(task.getId());
    for (String group : groups) {
      taskService.addCandidateGroup(task.getId(), group);
    }
    if (assignee != null) {
      taskService.setAssignee(task.getId(), assignee);
    }
    return task.getId();
  }

  @Test
  public void shouldCountUnderTheIdAskedForWhenTheDatabaseIgnoresCase() {
    task("Alice", "Sales");
    task(null, "Sales", "support");
    task("bob", "SUPPORT");
    TaskService taskService = engineRule.getTaskService();
    // the premise: this database compares without regard to case
    assertThat(taskService.createTaskQuery().taskCandidateGroup("sales").includeAssignedTasks().count()).isEqualTo(2);

    TaskWorkload workload = TaskWorkloads.query(engineRule.getProcessEngine())
        .groupIdIn(Arrays.asList("sales", "Support", "idle"))
        .userIdIn(Arrays.asList("alice", "BOB"))
        .read();

    assertThat(workload.getGroups()).extracting(TaskLoad::getId).containsExactly("sales", "Support", "idle");
    assertThat(workload.getGroups()).extracting(TaskLoad::getOpen).containsExactly(
        taskService.createTaskQuery().taskCandidateGroup("sales").includeAssignedTasks().count(),
        taskService.createTaskQuery().taskCandidateGroup("Support").includeAssignedTasks().count(),
        0L);
    assertThat(workload.getGroups().get(0).getOpen()).isEqualTo(2);
    assertThat(workload.getGroups().get(0).getUnassigned()).isEqualTo(1);
    assertThat(workload.getGroups().get(1).getOpen()).isEqualTo(2);
    assertThat(workload.getGroups().get(0).getOldest()).isNotNull();
    assertThat(workload.getUsers()).extracting(TaskLoad::getId).containsExactly("alice", "BOB");
    assertThat(workload.getUsers()).extracting(TaskLoad::getOpen).containsExactly(
        taskService.createTaskQuery().taskAssignee("alice").count(),
        taskService.createTaskQuery().taskAssignee("BOB").count());
    assertThat(workload.getUsers().get(0).getOpen()).isEqualTo(1);
    assertThat(workload.getUsers().get(1).getOpen()).isEqualTo(1);
  }
}
