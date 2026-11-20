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

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditEventQuery;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTrail;
import org.camunda.bpm.engine.rest.AuditRestService;
import org.camunda.bpm.engine.rest.dto.CountResultDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditEventDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditPurgeDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditPurgeResultDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditStatusDto;
import org.camunda.bpm.engine.rest.dto.audit.AuditVerificationDto;
import org.camunda.bpm.engine.rest.dto.converter.DateConverter;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;

public class AuditRestServiceImpl extends AbstractRestProcessEngineAware implements AuditRestService {

  static final int DEFAULT_MAX_RESULTS = 50;
  static final int LIMIT_MAX_RESULTS = 1000;
  static final int EXPORT_PAGE = 1000;
  /** Exports beyond this need a narrower filter. */
  static final int LIMIT_EXPORT = 100_000;
  static final String ACTOR_SYSTEM = "system";
  /**
   * Starts of text a spreadsheet may run as a formula or a DDE call: also after leading white space,
   * and in the full-width forms some spreadsheets accept.
   */
  static final Pattern FORMULA_START = Pattern.compile("^[\\t\\r\\n]|^[\\s\\u3000]*[=+\\-@;\\uFF1D\\uFF0B\\uFF0D\\uFF20]");

  static final String[] CSV_COLUMNS = {"id", "chainId", "sequence", "timestamp", "time", "type", "action", "actor",
      "engineName", "tenantId", "processDefinitionKey", "processDefinitionId", "processInstanceId", "businessKey",
      "entityType", "entityId", "entityName", "details", "previousHash", "hash"};

  public AuditRestServiceImpl(String engineName, ObjectMapper objectMapper) {
    super(engineName, objectMapper);
  }

  protected AuditService audit() {
    return AuditTrail.of(getProcessEngine());
  }

  @Override
  public AuditStatusDto getStatus() {
    return AuditStatusDto.fromService(audit());
  }

  @Override
  public List<AuditEventDto> getEvents(UriInfo uriInfo, Integer firstResult, Integer maxResults) {
    int first = firstResult == null ? 0 : firstResult;
    int max = maxResults == null ? DEFAULT_MAX_RESULTS : maxResults;
    if (first < 0 || max < 0 || max > LIMIT_MAX_RESULTS) {
      throw new InvalidRequestException(Status.BAD_REQUEST,
          "firstResult must not be negative and maxResults must be between 0 and " + LIMIT_MAX_RESULTS + ".");
    }
    List<AuditEventDto> result = new ArrayList<>();
    for (AuditEvent event : query(uriInfo.getQueryParameters()).listPage(first, max)) {
      result.add(AuditEventDto.fromEvent(event));
    }
    return result;
  }

  @Override
  public CountResultDto getEventCount(UriInfo uriInfo) {
    return new CountResultDto(query(uriInfo.getQueryParameters()).count());
  }

  /** The query the parameters describe. */
  protected AuditEventQuery query(MultivaluedMap<String, String> parameters) {
    AuditEventQuery query = audit().createQuery();
    query.processInstanceId(parameters.getFirst("processInstanceId"));
    query.businessKey(parameters.getFirst("businessKey"));
    String actor = parameters.getFirst("actor");
    if (ACTOR_SYSTEM.equals(actor)) {
      query.systemActor();
    } else {
      query.actor(actor);
    }
    String type = parameters.getFirst("type");
    if (type != null) {
      query.types(type.split(","));
    }
    query.action(parameters.getFirst("action"));
    query.tenantId(parameters.getFirst("tenantId"));
    query.processDefinitionKey(parameters.getFirst("processDefinitionKey"));
    query.entityId(parameters.getFirst("entityId"));
    query.chainId(parameters.getFirst("chainId"));
    query.from(date(parameters.getFirst("from"), "from"));
    query.to(date(parameters.getFirst("to"), "to"));
    String sortOrder = parameters.getFirst("sortOrder");
    if ("asc".equals(sortOrder)) {
      query.oldestFirst();
    } else if (sortOrder != null && !"desc".equals(sortOrder)) {
      throw new InvalidRequestException(Status.BAD_REQUEST, "sortOrder must be asc or desc.");
    }
    return query;
  }

  protected Date date(String value, String name) {
    if (value == null || value.isBlank()) {
      return null;
    }
    DateConverter converter = new DateConverter();
    converter.setObjectMapper(getObjectMapper());
    try {
      return converter.convertQueryParameterToType(value);
    } catch (RuntimeException e) {
      throw new InvalidRequestException(Status.BAD_REQUEST, e, "Cannot read " + name + ": " + value);
    }
  }

