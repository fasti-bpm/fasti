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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

/**
 * Typed access to the arguments of a tool call. The SDK has validated them against the input
 * schema already; the checks here keep the tools safe on their own.
 */
public class ToolArguments {

  protected final Map<String, Object> arguments;

  public ToolArguments(Map<String, Object> arguments) {
    this.arguments = arguments == null ? Map.of() : arguments;
  }

  /** The string argument, {@code null} when absent or blank. */
  public String string(String name) {
    Object value = arguments.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String text)) {
      throw new ToolException("Argument '" + name + "' must be a string");
    }
    String trimmed = text.trim();
    if (trimmed.length() > 1000) {
      throw new ToolException("Argument '" + name + "' is too long");
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  public String requiredString(String name) {
    String value = string(name);
    if (value == null) {
      throw new ToolException("Argument '" + name + "' is required");
    }
    return value;
  }

  public boolean bool(String name, boolean defaultValue) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean flag)) {
      throw new ToolException("Argument '" + name + "' must be true or false");
    }
    return flag;
  }

  public Integer integer(String name, Integer defaultValue, int minimum, int maximum) {
    Object value = arguments.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
      throw new ToolException("Argument '" + name + "' must be a whole number");
    }
    long longValue = number.longValue();
    if (longValue < minimum || longValue > maximum) {
      throw new ToolException("Argument '" + name + "' must be between " + minimum + " and " + maximum);
    }
    return (int) longValue;
  }

  /**
   * A point in time: an ISO-8601 instant or date-time with offset ({@code 2026-11-21T08:00:00Z}),
   * a local date-time (taken as UTC) or a date (its start, UTC).
   */
  public Date date(String name) {
    String value = string(name);
    if (value == null) {
      return null;
    }
    try {
      return Date.from(OffsetDateTime.parse(value).toInstant());
    } catch (DateTimeParseException e) {
      // try the next format
    }
    try {
      return Date.from(Instant.parse(value));
    } catch (DateTimeParseException e) {
      // try the next format
    }
    try {
      return Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException e) {
      // try the next format
    }
    try {
      return Date.from(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException e) {
      throw new ToolException("Argument '" + name + "' must be an ISO-8601 date or date-time, e.g. 2026-11-21T08:00:00Z");
    }
  }

  /** The object argument, {@code null} when absent. */
  @SuppressWarnings("unchecked")
  public Map<String, Object> object(String name) {
    Object value = arguments.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw new ToolException("Argument '" + name + "' must be an object");
    }
    return (Map<String, Object>) map;
  }

  /** Index of the first item of a page. */
  public int firstResult() {
    return integer("firstResult", 0, 0, 1_000_000);
  }

  /** Size of a page, within the configured limit. */
  public int maxResults(int defaultResults, int maxResults) {
    return integer("maxResults", Math.min(defaultResults, maxResults), 1, maxResults);
  }
}
