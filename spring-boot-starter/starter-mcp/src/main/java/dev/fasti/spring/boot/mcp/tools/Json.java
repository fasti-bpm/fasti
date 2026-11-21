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

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON objects tools return: fields without a value are left out, dates become
 * ISO-8601 instants (UTC).
 */
public final class Json extends LinkedHashMap<String, Object> {

  private static final long serialVersionUID = 1L;

  public static Json obj() {
    return new Json();
  }

  public Json with(String name, Object value) {
    if (value instanceof Date date) {
      put(name, date(date));
    } else if (value != null) {
      put(name, value);
    }
    return this;
  }

  public static String date(Date date) {
    return date == null ? null : date.toInstant().toString();
  }

  /** A page of a list: the items, where it starts and how many there are in total. */
  public static Json page(List<? extends Map<String, Object>> items, int firstResult, long total) {
    return obj().with("items", items).with("count", items.size()).with("firstResult", firstResult)
        .with("total", total);
  }
}
