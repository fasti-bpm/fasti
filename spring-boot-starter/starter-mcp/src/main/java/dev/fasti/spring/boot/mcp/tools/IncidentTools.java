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
package dev.fasti.spring.boot.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.externaltask.ExternalTask;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.IncidentQuery;
import org.camunda.bpm.engine.runtime.Job;

import dev.fasti.spring.boot.mcp.tools.ToolSupport.Effect;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/** Incidents (failed jobs and external tasks) and retrying them. */
public class IncidentTools {

  protected static final int MAX_MESSAGE_LENGTH = 1000;
  protected static final int MAX_RETRIES = 10;

  protected final ToolSupport support;

  public IncidentTools(ToolSupport support) {
    this.support = support;
  }

  public List<SyncToolSpecification> readTools() {
    return List.of(listIncidents(), getIncident());
  }

  public List<SyncToolSpecification> writeTools() {
    return List.of(retryFailedJob());
  }

  protected RuntimeService runtime() {
    return support.engine().getRuntimeService();
  }

  protected ManagementService management() {
    return support.engine().getManagementService();
  }

  protected ExternalTaskService externalTasks() {
    return support.engine().getExternalTaskService();
  }

  protected SyncToolSpecification listIncidents() {
    return support.tool("list_incidents", "List incidents",
        "Lists open incidents the user may read, newest first: failed jobs (failedJob) and failed "
            + "external tasks (failedExternalTask) whose retries are used up, and others. Filter by "
            + "process instance, process definition key, incident type or activity. Use get_incident "
            + "for the stack trace and retry_failed_job to retry.",
        Schemas.object()
            .string("processInstanceId", "Only incidents of this process instance.")
            .string("processDefinitionKey", "Only incidents of this process definition key.")
            .string("incidentType", "Incident type, e.g. failedJob or failedExternalTask.")
            .string("activityId", "Only incidents at this activity.")
            .paging(support.defaultResults(), support.maxResults())
            .build(),
        Effect.READ, (arguments, identity) -> {
          IncidentQuery query = runtime().createIncidentQuery();
          String value = arguments.string("processInstanceId");
          if (value != null) {
            query.processInstanceId(value);
          }
          value = arguments.string("processDefinitionKey");
          if (value != null) {
            query.processDefinitionKeyIn(value);
          }
          value = arguments.string("incidentType");
          if (value != null) {
            query.incidentType(value);
          }
          value = arguments.string("activityId");
          if (value != null) {
            query.activityId(value);
          }
          query.orderByIncidentTimestamp().desc();
          int first = arguments.firstResult();
          List<Map<String, Object>> items = new ArrayList<>();
          for (Incident incident : query.listPage(first, arguments.maxResults(support.defaultResults(), support.maxResults()))) {
            items.add(incident(incident));
          }
          return Json.page(items, first, query.count());
        });
  }

  protected SyncToolSpecification getIncident() {
    return support.tool("get_incident", "Get an incident",
        "Returns one incident with the failed job (retries, due date, exception message and stack "
            + "trace) or the failed external task (topic, worker, error message and details), the "
            + "stack trace cut at the configured length.",
        Schemas.object().string("id", "Incident id.").required("id").build(),
        Effect.READ, (arguments, identity) -> {
          String id = arguments.requiredString("id");
          Incident incident = find(id);
          Json result = incident(incident);
          String configuration = incident.getConfiguration();
          int max = support.properties().getMaxStacktraceLength();
          if (Incident.FAILED_JOB_HANDLER_TYPE.equals(incident.getIncidentType()) && configuration != null) {
            Job job = management().createJobQuery().jobId(configuration).singleResult();
            if (job != null) {
              result.with("job", Json.obj()
                  .with("id", job.getId())
                  .with("retries", job.getRetries())
                  .with("dueDate", job.getDuedate())
                  .with("suspended", job.isSuspended())
                  .with("exceptionMessage", cut(job.getExceptionMessage(), MAX_MESSAGE_LENGTH))
                  .with("stacktrace", cut(management().getJobExceptionStacktrace(job.getId()), max)));
            }
          } else if (Incident.EXTERNAL_TASK_HANDLER_TYPE.equals(incident.getIncidentType()) && configuration != null) {
            ExternalTask task = externalTasks().createExternalTaskQuery().externalTaskId(configuration).singleResult();
            if (task != null) {
              result.with("externalTask", Json.obj()
                  .with("id", task.getId())
                  .with("topicName", task.getTopicName())
                  .with("workerId", task.getWorkerId())
                  .with("retries", task.getRetries())
                  .with("errorMessage", cut(task.getErrorMessage(), MAX_MESSAGE_LENGTH))
                  .with("errorDetails", cut(externalTasks().getExternalTaskErrorDetails(task.getId()), max)));
            }
          }
          return result;
        });
  }

