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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.json.JsonMapper;

/**
 * Speaks MCP (JSON-RPC 2.0 over Streamable HTTP) to the server under test, as an MCP client
 * does: POST with {@code Accept: application/json, text/event-stream}.
 */
public class McpHttpClient {

  public static final String PROTOCOL_VERSION = "2025-11-25";

  protected final HttpClient client = HttpClient.newHttpClient();
  protected final JsonMapper mapper = JsonMapper.builder().build();
  protected final URI uri;
  protected final AtomicInteger ids = new AtomicInteger();

  public McpHttpClient(int port, String path) {
    this.uri = URI.create("http://localhost:" + port + path);
  }

  public URI uri() {
    return uri;
  }

  public static String basic(String user, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  public HttpResponse<String> post(String body, Map<String, String> headers) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    return send(request.build());
  }

  public HttpResponse<String> send(HttpRequest request) {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public String request(String method, Object params) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("jsonrpc", "2.0");
    message.put("id", ids.incrementAndGet());
    message.put("method", method);
    if (params != null) {
      message.put("params", params);
    }
    return mapper.writeValueAsString(message);
  }

  /** Sends a JSON-RPC request as the user and returns the parsed response (status 200 asserted). */
  public Map<String, Object> rpc(String user, String password, String method, Object params) {
    HttpResponse<String> response = post(request(method, params),
        Map.of("Authorization", basic(user, password), "MCP-Protocol-Version", PROTOCOL_VERSION));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return parse(response.body());
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> parse(String body) {
    return mapper.readValue(body, Map.class);
  }

  public Map<String, Object> initialize(String user, String password) {
    return rpc(user, password, "initialize", Map.of(
        "protocolVersion", PROTOCOL_VERSION,
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "fasti-test", "version", "1.0")));
  }

  /** Calls a tool and returns the {@code result} (a CallToolResult). */
  @SuppressWarnings("unchecked")
  public Map<String, Object> callTool(String user, String password, String name, Map<String, Object> arguments) {
    Map<String, Object> response = rpc(user, password, "tools/call", Map.of("name", name, "arguments", arguments));
    assertThat(response).as("JSON-RPC response of %s", name).containsKey("result");
    return (Map<String, Object>) response.get("result");
  }

  /** Calls a tool that must succeed and returns its structured content. */
  @SuppressWarnings("unchecked")
  public Map<String, Object> callToolOk(String user, String password, String name, Map<String, Object> arguments) {
    Map<String, Object> result = callTool(user, password, name, arguments);
    assertThat(result.get("isError")).as("%s failed: %s", name, result).isEqualTo(false);
    return (Map<String, Object>) result.get("structuredContent");
  }

  /** Calls a tool that must fail and returns the error text. */
  @SuppressWarnings("unchecked")
  public String callToolError(String user, String password, String name, Map<String, Object> arguments) {
    Map<String, Object> result = callTool(user, password, name, arguments);
    assertThat(result.get("isError")).as("%s should fail: %s", name, result).isEqualTo(true);
    assertThat(result).doesNotContainKey("structuredContent");
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    return (String) content.get(0).get("text");
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> items(Map<String, Object> page) {
    return (List<Map<String, Object>>) page.get("items");
  }
}