  @Override
  public Response exportEvents(UriInfo uriInfo, String format) {
    String kind = format == null ? "csv" : format.toLowerCase(Locale.ROOT);
    if (!kind.equals("csv") && !kind.equals("json")) {
      throw new InvalidRequestException(Status.BAD_REQUEST, "format must be csv or json.");
    }
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();
    AuditEventQuery counting = query(parameters);
    long count = counting.count();
    if (count > LIMIT_EXPORT) {
      throw new InvalidRequestException(Status.BAD_REQUEST, "The filter selects " + count + " records; an export holds at most "
          + LIMIT_EXPORT + ". Narrow the filter, for example by time.");
    }
    AuditService audit = audit();
    audit.recordExport(kind, describe(parameters), count);

    AuditEventQuery query = query(parameters).oldestFirst();
    String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
    StreamingOutput body = output -> {
      Writer writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
      if (kind.equals("csv")) {
        writer.write('﻿');
        writer.write(String.join(",", CSV_COLUMNS));
        writer.write("\r\n");
        for (int first = 0; first < count; first += EXPORT_PAGE) {
          for (AuditEvent event : query.listPage(first, EXPORT_PAGE)) {
            writer.write(csvRow(event));
          }
        }
        writer.flush();
      } else {
        JsonGenerator json = getObjectMapper().getFactory().createGenerator(writer);
        json.writeStartArray();
        for (int first = 0; first < count; first += EXPORT_PAGE) {
          for (AuditEvent event : query.listPage(first, EXPORT_PAGE)) {
            getObjectMapper().writeValue(json, AuditEventDto.fromEvent(event));
          }
        }
        json.writeEndArray();
        json.flush();
      }
    };
    return Response.ok(body, kind.equals("csv") ? "text/csv; charset=UTF-8" : MediaType.APPLICATION_JSON)
        .header("Content-Disposition", "attachment; filename=\"audit-trail-" + stamp + "." + kind + "\"")
        .build();
  }

  static String describe(MultivaluedMap<String, String> parameters) {
    List<String> parts = new ArrayList<>();
    for (String name : List.of("processInstanceId", "businessKey", "actor", "type", "action", "tenantId",
        "processDefinitionKey", "entityId", "chainId", "from", "to")) {
      String value = parameters.getFirst(name);
      if (value != null && !value.isBlank()) {
        parts.add(name + "=" + value);
      }
    }
    return parts.isEmpty() ? "all" : String.join(", ", parts);
  }

  static String csvRow(AuditEvent event) {
    String[] values = {event.getId(), event.getChainId(), Long.toString(event.getSequence()),
        event.getTimestamp().toInstant().toString(), Long.toString(event.getTimestamp().getTime()), event.getType(),
        event.getAction(), event.getActor(), event.getEngineName(), event.getTenantId(), event.getProcessDefinitionKey(),
        event.getProcessDefinitionId(), event.getProcessInstanceId(), event.getBusinessKey(), event.getEntityType(),
        event.getEntityId(), event.getEntityName(), event.getDetails(), event.getPreviousHash(), event.getHash()};
    StringBuilder row = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        row.append(',');
      }
      row.append(csvCell(values[i]));
    }
    return row.append("\r\n").toString();
  }

  /**
   * A CSV cell, quoted when needed. Text that a spreadsheet would run as a formula gets a leading
   * apostrophe; the JSON export keeps every value exactly as hashed.
   */
  static String csvCell(String value) {
    if (value == null) {
      return "";
    }
    String text = value;
    if (FORMULA_START.matcher(text).find()) {
      text = "'" + text;
    }
    if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
        || !text.equals(value)) {
      return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    return text;
  }

  @Override
  public AuditVerificationDto verify(String chainId) {
    return AuditVerificationDto.fromVerification(chainId == null || chainId.isBlank() ? audit().verify() : audit().verify(chainId));
  }

  @Override
  public AuditPurgeResultDto purge(AuditPurgeDto purge) {
    if (purge == null || purge.getBefore() == null) {
      throw new InvalidRequestException(Status.BAD_REQUEST, "A date (before) is required: records before it are purged.");
    }
    if (!purge.isExported()) {
      throw new InvalidRequestException(Status.BAD_REQUEST,
          "Export the records first and confirm it with \"exported\": true; a purge cannot be undone.");
    }
    return AuditPurgeResultDto.fromResult(audit().purge(purge.getBefore()));
  }
}
