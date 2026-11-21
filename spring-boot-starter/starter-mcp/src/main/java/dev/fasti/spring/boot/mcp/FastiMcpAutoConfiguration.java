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
package dev.fasti.spring.boot.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ProductPropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import dev.fasti.spring.boot.mcp.auth.BasicMcpAuthenticator;
import dev.fasti.spring.boot.mcp.auth.McpAuthenticator;
import dev.fasti.spring.boot.mcp.auth.McpIdentity;
import dev.fasti.spring.boot.mcp.auth.McpRequestFilter;
import dev.fasti.spring.boot.mcp.tools.DecisionTools;
import dev.fasti.spring.boot.mcp.tools.EngineTools;
import dev.fasti.spring.boot.mcp.tools.IncidentTools;
import dev.fasti.spring.boot.mcp.tools.ModelResources;
import dev.fasti.spring.boot.mcp.tools.ProcessTools;
import dev.fasti.spring.boot.mcp.tools.TaskTools;
import dev.fasti.spring.boot.mcp.tools.ToolSupport;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * Model Context Protocol server of the process engine, off unless {@code fasti.mcp.enabled=true}.
 * <p>
 * Served over the stateless Streamable HTTP transport of the MCP Java SDK at
 * {@code fasti.mcp.path} (default {@code /mcp}) in the servlet container of the application.
 * Every request must be authenticated ({@link McpRequestFilter}); every tool runs as the
 * authenticated user with the authorization checks of the engine. Tools that change data are
 * only offered with {@code fasti.mcp.write-tools.enabled=true}. No tool deploys models or
 * evaluates expressions or scripts given by the caller.
 */
@AutoConfiguration(afterName = {
    "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration",
    "org.camunda.bpm.spring.boot.starter.security.oauth2.FastiRestBearerAutoConfiguration" })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(McpStatelessSyncServer.class)
