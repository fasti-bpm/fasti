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
package dev.fasti.spring.boot.performance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(FastiPerformanceProperties.PREFIX)
public class FastiPerformanceProperties {

  public static final String PREFIX = "fasti.performance";

  /**
   * Whether the engine logs one warning at start that lists the optional performance indexes
   * (sql/optional of the distribution) missing in its database: warn or off (another value is logged and read as warn).
   */
  private String indexCheck = "warn";

  /**
   * Whether the engine logs a warning at start for each setting that is known to cost performance
   * as the data grows (history without cleanup, connection pool smaller than the job executor
   * needs, ...): warn or off (another value is logged and read as warn).
   */
  private String configCheck = "warn";

  public String getIndexCheck() {
    return indexCheck;
  }

  public void setIndexCheck(String indexCheck) {
    this.indexCheck = indexCheck;
  }

  public String getConfigCheck() {
    return configCheck;
  }

  public void setConfigCheck(String configCheck) {
    this.configCheck = configCheck;
  }
}