  protected Incident find(String id) {
    Incident incident = runtime().createIncidentQuery().incidentId(id).singleResult();
    if (incident == null) {
      throw ToolException.notFound("Incident", id);
    }
    return incident;
  }

  public static Json incident(Incident incident) {
    return Json.obj()
        .with("id", incident.getId())
        .with("incidentType", incident.getIncidentType())
        .with("message", cut(incident.getIncidentMessage(), MAX_MESSAGE_LENGTH))
        .with("timestamp", incident.getIncidentTimestamp())
        .with("activityId", incident.getActivityId())
        .with("failedActivityId", incident.getFailedActivityId())
        .with("processInstanceId", incident.getProcessInstanceId())
        .with("processDefinitionId", incident.getProcessDefinitionId())
        .with("configuration", incident.getConfiguration())
        .with("causeIncidentId", incident.getCauseIncidentId())
        .with("rootCauseIncidentId", incident.getRootCauseIncidentId())
        .with("annotation", cut(incident.getAnnotation(), MAX_MESSAGE_LENGTH))
        .with("tenantId", incident.getTenantId());
  }

  protected static String cut(String text, int max) {
    if (text == null || text.length() <= max) {
      return text;
    }
    return text.substring(0, max) + "... (cut, " + text.length() + " characters)";
  }

  protected SyncToolSpecification retryFailedJob() {
    return support.tool("retry_failed_job", "Retry a failed job",
        "Gives a failed job or failed external task new retries, so that it is executed again; this "
            + "resolves its incident. Pass the incidentId (failedJob or failedExternalTask) or a jobId. "
            + "Needs RETRY_JOB or UPDATE on the process instance (or the matching permission on the definition).",
        Schemas.object()
            .string("incidentId", "Id of a failedJob or failedExternalTask incident.")
            .string("jobId", "Id of a job, used when no incidentId is given.")
            .integer("retries", "Number of retries to set, default 1.", 1, MAX_RETRIES)
            .build(),
        Effect.WRITE, (arguments, identity) -> {
          int retries = arguments.integer("retries", 1, 1, MAX_RETRIES);
          String incidentId = arguments.string("incidentId");
          String jobId = arguments.string("jobId");
          if (incidentId != null) {
            Incident incident = find(incidentId);
            String configuration = incident.getConfiguration();
            if (Incident.FAILED_JOB_HANDLER_TYPE.equals(incident.getIncidentType()) && configuration != null) {
              management().setJobRetries(configuration, retries);
              return Json.obj().with("jobId", configuration).with("retries", retries);
            }
            if (Incident.EXTERNAL_TASK_HANDLER_TYPE.equals(incident.getIncidentType()) && configuration != null) {
              externalTasks().setRetries(configuration, retries);
              return Json.obj().with("externalTaskId", configuration).with("retries", retries);
            }
            throw new ToolException("Incidents of type '" + incident.getIncidentType() + "' cannot be retried");
          }
          if (jobId != null) {
            management().setJobRetries(jobId, retries);
            return Json.obj().with("jobId", jobId).with("retries", retries);
          }
          throw new ToolException("Give incidentId or jobId");
        });
  }
}
