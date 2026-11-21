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

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.ProcessEngineException;

import dev.fasti.spring.boot.mcp.auth.McpIdentity;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

/**
 * The XML of deployed models as resources: {@code fasti://process-definitions/{id}/bpmn} and
 * {@code fasti://decision-definitions/{id}/dmn}, read as the authenticated user (READ on the
 * definition) and cut at the configured length.
 */
public class ModelResources {

  public static final String BPMN_PREFIX = "fasti://process-definitions/";
  public static final String BPMN_SUFFIX = "/bpmn";
  public static final String DMN_PREFIX = "fasti://decision-definitions/";
  public static final String DMN_SUFFIX = "/dmn";

  protected final ToolSupport support;

  public ModelResources(ToolSupport support) {
    this.support = support;
  }

  public List<SyncResourceTemplateSpecification> templates() {
    return List.of(
        template(BPMN_PREFIX + "{processDefinitionId}" + BPMN_SUFFIX, "process-definition-bpmn",
            "BPMN XML of a process definition", "The BPMN 2.0 XML of a deployed process definition (id as returned by list_process_definitions).",
            BPMN_PREFIX, BPMN_SUFFIX, id -> support.engine().getRepositoryService().getProcessModel(id)),
        template(DMN_PREFIX + "{decisionDefinitionId}" + DMN_SUFFIX, "decision-definition-dmn",
            "DMN XML of a decision definition", "The DMN 1.3 XML of a deployed decision definition (id as returned by list_decision_definitions).",
            DMN_PREFIX, DMN_SUFFIX, id -> support.engine().getRepositoryService().getDecisionModel(id)));
  }

  protected SyncResourceTemplateSpecification template(String uriTemplate, String name, String title, String description,
                                                       String prefix, String suffix,
                                                       Function<String, InputStream> model) {
    ResourceTemplate template = ResourceTemplate.builder(uriTemplate, name)
        .title(title)
        .description(description)
        .mimeType("application/xml")
        .build();
    return new SyncResourceTemplateSpecification(template, (context, request) -> {
      String uri = request.uri();
      if (!uri.startsWith(prefix) || !uri.endsWith(suffix) || uri.length() <= prefix.length() + suffix.length()) {
        throw McpError.builder(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND).message("Resource not found").build();
      }
      String id = URLDecoder.decode(uri.substring(prefix.length(), uri.length() - suffix.length()), StandardCharsets.UTF_8);
      McpIdentity identity = ToolSupport.identity(context);
      try {
        Json xml = support.runAs(identity, () -> support.model(() -> model.apply(id)));
        Map<String, Object> meta = Boolean.TRUE.equals(xml.get("truncated")) ? Map.of("truncated", true) : null;
        return new ReadResourceResult(List.of(new TextResourceContents(uri, "application/xml", (String) xml.get("xml"))),
            meta);
      } catch (AuthorizationException e) {
        throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST).message("Not authorized: " + e.getMessage()).build();
      } catch (ToolException | ProcessEngineException e) {
        throw McpError.builder(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND).message("Resource not found").build();
      }
    });
  }
}
