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

import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.dmn.DecisionsEvaluationBuilder;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionDefinitionQuery;

import dev.fasti.spring.boot.mcp.tools.ToolSupport.Effect;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/**
 * DMN decisions: listing them and evaluating one with given inputs.
 * <p>
 * Evaluating is offered without the write switch: it changes no process, task or business data.
 * The inputs are plain values, never expressions; only the expressions of the deployed decision
 * run. The engine records the evaluation in the history (and the audit trail) and requires
 * CREATE_INSTANCE on the decision definition, so it stays a deliberate permission.
 */
public class DecisionTools {

  protected final ToolSupport support;

  public DecisionTools(ToolSupport support) {
    this.support = support;
  }

  public List<SyncToolSpecification> readTools() {
    return List.of(listDecisionDefinitions(), evaluateDecision());
  }

  protected RepositoryService repository() {
    return support.engine().getRepositoryService();
  }

  protected SyncToolSpecification listDecisionDefinitions() {
    return support.tool("list_decision_definitions", "List decision definitions",
        "Lists the deployed DMN decision definitions the user may read, by default only the latest "
            + "version of each key. With includeXml=true and a key, also the DMN XML of each decision "
            + "(cut at the configured length), which shows its inputs, outputs and rules.",
        Schemas.object()
            .string("key", "Exact decision definition key.")
            .string("nameLike", "Part of the name.")
            .bool("latestVersionOnly", "Only the latest version of each key (default true).")
            .bool("includeXml", "Include the DMN XML; only together with key (default false).")
            .paging(support.defaultResults(), support.maxResults())
            .build(),
        Effect.READ, (arguments, identity) -> {
          DecisionDefinitionQuery query = repository().createDecisionDefinitionQuery();
          String key = arguments.string("key");
          if (key != null) {
            query.decisionDefinitionKey(key);
          }
          String nameLike = arguments.string("nameLike");
          if (nameLike != null) {
            query.decisionDefinitionNameLike(ToolSupport.like(nameLike));
          }
          if (arguments.bool("latestVersionOnly", true)) {
            query.latestVersion();
          }
          boolean includeXml = arguments.bool("includeXml", false);
          if (includeXml && key == null) {
            throw new ToolException("includeXml needs a key");
          }
          query.orderByDecisionDefinitionKey().asc().orderByDecisionDefinitionVersion().desc();
          int first = arguments.firstResult();
          List<Map<String, Object>> items = new ArrayList<>();
          for (DecisionDefinition definition : query.listPage(first, arguments.maxResults(support.defaultResults(), support.maxResults()))) {
            Json item = Json.obj()
                .with("id", definition.getId())
                .with("key", definition.getKey())
                .with("name", definition.getName())
                .with("version", definition.getVersion())
                .with("versionTag", definition.getVersionTag())
                .with("decisionRequirementsDefinitionKey", definition.getDecisionRequirementsDefinitionKey())
                .with("deploymentId", definition.getDeploymentId())
                .with("tenantId", definition.getTenantId());
            if (includeXml) {
              item.with("dmn", support.model(() -> repository().getDecisionModel(definition.getId())));
            }
            items.add(item);
          }
          return Json.page(items, first, query.count());
        });
  }

  protected SyncToolSpecification evaluateDecision() {
    return support.tool("evaluate_decision", "Evaluate a DMN decision",
        "Evaluates a deployed DMN decision (by id, or the latest or given version of a key) with the "
            + "given input variables (strings, numbers, booleans or null) and returns the matched "
            + "results. Changes no process data; the engine records the evaluation in the history and "
            + "requires CREATE_INSTANCE on the decision definition.",
        Schemas.object()
            .string("id", "Decision definition id.")
            .string("key", "Decision definition key, used when no id is given.")
            .integer("version", "Version of the key; default the latest.", 1, Integer.MAX_VALUE)
            .string("tenantId", "Tenant of the key.")
            .variables("variables", "Input variables, e.g. {\"amount\": 1200, \"category\": \"Travel\"}.",
                support.properties().getMaxInputVariables())
            .build(),
        Effect.COMPUTE, (arguments, identity) -> {
          String id = arguments.string("id");
          String key = arguments.string("key");
          DecisionsEvaluationBuilder builder;
          if (id != null) {
            builder = support.engine().getDecisionService().evaluateDecisionById(id);
          } else if (key != null) {
            builder = support.engine().getDecisionService().evaluateDecisionByKey(key);
            Integer version = arguments.integer("version", null, 1, Integer.MAX_VALUE);
            if (version != null) {
              builder.version(version);
            }
            String tenantId = arguments.string("tenantId");
            if (tenantId != null) {
              builder.decisionDefinitionTenantId(tenantId);
            }
          } else {
            throw new ToolException("Give the id or the key of the decision definition");
          }
          builder.variables(support.values().toVariables(arguments.object("variables")));
          DmnDecisionResult result = builder.evaluate();
          List<Map<String, Object>> results = new ArrayList<>();
          for (DmnDecisionResultEntries entries : ToolSupport.limit(result, support.maxResults())) {
            results.add(support.values().namedValues(entries.getEntryMapTyped()));
          }
          return Json.obj()
              .with("results", results)
              .with("count", result.size())
              .with("truncated", result.size() > results.size() ? Boolean.TRUE : null);
        });
  }
}
