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

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ProductPropertiesUtil;

import dev.fasti.spring.boot.mcp.tools.ToolSupport.Effect;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/** Information about the engine and the caller. */
public class EngineTools {

  public static final String BASED_ON = "Camunda 7 Community Edition 7.24.0";

  protected final ToolSupport support;

  public EngineTools(ToolSupport support) {
    this.support = support;
  }

  public List<SyncToolSpecification> readTools() {
    return List.of(engineInfo());
  }

  protected SyncToolSpecification engineInfo() {
    return support.tool("get_engine_info", "Engine information",
        "Returns the name and version of the Fasti BPM process engine, its history level, whether "
            + "authorization checks are on, the user the MCP session is authenticated as (with groups "
            + "and tenants), whether write tools are enabled and the result limits. Call it first to "
            + "learn what the other tools can do for this user.",
        Schemas.object().build(), Effect.READ, (arguments, identity) -> {
          ProcessEngineConfiguration configuration = support.engine().getProcessEngineConfiguration();
          Json info = Json.obj()
              .with("product", "Fasti BPM")
              .with("version", ProductPropertiesUtil.getProductVersion())
              .with("basedOn", BASED_ON)
              .with("engineName", support.engine().getName())
              .with("authorizationEnabled", configuration.isAuthorizationEnabled());
          if (configuration instanceof ProcessEngineConfigurationImpl impl && impl.getHistoryLevel() != null) {
            info.with("historyLevel", impl.getHistoryLevel().getName());
          }
          info.with("user", Json.obj()
                  .with("id", identity.userId())
                  .with("groups", identity.groups())
                  .with("tenants", identity.tenants()))
              .with("writeToolsEnabled", support.properties().getWriteTools().isEnabled())
              .with("limits", Json.obj()
                  .with("defaultResults", support.defaultResults())
                  .with("maxResults", support.maxResults())
                  .with("maxVariables", support.properties().getMaxVariables())
                  .with("maxValueLength", support.properties().getMaxValueLength())
                  .with("maxXmlLength", support.properties().getMaxXmlLength()))
              .with("maskedVariableNames", support.properties().getMaskedVariableNames());
          return info;
        });
  }
}
