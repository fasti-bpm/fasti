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
package org.camunda.bpm.engine.rest.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import dev.fasti.engine.workload.TaskWorkloadQuery;
import dev.fasti.engine.workload.TaskWorkloads;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.rest.TaskRestService;
import org.camunda.bpm.engine.rest.dto.CountResultDto;
import org.camunda.bpm.engine.rest.dto.task.TaskDto;
import org.camunda.bpm.engine.rest.dto.task.TaskQueryDto;
import org.camunda.bpm.engine.rest.dto.task.TaskWithAttachmentAndCommentDto;
import org.camunda.bpm.engine.rest.dto.task.TaskWithVariablesDto;
import org.camunda.bpm.engine.rest.dto.task.TaskWorkloadDto;
import org.camunda.bpm.engine.rest.dto.task.TaskWorkloadQueryDto;
import org.camunda.bpm.engine.rest.dto.converter.DateConverter;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.hal.Hal;
import org.camunda.bpm.engine.rest.hal.task.HalTaskList;
import org.camunda.bpm.engine.rest.sub.task.TaskReportResource;
import org.camunda.bpm.engine.rest.sub.task.TaskResource;
import org.camunda.bpm.engine.rest.sub.task.impl.TaskReportResourceImpl;
import org.camunda.bpm.engine.rest.sub.task.impl.TaskResourceImpl;
import org.camunda.bpm.engine.rest.util.QueryUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.variable.VariableMap;

public class TaskRestServiceImpl extends AbstractRestProcessEngineAware implements TaskRestService {

