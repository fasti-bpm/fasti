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

import static dev.fasti.spring.boot.mcp.McpTestFixture.ADMIN;
import static dev.fasti.spring.boot.mcp.McpTestFixture.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import io.modelcontextprotocol.server.McpStatelessSyncServer;

/** Without {@code fasti.mcp.enabled=true} nothing of the MCP server exists. */
@SpringBootTest(classes = McpTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "camunda.bpm.process-engine-name=mcpDisabled",
        "spring.datasource.url=jdbc:h2:mem:mcpDisabled;DB_CLOSE_ON_EXIT=FALSE",
        "fasti.mcp.enabled=false"
    })
class McpDisabledByDefaultTest {

  @LocalServerPort
  int port;

  @Autowired
  ApplicationContext context;

  @Test
  void servesNothing() {
    HttpResponse<String> response = new McpHttpClient(port, "/mcp").post("{}",
        Map.of("Authorization", McpHttpClient.basic(ADMIN, ADMIN_PASSWORD)));

    assertThat(context.getBeanNamesForType(McpStatelessSyncServer.class)).isEmpty();
    assertThat(context.containsBean("fastiMcpServlet")).isFalse();
    assertThat(response.statusCode()).isIn(404, 405);
  }

  @Test
  void isOffWithoutTheProperty() {
    assertThat(new FastiMcpProperties().isEnabled()).isFalse();
    assertThat(new FastiMcpProperties().getWriteTools().isEnabled()).isFalse();
  }
}
