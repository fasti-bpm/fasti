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
package org.camunda.bpm.engine.rest.sub.identity;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.camunda.bpm.engine.rest.dto.identity.AbsenceDto;
import org.camunda.bpm.engine.rest.dto.identity.AbsenceHandoverDto;

/**
 * The absence of a user and their substitute ({@code /user/{id}/absence}). A user manages their
 * own absence; the absence of others needs {@code READ} or {@code UPDATE} on the user.
 */
public interface UserAbsenceResource {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  AbsenceDto getAbsence();

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  AbsenceDto setAbsence(AbsenceDto absence);

  @DELETE
  void clearAbsence();

  /** Hands the open tasks of the user over to the substitute now. */
  @POST
  @Path("/handover")
  @Produces(MediaType.APPLICATION_JSON)
  AbsenceHandoverDto handOverTasks(@QueryParam("maxTasks") Integer maxTasks);

  /** The absences that are active now and whose tasks go to this user. */
  @GET
  @Path("/substituting")
  @Produces(MediaType.APPLICATION_JSON)
  List<AbsenceDto> getSubstitutedAbsences();
}