  public static final List<Variant> VARIANTS = Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, Hal.APPLICATION_HAL_JSON_TYPE).add().build();

  public TaskRestServiceImpl(String engineName, final ObjectMapper objectMapper) {
    super(engineName, objectMapper);
  }

  @Override
  public Object getTasks(Request request, UriInfo uriInfo, Integer firstResult, Integer maxResults) {
    Variant variant = request.selectVariant(VARIANTS);
    if (variant != null) {
      if (MediaType.APPLICATION_JSON_TYPE.equals(variant.getMediaType())) {
        return getJsonTasks(uriInfo, firstResult, maxResults);
      }
      else if (Hal.APPLICATION_HAL_JSON_TYPE.equals(variant.getMediaType())) {
        return getHalTasks(uriInfo, firstResult, maxResults);
      }
    }
    throw new InvalidRequestException(Response.Status.NOT_ACCEPTABLE, "No acceptable content-type found");
  }

  public List<TaskDto> getJsonTasks(UriInfo uriInfo, Integer firstResult, Integer maxResults) {
    // get list of tasks
    TaskQueryDto queryDto = new TaskQueryDto(getObjectMapper(), uriInfo.getQueryParameters());
    return queryTasks(queryDto, firstResult, maxResults);
  }

  public HalTaskList getHalTasks(UriInfo uriInfo, Integer firstResult, Integer maxResults) {
    TaskQueryDto queryDto = new TaskQueryDto(getObjectMapper(), uriInfo.getQueryParameters());

    ProcessEngine engine = getProcessEngine();
    TaskQuery query = queryDto.toQuery(engine);

    // get list of tasks
    List<Task> matchingTasks = executeTaskQuery(firstResult, maxResults, query);

    // get total count
    long count = query.count();

    return HalTaskList.generate(matchingTasks, count, engine);
  }

  @Override
  public List<TaskDto> queryTasks(TaskQueryDto queryDto, Integer firstResult,
      Integer maxResults) {
    ProcessEngine engine = getProcessEngine();
    queryDto.setObjectMapper(getObjectMapper());
    TaskQuery query = queryDto.toQuery(engine);

    List<Task> matchingTasks = executeTaskQuery(firstResult, maxResults, query);
    List<TaskDto> tasks = new ArrayList<TaskDto>();

    boolean withTaskVariables = Boolean.TRUE.equals(queryDto.getWithTaskVariablesInReturn());
    boolean withTaskLocalVariables = Boolean.TRUE.equals(queryDto.getWithTaskLocalVariablesInReturn());
    boolean withCommentInfo = Boolean.TRUE.equals(queryDto.getWithCommentAttachmentInfo());

    if (withTaskVariables || withTaskLocalVariables) {
      return getVariablesForTasks(engine, matchingTasks, withTaskVariables, withCommentInfo);
    }
    if (withCommentInfo) {
      tasks = matchingTasks.stream().map(TaskWithAttachmentAndCommentDto::fromEntity).collect(Collectors.toList());
    }
    else {
      tasks = matchingTasks.stream().map(TaskDto::fromEntity).collect(Collectors.toList());
    }
    return tasks;
  }

  protected List<Task> executeTaskQuery(Integer firstResult, Integer maxResults, TaskQuery query) {

    // enable initialization of form key:
    query.initializeFormKeys();
    return QueryUtil.list(query, firstResult, maxResults);
  }

  @Override
  public CountResultDto getTasksCount(UriInfo uriInfo) {
    TaskQueryDto queryDto = new TaskQueryDto(getObjectMapper(), uriInfo.getQueryParameters());
    return queryTasksCount(queryDto);
  }

  @Override
  public CountResultDto queryTasksCount(TaskQueryDto queryDto) {
    ProcessEngine engine = getProcessEngine();
    queryDto.setObjectMapper(getObjectMapper());
    TaskQuery query = queryDto.toQuery(engine);

    long count = query.count();
    CountResultDto result = new CountResultDto();
    result.setCount(count);

    return result;
  }

  @Override
  public TaskResource getTask(String id,
                              boolean withCommentAttachmentInfo,
                              boolean withTaskVariablesInReturn,
                              boolean withTaskLocalVariablesInReturn) {
    return new TaskResourceImpl(getProcessEngine(), id, relativeRootResourcePath, getObjectMapper(),
        withCommentAttachmentInfo, withTaskVariablesInReturn, withTaskLocalVariablesInReturn);
  }

  @Override
  public void createTask(TaskDto taskDto) {
    ProcessEngine engine = getProcessEngine();
    TaskService taskService = engine.getTaskService();

    Task newTask = taskService.newTask(taskDto.getId());
    taskDto.updateTask(newTask);

    try {
      taskService.saveTask(newTask);

    } catch (NotValidException e) {
      throw new InvalidRequestException(Status.BAD_REQUEST, e, "Could not save task: " + e.getMessage());
    }

  }

  @Override
  public TaskWorkloadDto getWorkload(UriInfo uriInfo) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    TaskWorkloadQueryDto queryDto = new TaskWorkloadQueryDto();
    queryDto.setGroupIdIn(listParameter(parameters, "groupIdIn"));
    queryDto.setUserIdIn(listParameter(parameters, "userIdIn"));
    queryDto.setTenantIdIn(listParameter(parameters, "tenantIdIn"));
    String overdueBefore = parameters.getFirst("overdueBefore");
    if (overdueBefore != null && !overdueBefore.isEmpty()) {
      DateConverter converter = new DateConverter();
      converter.setObjectMapper(getObjectMapper());
      try {
        queryDto.setOverdueBefore(converter.convertQueryParameterToType(overdueBefore));
      } catch (RuntimeException e) {
        throw new InvalidRequestException(Status.BAD_REQUEST, e, "Cannot read overdueBefore: " + overdueBefore);
      }
    }
    return queryWorkload(queryDto);
  }

  @Override
  public TaskWorkloadDto queryWorkload(TaskWorkloadQueryDto queryDto) {
    if (queryDto == null) {
      queryDto = new TaskWorkloadQueryDto();
    }
    TaskWorkloadQuery query = TaskWorkloads.query(getProcessEngine());
    try {
      query.groupIdIn(queryDto.getGroupIdIn()).userIdIn(queryDto.getUserIdIn()).overdueBefore(queryDto.getOverdueBefore());
      List<String> tenantIds = queryDto.getTenantIdIn();
      if (tenantIds != null && !tenantIds.isEmpty()) {
        query.tenantIdIn(tenantIds.toArray(new String[0]));
      }
    } catch (BadUserRequestException e) {
      throw new InvalidRequestException(Status.BAD_REQUEST, e, e.getMessage());
    }
    return TaskWorkloadDto.fromWorkload(query.read());
  }

  /** The values of a comma separated parameter; {@code null} if it is not given. */
  private static List<String> listParameter(MultivaluedMap<String, String> parameters, String name) {
    List<String> values = parameters.get(name);
    if (values == null) {
      return null;
    }
    List<String> result = new ArrayList<>();
    for (String value : values) {
      for (String part : value.split(",")) {
        if (!part.isEmpty()) {
          result.add(part);
        }
      }
    }
    return result;
  }

  @Override
  public TaskReportResource getTaskReportResource() {
    return new TaskReportResourceImpl(getProcessEngine());
  }

  private List<TaskDto> getVariablesForTasks(ProcessEngine engine,
                                             List<Task> matchingTasks,
                                             boolean withTaskVariablesInReturn,
                                             boolean withCommentAndAttachments) {
    TaskService taskService = engine.getTaskService();
    List<TaskDto> tasks = new ArrayList<TaskDto>();
    for (Task task : matchingTasks) {
      VariableMap taskVariables;
      if (withTaskVariablesInReturn) {
        taskVariables = taskService.getVariablesTyped(task.getId(), true);
      } else {
        taskVariables = taskService.getVariablesLocalTyped(task.getId(), true);
      }
      Map<String, VariableValueDto> taskVariablesDto = VariableValueDto.fromMap(taskVariables);
      if (withCommentAndAttachments) {
        tasks.add(TaskWithAttachmentAndCommentDto.fromEntity(task, taskVariablesDto));
      } else {
        tasks.add(TaskWithVariablesDto.fromEntity(task, taskVariablesDto));
      }
    }
    return tasks;
  }
}
