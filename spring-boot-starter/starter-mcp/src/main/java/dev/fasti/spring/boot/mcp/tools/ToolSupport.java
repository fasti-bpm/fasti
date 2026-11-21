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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.SuspendedEntityInteractionException;
import org.camunda.bpm.engine.TaskAlreadyClaimedException;
import org.camunda.bpm.engine.exception.NullValueException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.fasti.engine.foureyes.FourEyesViolationException;
import dev.fasti.spring.boot.mcp.FastiMcpProperties;
import dev.fasti.spring.boot.mcp.auth.McpIdentity;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * What every tool shares: running as the authenticated user, turning engine exceptions into tool
 * errors and results into JSON.
 */
public class ToolSupport {

  protected static final Logger LOG = LoggerFactory.getLogger("dev.fasti.spring.boot.mcp");
  protected static final int MAX_ERROR_LENGTH = 2000;

  /** Body of a tool: computes the result object (a map) as the authenticated user. */
  @FunctionalInterface
  public interface ToolBody {
    Map<String, Object> call(ToolArguments arguments, McpIdentity identity);
  }

  /** How a tool affects the engine, for the annotations clients use to ask for confirmation. */
  public enum Effect {
    /** Reads only. */
    READ,
    /** Records something (history) without changing business data. */
    COMPUTE,
    /** Changes data. */
    WRITE
  }

  protected final ProcessEngine engine;
  protected final FastiMcpProperties properties;
  protected final McpJsonMapper jsonMapper;
  protected final Values values;

  public ToolSupport(ProcessEngine engine, FastiMcpProperties properties, McpJsonMapper jsonMapper) {
    this.engine = engine;
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    this.values = new Values(properties);
  }

  public ProcessEngine engine() {
    return engine;
  }

  public FastiMcpProperties properties() {
    return properties;
  }

  public Values values() {
    return values;
  }

  public int defaultResults() {
    return Math.min(properties.getDefaultResults(), properties.getMaxResults());
  }

  public int maxResults() {
    return properties.getMaxResults();
  }

  public SyncToolSpecification tool(String name, String title, String description, Map<String, Object> inputSchema,
                                    Effect effect, ToolBody body) {
    ToolAnnotations annotations = ToolAnnotations.builder()
        .title(title)
        .readOnlyHint(effect == Effect.READ)
        .destructiveHint(false)
        .idempotentHint(effect != Effect.WRITE)
        .openWorldHint(false)
        .build();
    Tool tool = Tool.builder()
        .name(name)
        .title(title)
        .description(description)
        .inputSchema(inputSchema)
        .annotations(annotations)
        .build();
    return SyncToolSpecification.builder()
        .tool(tool)
        .callHandler((context, request) -> call(name, effect, context, request.arguments(), body))
        .build();
  }

  protected CallToolResult call(String name, Effect effect, McpTransportContext context, Map<String, Object> arguments,
                                ToolBody body) {
    McpIdentity identity = identity(context);
    if (effect == Effect.WRITE) {
      LOG.info("MCP tool {} called by user {}", name, identity.userId());
    } else {
      LOG.debug("MCP tool {} called by user {}", name, identity.userId());
    }
    try {
      Map<String, Object> result = runAs(identity, () -> body.call(new ToolArguments(arguments), identity));
      return CallToolResult.builder()
          .structuredContent(result)
          .addTextContent(jsonMapper.writeValueAsString(result))
          .isError(false)
          .build();
    } catch (ToolException e) {
      return error(e.getMessage());
    } catch (FourEyesViolationException e) {
      return error("Refused by the four-eyes rule: " + e.getMessage());
    } catch (AuthorizationException e) {
      return error("Not authorized: " + e.getMessage());
    } catch (ProcessEngineException e) {
      return error(engineMessage(e));
    } catch (IOException | RuntimeException e) {
      String reference = reference();
      LOG.warn("MCP tool {} failed for user {} [reference {}]", name, identity.userId(), reference, e);
      return error("The tool failed with an internal error (reference " + reference + "; details are in the server log)");
    }
  }

  /**
   * Runs the action with the identity set on the engine, so that its authorization checks apply
   * and the audit trail records the user; the previous authentication of the thread is restored.
   */
  public <T> T runAs(McpIdentity identity, Supplier<T> action) {
    IdentityService identityService = engine.getIdentityService();
    Authentication previous = identityService.getCurrentAuthentication();
    identityService.setAuthentication(identity.userId(), identity.groups(), identity.tenants());
    try {
      return action.get();
    } finally {
      if (previous != null) {
        identityService.setAuthentication(previous);
      } else {
        identityService.clearAuthentication();
      }
    }
  }

  /** The identity of the request, set by the request filter; never missing for a served call. */
  public static McpIdentity identity(McpTransportContext context) {
    Object identity = context == null ? null : context.get(McpIdentity.CONTEXT_KEY);
    if (identity instanceof McpIdentity mcpIdentity) {
      return mcpIdentity;
    }
    throw McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST).message("Not authenticated").build();
  }

  /**
   * The message of an engine exception if it is meant for the caller (a missing or invalid
   * argument, something not found, suspended or changed concurrently, a task claimed by someone
   * else); otherwise a reference to the server log. Messages of failed
   * expressions, scripts, delegates and the persistence layer can carry model details, values or
   * secrets and are never returned.
   */
  protected String engineMessage(ProcessEngineException e) {
    String message = e.getMessage();
    if (message != null && !message.contains("### ") && isForTheCaller(e)) {
      return message;
    }
    String reference = reference();
    LOG.warn("MCP tool call failed in the engine [reference {}]", reference, e);
    return "The engine could not carry out the request (reference " + reference + "; details are in the server log)";
  }

  protected boolean isForTheCaller(ProcessEngineException e) {
    // a plain ProcessEngineException, even with an ENGINE code, may quote values (for example a
    // decision result that cannot be mapped), so only these kinds are passed on
    return e instanceof BadUserRequestException || e instanceof NullValueException
        || e instanceof SuspendedEntityInteractionException || e instanceof OptimisticLockingException
        || e instanceof TaskAlreadyClaimedException;
  }

  protected static String reference() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  protected CallToolResult error(String message) {
    String text = message == null ? "Error" : message;
    if (text.length() > MAX_ERROR_LENGTH) {
      text = text.substring(0, MAX_ERROR_LENGTH) + "...";
    }
    return CallToolResult.builder().addTextContent(text).isError(true).build();
  }

  /** Reads a model (BPMN, DMN) up to the configured length. */
  public Json model(Supplier<InputStream> stream) {
    int max = properties.getMaxXmlLength();
    try (InputStream input = stream.get(); Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      StringBuilder text = new StringBuilder();
      char[] buffer = new char[8192];
      int read;
      boolean truncated = false;
      while ((read = reader.read(buffer)) != -1) {
        if (text.length() + read > max) {
          text.append(buffer, 0, max - text.length());
          truncated = true;
          break;
        }
        text.append(buffer, 0, read);
      }
      return Json.obj().with("xml", text.toString()).with("truncated", truncated ? Boolean.TRUE : null);
    } catch (IOException e) {
      throw new ToolException("The model cannot be read");
    }
  }

  /** Limits a list to the given number of entries. */
  public static <T> List<T> limit(List<T> list, int max) {
    return list.size() <= max ? list : list.subList(0, max);
  }

  /** A LIKE pattern that finds the text anywhere. */
  public static String like(String text) {
    return text == null ? null : "%" + text + "%";
  }
}
