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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.SerializableValue;
import org.camunda.bpm.engine.variable.value.TypedValue;

import dev.fasti.spring.boot.mcp.FastiMcpProperties;

/**
 * Turns variable values into JSON for the caller, and JSON arguments into variables.
 * <p>
 * Out: values of masked names are never returned; strings and serialized objects are cut to the
 * configured length; binary values and files are described, not returned; objects stay in their
 * serialized form (Java objects are never deserialized for a tool), Java-serialized objects are
 * only named.
 * <p>
 * In: only strings, numbers, booleans and null, within the configured count and length.
 */
public class Values {

  protected static final String JAVA_SERIALIZATION = "application/x-java-serialized-object";

  protected final List<String> maskedNames;
  protected final int maxValueLength;
  protected final int maxInputVariables;
  protected final int maxInputValueLength;

  public Values(FastiMcpProperties properties) {
    this.maskedNames = properties.getMaskedVariableNames().stream()
        .filter(name -> name != null && !name.isBlank())
        .map(name -> name.trim().toLowerCase(Locale.ROOT))
        .toList();
    this.maxValueLength = properties.getMaxValueLength();
    this.maxInputVariables = properties.getMaxInputVariables();
    this.maxInputValueLength = properties.getMaxInputValueLength();
  }

  /** Whether the value of a variable with this name is never returned. */
  public boolean isMasked(String name) {
    if (name == null) {
      return false;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    for (String masked : maskedNames) {
      if (lower.contains(masked)) {
        return true;
      }
    }
    return false;
  }

  /** A variable as {@code name}, {@code type} and {@code value} (or why there is no value). */
  public Map<String, Object> variable(String name, TypedValue value, String errorMessage) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", name);
    if (value != null && value.getType() != null) {
      result.put("type", value.getType().getName());
    }
    if (isMasked(name)) {
      result.put("masked", true);
      return result;
    }
    if (errorMessage != null) {
      result.put("error", cut(errorMessage, result, "error"));
      return result;
    }
    describe(value, result);
    return result;
  }

  protected void describe(TypedValue typed, Map<String, Object> result) {
    if (typed == null) {
      result.put("value", null);
      return;
    }
    ValueType type = typed.getType();
    if (typed instanceof FileValue file) {
      result.put("filename", file.getFilename());
      result.put("mimeType", file.getMimeType());
      result.put("note", "file content is not returned");
      return;
    }
    if (type != null && ValueType.BYTES.getName().equals(type.getName())) {
      Object bytes = typed.getValue();
      if (bytes instanceof byte[] array) {
        result.put("size", array.length);
      }
      result.put("note", "binary content is not returned");
      return;
    }
    if (typed instanceof SerializableValue serializable) {
      String format = serializable.getSerializationDataFormat();
      result.put("serializationDataFormat", format);
      if (typed instanceof ObjectValue object) {
        result.put("objectTypeName", object.getObjectTypeName());
      }
      if (JAVA_SERIALIZATION.equals(format)) {
        result.put("note", "Java-serialized objects are not returned");
        return;
      }
      String serialized = serializable.getValueSerialized();
      result.put("value", serialized == null ? null : cut(serialized, result, "value"));
      return;
    }
    result.put("value", json(typed.getValue(), result));
  }

  /** A plain value (decision results, query results) as JSON. */
  public Object json(Object value, Map<String, Object> holder) {
    if (value == null || value instanceof Boolean || value instanceof Number) {
      return value;
    }
    if (value instanceof Date date) {
      return Json.date(date);
    }
    if (value instanceof byte[]) {
      return "(binary)";
    }
    return cut(String.valueOf(value), holder, "value");
  }

  protected String cut(String text, Map<String, Object> holder, String field) {
    if (text.length() <= maxValueLength) {
      return text;
    }
    holder.put("truncated", true);
    holder.put("length", text.length());
    return text.substring(0, maxValueLength);
  }

  /** The decision result or other named values, masked and cut like variables. */
  public Map<String, Object> namedValues(Map<String, TypedValue> values) {
    Map<String, Object> result = new LinkedHashMap<>();
    values.forEach((name, value) -> {
      Map<String, Object> described = variable(name, value, null);
      described.remove("name");
      result.put(name, described);
    });
    return result;
  }

  /**
   * Variables from a tool argument: strings, numbers, booleans and null only. Whole numbers become
   * integers (longs when needed), others doubles.
   */
  public VariableMap toVariables(Map<String, Object> input) {
    VariableMap variables = Variables.createVariables();
    if (input == null) {
      return variables;
    }
    if (input.size() > maxInputVariables) {
      throw new ToolException("At most " + maxInputVariables + " variables are accepted");
    }
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String name = entry.getKey();
      if (name == null || name.isBlank() || name.length() > 255) {
        throw new ToolException("Variable names must have 1 to 255 characters");
      }
      variables.putValue(name, toValue(name, entry.getValue()));
    }
    return variables;
  }

  protected Object toValue(String name, Object value) {
    if (value == null || value instanceof Boolean) {
      return value;
    }
    if (value instanceof String text) {
      if (text.length() > maxInputValueLength) {
        throw new ToolException("Variable '" + name + "' is longer than " + maxInputValueLength + " characters");
      }
      return text;
    }
    if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
      return ((Number) value).intValue();
    }
    if (value instanceof Long longValue) {
      return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE ? (Object) longValue.intValue() : longValue;
    }
    if (value instanceof BigInteger big) {
      try {
        return toValue(name, big.longValueExact());
      } catch (ArithmeticException e) {
        throw new ToolException("Variable '" + name + "' is out of range");
      }
    }
    if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
      double number = ((Number) value).doubleValue();
      if (Double.isNaN(number) || Double.isInfinite(number)) {
        throw new ToolException("Variable '" + name + "' is not a finite number");
      }
      return number;
    }
    throw new ToolException("Variable '" + name + "' must be a string, number, boolean or null");
  }
}
