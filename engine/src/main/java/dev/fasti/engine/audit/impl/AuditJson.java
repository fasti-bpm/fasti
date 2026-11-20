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
package dev.fasti.engine.audit.impl;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the JSON object of the details of a record, with keys in alphabetical order and without
 * {@code null} values. The stored text is what is hashed, so it never has to be re-serialized.
 */
public class AuditJson {

  protected final Map<String, Object> values = new TreeMap<>();

  public static AuditJson object() {
    return new AuditJson();
  }

  public AuditJson put(String key, Object value) {
    if (value != null && !(value instanceof String && ((String) value).isEmpty())) {
      values.put(key, value);
    }
    return this;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /** The JSON text, or {@code null} without values. */
  public String toJson() {
    if (values.isEmpty()) {
      return null;
    }
    StringBuilder out = new StringBuilder();
    write(out, values);
    return out.toString();
  }

  @SuppressWarnings("unchecked")
  static void write(StringBuilder out, Object value) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof AuditJson) {
      write(out, ((AuditJson) value).values);
    } else if (value instanceof Map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        string(out, entry.getKey());
        out.append(':');
        write(out, entry.getValue());
      }
      out.append('}');
    } else if (value instanceof Collection) {
      out.append('[');
      boolean first = true;
      for (Object item : (Collection<Object>) value) {
        if (!first) {
          out.append(',');
        }
        first = false;
        write(out, item);
      }
      out.append(']');
    } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
      out.append(value);
    } else if (value instanceof Double || value instanceof Float) {
      double number = ((Number) value).doubleValue();
      if (Double.isNaN(number) || Double.isInfinite(number)) {
        string(out, String.valueOf(number));
      } else {
        out.append(number);
      }
    } else {
      string(out, String.valueOf(value));
    }
  }

  static void string(StringBuilder out, String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (c < 0x20 || c == 0x2028 || c == 0x2029) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
      }
    }
    out.append('"');
  }
}
