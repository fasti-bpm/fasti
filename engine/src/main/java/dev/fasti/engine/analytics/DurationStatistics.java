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
package dev.fasti.engine.analytics;

import java.util.Collections;
import java.util.List;

/**
 * How long something took: count, total, extremes, average and percentiles, in milliseconds.
 *
 * <p>Statistics the engine computes in the database are built from a histogram with twenty
 * buckets per power of ten; their percentiles are interpolated within a bucket and are within
 * about 12 % of the exact value ({@link #isApproximate()}). Count, total, minimum, maximum, average and
 * {@link #getWithinTarget()} are exact.</p>
 */
public class DurationStatistics {

  /** One bucket of the histogram: durations from {@code lower} (inclusive) to {@code upper} (exclusive). */
  public static class Bucket {

    protected final long lower;
    protected final Long upper;
    protected final long count;

    public Bucket(long lower, Long upper, long count) {
      this.lower = lower;
      this.upper = upper;
      this.count = count;
    }

    public long getLower() {
      return lower;
    }

    /** The exclusive upper end; {@code null} for the last bucket. */
    public Long getUpper() {
      return upper;
    }

    public long getCount() {
      return count;
    }
  }

  protected final long count;
  protected final long total;
  protected final Long minimum;
  protected final Long maximum;
  protected final Long p50;
  protected final Long p75;
  protected final Long p90;
  protected final Long p95;
  protected final Long p99;
  protected final boolean approximate;
  protected final Long withinTarget;
  protected final List<Bucket> buckets;

  public DurationStatistics(long count, long total, Long minimum, Long maximum, Long p50, Long p75, Long p90, Long p95,
      Long p99, boolean approximate, Long withinTarget, List<Bucket> buckets) {
    this.count = count;
    this.total = total;
    this.minimum = minimum;
    this.maximum = maximum;
    this.p50 = p50;
    this.p75 = p75;
    this.p90 = p90;
    this.p95 = p95;
    this.p99 = p99;
    this.approximate = approximate;
    this.withinTarget = withinTarget;
    this.buckets = buckets == null ? Collections.emptyList() : Collections.unmodifiableList(buckets);
  }

  public static DurationStatistics empty(Long target) {
    return new DurationStatistics(0, 0, null, null, null, null, null, null, null, false, target == null ? null : 0L,
        null);
  }

  public long getCount() {
    return count;
  }

  /** The sum of all durations: the time spent here in total. */
  public long getTotal() {
    return total;
  }

  public Long getMinimum() {
    return minimum;
  }

  public Long getMaximum() {
    return maximum;
  }

  public Long getAverage() {
    return count == 0 ? null : Math.round((double) total / count);
  }

  /** The median. */
  public Long getP50() {
    return p50;
  }

  public Long getP75() {
    return p75;
  }

  public Long getP90() {
    return p90;
  }

  public Long getP95() {
    return p95;
  }

  public Long getP99() {
    return p99;
  }

  public boolean isApproximate() {
    return approximate;
  }

  /** How many took at most the target duration; {@code null} without a target. */
  public Long getWithinTarget() {
    return withinTarget;
  }

  /** The non-empty buckets of the histogram, shortest first. */
  public List<Bucket> getBuckets() {
    return buckets;
  }
}
