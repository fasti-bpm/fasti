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
package org.camunda.bpm.engine.rest.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fasti.engine.analytics.ActivityAnalytics;
import dev.fasti.engine.analytics.AnalyticsNotAvailableException;
import dev.fasti.engine.analytics.InstanceAnalytics;
import dev.fasti.engine.analytics.PeriodAnalytics;
import dev.fasti.engine.analytics.ProcessAnalytics;
import dev.fasti.engine.analytics.ProcessAnalyticsQuery;
import dev.fasti.engine.analytics.SlowInstance;
import dev.fasti.engine.analytics.UserTaskAnalytics;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.camunda.bpm.engine.rest.AnalyticsRestService;
import org.camunda.bpm.engine.rest.dto.converter.DateConverter;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.exception.RestException;

public class AnalyticsRestServiceImpl extends AbstractRestProcessEngineAware implements AnalyticsRestService {

  static final int DEFAULT_MAX_RESULTS = 20;

  public AnalyticsRestServiceImpl(String engineName, ObjectMapper objectMapper) {
    super(engineName, objectMapper);
  }

  @Override
  public List<ActivityAnalytics> getActivities(String key, UriInfo uriInfo) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    return run(() -> query(key, parameters).activities());
  }

  @Override
  public List<SlowInstance> getActivityInstances(String key, String activityId, UriInfo uriInfo) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    int max = (int) number(parameters, "maxResults", DEFAULT_MAX_RESULTS, 1, ProcessAnalyticsQuery.MAX_RESULTS);
    boolean running = flag(parameters, "running");
    return run(() -> running ? query(key, parameters).oldestRunning(activityId, max)
        : query(key, parameters).slowest(activityId, max));
  }

  @Override
  public InstanceAnalytics getInstances(String key, UriInfo uriInfo) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    return run(() -> query(key, parameters).instances());
  }

  @Override
  public List<PeriodAnalytics> getTrend(String key, UriInfo uriInfo) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    List<Date> boundaries = new ArrayList<>();
    for (String value : list(parameters, "boundaries")) {
      boundaries.add(date("boundaries", value));
    }
    return run(() -> query(key, parameters).trend(boundaries));
  }

  @Override
  public UserTaskAnalytics getUserTasks(String key, UriInfo uriInfo) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    int max = (int) number(parameters, "maxTasks", ProcessAnalyticsQuery.DEFAULT_USER_TASKS, 1,
        ProcessAnalyticsQuery.MAX_USER_TASKS);
    return run(() -> query(key, parameters).userTasks(max));
  }

  protected <T> T run(Supplier<T> call) {
    try {
      return call.get();
    } catch (AnalyticsNotAvailableException e) {
      // a custom MyBatis configuration without the statements
      throw new RestException(Status.NOT_IMPLEMENTED, e, e.getMessage());
    } catch (NotFoundException e) {
      throw new InvalidRequestException(Status.NOT_FOUND, e, e.getMessage());
    }
  }

  protected ProcessAnalyticsQuery query(String key, MultivaluedMap<String, String> parameters) {
    ProcessAnalyticsQuery query = ProcessAnalytics.query(getProcessEngine()).processDefinitionKey(key);
    List<String> definitionIds = list(parameters, "processDefinitionIdIn");
    if (!definitionIds.isEmpty()) {
      query.processDefinitionIdIn(definitionIds);
    }
    List<String> tenantIds = list(parameters, "tenantIdIn");
    if (!tenantIds.isEmpty()) {
      query.tenantIdIn(tenantIds);
    }
    String after = parameters.getFirst("finishedAfter");
    if (after != null && !after.isEmpty()) {
      query.finishedAfter(date("finishedAfter", after));
    }
    String before = parameters.getFirst("finishedBefore");
    if (before != null && !before.isEmpty()) {
      query.finishedBefore(date("finishedBefore", before));
    }
    if (parameters.getFirst("targetDuration") != null && !parameters.getFirst("targetDuration").isEmpty()) {
      query.targetDuration(number(parameters, "targetDuration", 0, 0, Long.MAX_VALUE));
    }
    return query;
  }

  /** The values of a parameter given once with commas, or several times. */
  protected static List<String> list(MultivaluedMap<String, String> parameters, String name) {
    List<String> values = parameters.get(name);
    if (values == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String value : values) {
      for (String part : value.split(",")) {
        if (!part.trim().isEmpty()) {
          result.add(part.trim());
        }
      }
    }
    return result;
  }

  protected Date date(String name, String value) {
    DateConverter converter = new DateConverter();
    converter.setObjectMapper(getObjectMapper());
    try {
      return converter.convertQueryParameterToType(value);
    } catch (RuntimeException e) {
      throw new InvalidRequestException(Status.BAD_REQUEST, e, name + " is not a date: " + value);
    }
  }

  protected static long number(MultivaluedMap<String, String> parameters, String name, long defaultValue,
      long minimum, long maximum) {
    String value = parameters.getFirst(name);
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      long number = Long.parseLong(value.trim());
      if (number < minimum || number > maximum) {
        throw new NumberFormatException();
      }
      return number;
    } catch (NumberFormatException e) {
      throw new InvalidRequestException(Status.BAD_REQUEST,
          name + " must be a whole number from " + minimum + " to " + maximum);
    }
  }

  /** {@code true} or {@code false}; absent is false. */
  protected static boolean flag(MultivaluedMap<String, String> parameters, String name) {
    String value = parameters.getFirst(name);
    if (value == null || value.isEmpty() || "false".equalsIgnoreCase(value.trim())) {
      return false;
    }
    if ("true".equalsIgnoreCase(value.trim())) {
      return true;
    }
    throw new InvalidRequestException(Status.BAD_REQUEST, name + " must be true or false");
  }
}
