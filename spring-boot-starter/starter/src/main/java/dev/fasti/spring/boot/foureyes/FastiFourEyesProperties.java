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
package dev.fasti.spring.boot.foureyes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Four-eyes control for approvals ({@code fasti.four-eyes.*}).
 */
@ConfigurationProperties(FastiFourEyesProperties.PREFIX)
public class FastiFourEyesProperties {

  public static final String PREFIX = "fasti.four-eyes";

  /**
   * Whether the engine enforces the four-eyes rules of user tasks (extension property
   * fasti:fourEyes): a person who completed an earlier step that a task checks cannot complete it.
   */
  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
