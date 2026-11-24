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
package org.camunda.bpm.engine.rest;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import dev.fasti.engine.analytics.ActivityAnalytics;
import dev.fasti.engine.analytics.InstanceAnalytics;
import dev.fasti.engine.analytics.PeriodAnalytics;
import dev.fasti.engine.analytics.SlowInstance;
import dev.fasti.engine.analytics.UserTaskAnalytics;

/**
 * Fasti: process analytics computed over the history (heatmap, bottlenecks, durations, user
 * tasks). Every call takes {@code processDefinitionIdIn}, {@code tenantIdIn} (comma-separated),
 * {@code finishedAfter}, {@code finishedBefore} (dates) and {@code targetDuration} (milliseconds).
 */
@Produces(MediaType.APPLICATION_JSON)
public interface AnalyticsRestService {

  String PATH = "/analytics";

  @GET
  @Path("/process-definition/key/{key}/activities")
  @Produces(MediaType.APPLICATION_JSON)
  List<ActivityAnalytics> getActivities(@PathParam("key") String key, @Context UriInfo uriInfo);

  /** The slowest finished runs of an activity, or with {@code running=true} the oldest running ones. */
  @GET
  @Path("/process-definition/key/{key}/activities/{activityId}/instances")
  @Produces(MediaType.APPLICATION_JSON)
  List<SlowInstance> getActivityInstances(@PathParam("key") String key, @PathParam("activityId") String activityId,
      @Context UriInfo uriInfo);

  @GET
  @Path("/process-definition/key/{key}/instances")
  @Produces(MediaType.APPLICATION_JSON)
  InstanceAnalytics getInstances(@PathParam("key") String key, @Context UriInfo uriInfo);

  /** Periods between the dates of {@code boundaries} (comma-separated, ascending, 2 to 63). */
  @GET
  @Path("/process-definition/key/{key}/trend")
  @Produces(MediaType.APPLICATION_JSON)
  List<PeriodAnalytics> getTrend(@PathParam("key") String key, @Context UriInfo uriInfo);

  /** Waiting and working time of the completed user tasks, the newest {@code maxTasks} at most. */
  @GET
  @Path("/process-definition/key/{key}/user-tasks")
  @Produces(MediaType.APPLICATION_JSON)
  UserTaskAnalytics getUserTasks(@PathParam("key") String key, @Context UriInfo uriInfo);
}
