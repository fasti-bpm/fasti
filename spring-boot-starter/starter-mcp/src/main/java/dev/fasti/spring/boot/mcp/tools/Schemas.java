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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON schemas of tool inputs. Every object schema refuses unknown properties, so a
 * misspelt argument is reported instead of silently ignored.
 */
public final class Schemas {

  private final Map<String, Object> properties = new LinkedHashMap<>();
  private final List<String> required = new ArrayList<>();

  private Schemas() {
  }

  public static Schemas object() {
    return new Schemas();
  }

  public Schemas string(String name, String description) {
    return property(name, Map.of("type", "string", "description", description, "maxLength", 1000));
  }

  public Schemas enumeration(String name, String description, String... values) {
    return property(name, Map.of("type", "string", "description", description, "enum", List.of(values)));
  }

  public Schemas bool(String name, String description) {
    return property(name, Map.of("type", "boolean", "description", description));
  }

  public Schemas integer(String name, String description, int minimum, int maximum) {
    return property(name, Map.of("type", "integer", "description", description, "minimum", minimum, "maximum", maximum));
  }

  /** {@code firstResult} and {@code maxResults} of a list tool. */
  public Schemas paging(int defaultResults, int maxResults) {
    integer("firstResult", "Index of the first item to return (paging), default 0.", 0, 1_000_000);
    return integer("maxResults", "Number of items to return, default " + defaultResults + ", at most " + maxResults + ".",
        1, maxResults);
  }

  /** An object of variables whose values are strings, numbers, booleans or null. */
  public Schemas variables(String name, String description, int maxProperties) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("description", description);
    schema.put("maxProperties", maxProperties);
    schema.put("additionalProperties", Map.of("type", List.of("string", "number", "integer", "boolean", "null")));
    return property(name, schema);
  }

  public Schemas required(String... names) {
    required.addAll(List.of(names));
    return this;
  }

  protected Schemas property(String name, Map<String, Object> schema) {
    properties.put(name, schema);
    return this;
  }

  public Map<String, Object> build() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", List.copyOf(required));
    }
    schema.put("additionalProperties", false);
    return schema;
  }
}