@ConditionalOnBooleanProperty(prefix = FastiMcpProperties.PREFIX, name = "enabled")
@ConditionalOnBean(ProcessEngine.class)
@EnableConfigurationProperties(FastiMcpProperties.class)
@Import(FastiMcpBearerConfiguration.class)
public class FastiMcpAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(FastiMcpAutoConfiguration.class);

  public static final String SERVLET_NAME = "fastiMcp";

  protected static final String INSTRUCTIONS = """
      Fasti BPM process engine (built on Camunda 7 CE). Every tool runs as the authenticated user \
      and sees only what the engine's authorization checks allow; a refusal comes back as a tool \
      error with the engine's reason. Start with get_engine_info. Lists are paged (firstResult, \
      maxResults) and report the total. Variables whose names look secret are masked and long \
      values are cut. Tools that start processes, claim or complete tasks or retry jobs exist only \
      when the administrator has enabled write tools.""";

  @Bean
  public McpJsonMapper fastiMcpJsonMapper() {
    return new JacksonMcpJsonMapper(JsonMapper.builder().build());
  }

  @Bean
  public HttpServletStatelessServerTransport fastiMcpTransport(FastiMcpProperties properties, McpJsonMapper jsonMapper) {
    String path = validPath(properties.getPath());
    long maxRequestSize = properties.getMaxRequestSize().toBytes();
    if (maxRequestSize <= 0 || maxRequestSize > Integer.MAX_VALUE) {
      throw new IllegalStateException(FastiMcpProperties.PREFIX + ".max-request-size must be between 1 byte and 2 GB");
    }
    return HttpServletStatelessServerTransport.builder()
        .jsonMapper(jsonMapper)
        .messageEndpoint(path)
        .maxRequestSize((int) maxRequestSize)
        .contextExtractor(request -> {
          Object identity = request.getAttribute(McpIdentity.REQUEST_ATTRIBUTE);
          return identity instanceof McpIdentity
              ? McpTransportContext.create(Map.of(McpIdentity.CONTEXT_KEY, identity))
              : McpTransportContext.EMPTY;
        })
        .build();
  }

  @Bean
  public ToolSupport fastiMcpToolSupport(ProcessEngine processEngine, FastiMcpProperties properties,
                                         McpJsonMapper jsonMapper) {
    if (properties.getMaxResults() < 1 || properties.getDefaultResults() < 1) {
      throw new IllegalStateException(FastiMcpProperties.PREFIX + ".max-results and default-results must be positive");
    }
    return new ToolSupport(processEngine, properties, jsonMapper);
  }

  @Bean(destroyMethod = "close")
  public McpStatelessSyncServer fastiMcpServer(HttpServletStatelessServerTransport transport, ToolSupport support,
                                               FastiMcpProperties properties, McpJsonMapper jsonMapper,
                                               ProcessEngine processEngine) {
    ProcessTools processTools = new ProcessTools(support);
    TaskTools taskTools = new TaskTools(support);
    IncidentTools incidentTools = new IncidentTools(support);
    List<SyncToolSpecification> tools = new ArrayList<>();
    tools.addAll(new EngineTools(support).readTools());
    tools.addAll(processTools.readTools());
    tools.addAll(taskTools.readTools());
    tools.addAll(incidentTools.readTools());
    tools.addAll(new DecisionTools(support).readTools());
    boolean writeTools = properties.getWriteTools().isEnabled();
    if (writeTools) {
      tools.addAll(processTools.writeTools());
      tools.addAll(taskTools.writeTools());
      tools.addAll(incidentTools.writeTools());
    }

    if (!processEngine.getProcessEngineConfiguration().isAuthorizationEnabled()) {
      LOG.warn("The MCP server is enabled while authorization checks of the engine are off "
          + "(camunda.bpm.authorization.enabled=false): every authenticated user can read{} all data through it",
          writeTools ? " and change" : "");
    }
    LOG.info("MCP server at {} with {} tools; write tools {}", properties.getPath(), tools.size(),
        writeTools ? "enabled" : "disabled");

    return McpServer.sync(transport)
        .serverInfo(McpSchema.Implementation.builder("fasti-bpm", ProductPropertiesUtil.getProductVersion())
            .title("Fasti BPM")
            .build())
        .instructions(INSTRUCTIONS)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).resources(false, false).build())
        .jsonMapper(jsonMapper)
        .jsonSchemaValidator(new DefaultJsonSchemaValidator(JsonMapper.builder().build()))
        .validateToolInputs(true)
        // tools run on the request thread, which carries nothing else of the engine
        .immediateExecution(true)
        .tools(tools)
        .resourceTemplates(new ModelResources(support).templates())
        .build();
  }

  @Bean
  public ServletRegistrationBean<HttpServletStatelessServerTransport> fastiMcpServlet(
      HttpServletStatelessServerTransport transport, McpStatelessSyncServer server, FastiMcpProperties properties) {
    ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
        new ServletRegistrationBean<>(transport, validPath(properties.getPath()));
    registration.setName(SERVLET_NAME);
    registration.setAsyncSupported(true);
    registration.setLoadOnStartup(1);
    return registration;
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnBooleanProperty(prefix = FastiMcpProperties.PREFIX, name = "basic-auth-enabled", matchIfMissing = true)
  public BasicMcpAuthenticator fastiMcpBasicAuthenticator() {
    return new BasicMcpAuthenticator();
  }

  @Bean
  public FilterRegistrationBean<McpRequestFilter> fastiMcpRequestFilter(
      ProcessEngine processEngine, ObjectProvider<McpAuthenticator> authenticators, FastiMcpProperties properties,
      HttpServletStatelessServerTransport transport,
      ServletRegistrationBean<HttpServletStatelessServerTransport> servlet) {
    List<McpAuthenticator> available = authenticators.orderedStream().toList();
    if (available.isEmpty()) {
      throw new IllegalStateException("The MCP server has no authentication method: enable "
          + FastiMcpProperties.PREFIX + ".basic-auth-enabled or bearer tokens (fasti.security.oidc.rest.bearer-enabled)");
    }
    McpRequestFilter filter = new McpRequestFilter(processEngine, available, properties.getAllowedOrigins(),
        properties.getAllowedHosts(), transport.protocolVersions());
    FilterRegistrationBean<McpRequestFilter> registration = new FilterRegistrationBean<>(filter, servlet);
    registration.setName("fastiMcpRequestFilter");
    registration.setAsyncSupported(true);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }

  protected static String validPath(String path) {
    if (path == null || !path.startsWith("/") || path.length() < 2 || path.endsWith("/") || path.contains("*")
        || path.contains("..")) {
      throw new IllegalStateException(FastiMcpProperties.PREFIX + ".path must be a path like /mcp, not '" + path + "'");
    }
    return path;
  }
}
