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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.camunda.bpm.engine.rest.dto.CountResultDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditEventDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditPurgeDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditPurgeResultDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditStatusDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditVerificationDto;

/**
 * Fasti: the audit trail. Query parameters of the event endpoints: {@code processInstanceId},
 * {@code businessKey}, {@code actor} (a user id, or {@code system} for the engine itself),
 * {@code type} (comma separated), {@code action}, {@code tenantId}, {@code processDefinitionKey},
 * {@code entityId}, {@code chainId}, {@code from}, {@code to} and {@code sortOrder} ({@code desc},
 * default, or {@code asc}).
 */
@Produces(MediaType.APPLICATION_JSON)
public interface AuditRestService {

  String PATH = "/audit";

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  AuditStatusDto getStatus();

  @GET
  @Path("/event")
  @Produces(MediaType.APPLICATION_JSON)
  List<AuditEventDto> getEvents(@Context UriInfo uriInfo, @QueryParam("firstResult") Integer firstResult,
      @QueryParam("maxResults") Integer maxResults);

  @GET
  @Path("/event/count")
  @Produces(MediaType.APPLICATION_JSON)
  CountResultDto getEventCount(@Context UriInfo uriInfo);

  /** The selected records as a file ({@code format} {@code csv} or {@code json}), oldest first; the export is recorded. */
  @GET
  @Path("/event/export")
  @Produces({"text/csv", MediaType.APPLICATION_JSON})
  Response exportEvents(@Context UriInfo uriInfo, @QueryParam("format") String format);

  @POST
  @Path("/verify")
  @Produces(MediaType.APPLICATION_JSON)
  AuditVerificationDto verify(@QueryParam("chainId") String chainId);

  @POST
  @Path("/purge")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  AuditPurgeResultDto purge(AuditPurgeDto purge);
}
