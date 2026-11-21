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
import java.util.List;
import java.util.Map;

/**
 * What a purge removed. A chain that could not be purged (for example because another
 * transaction held it for longer than the lock timeout) does not stop the purge of the others: it
 * is listed in {@link #getFailedChains()}, and what was purged of it before the failure is still in
 * {@link #getPurgedThrough()}.
 */
public class AuditPurgeResult {

  protected final Date before;
  protected final Map<String, Long> purgedThrough;
  protected final long removed;
  protected final List<String> failedChains;

  public AuditPurgeResult(Date before, Map<String, Long> purgedThrough, long removed) {
    this(before, purgedThrough, removed, List.of());
  }

  public AuditPurgeResult(Date before, Map<String, Long> purgedThrough, long removed, List<String> failedChains) {
    this.before = before;
    this.purgedThrough = Collections.unmodifiableMap(purgedThrough);
    this.removed = removed;
    this.failedChains = List.copyOf(failedChains);
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

  /** Chains whose purge failed, sorted; the cause is in the log of the server. Purge again to complete them. */
  public List<String> getFailedChains() {
    return failedChains;
  }

  /** Whether every chain was purged. */
  public boolean isComplete() {
    return failedChains.isEmpty();
  }
}
