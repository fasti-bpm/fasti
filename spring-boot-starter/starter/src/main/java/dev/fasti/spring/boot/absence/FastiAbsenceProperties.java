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
package dev.fasti.spring.boot.absence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Substitutes during absence ({@code fasti.absence.*}).
 */
@ConfigurationProperties(FastiAbsenceProperties.PREFIX)
public class FastiAbsenceProperties {

  public static final String PREFIX = "fasti.absence";

  /**
   * Whether users can record an absence with a substitute, who then gets the user tasks created
   * for or assigned to them during the absence.
   */
  private boolean enabled = false;

  /**
   * How many absent substitutes in a row are followed to find a present one.
   */
  private int maxHops = 5;

  /**
   * Whether the substitute of an absent candidate user becomes a candidate user as well.
   */
  private boolean substituteCandidates = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxHops() {
    return maxHops;
  }

  public void setMaxHops(int maxHops) {
    this.maxHops = maxHops;
  }

  public boolean isSubstituteCandidates() {
    return substituteCandidates;
  }

  public void setSubstituteCandidates(boolean substituteCandidates) {
    this.substituteCandidates = substituteCandidates;
  }
}
