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
package dev.fasti.engine.audit;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

/** What a purge removed. */
public class AuditPurgeResult {

  protected final Date before;
  protected final Map<String, Long> purgedThrough;
  protected final long removed;

  public AuditPurgeResult(Date before, Map<String, Long> purgedThrough, long removed) {
    this.before = before;
    this.purgedThrough = Collections.unmodifiableMap(purgedThrough);
    this.removed = removed;
  }

  public Date getBefore() {
    return before;
  }

  /** Per chain that lost records: the position of the last removed record. */
  public Map<String, Long> getPurgedThrough() {
    return purgedThrough;
  }

  /** Records removed in all. */
  public long getRemoved() {
    return removed;
  }
}
