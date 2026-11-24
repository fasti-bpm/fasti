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
package dev.fasti.engine.analytics.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.fasti.engine.analytics.DurationStatistics;
import dev.fasti.engine.analytics.DurationStatistics.Bucket;

/**
 * A histogram of durations that every supported database computes with plain SQL.
 *
 * <p>Ten buckets per power of ten from 1 ms to about three years. The bucket of a duration is a
 * nested {@code CASE} of constant numbers (a binary search, at most eight comparisons per row),
 * so no database function is needed: logarithms and percentile functions differ between, or are
 * missing in, PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, Db2 and H2. Percentiles are
 * interpolated within their bucket on a logarithmic scale and lie within about 12 % of the exact
 * value.</p>
 */
public final class DurationBuckets {

  /** Exclusive upper ends of the buckets; bucket {@code i} holds {@code [BOUNDS[i-1], BOUNDS[i])}, the last one is open. */
  static final long[] BOUNDS = bounds();

  /** Number of buckets: one more than bounds. */
  static final int SIZE = BOUNDS.length + 1;

  private DurationBuckets() {
  }

  private static long[] bounds() {
    List<Long> bounds = new ArrayList<>();
    bounds.add(1L);
    for (int step = 1; step <= 110; step++) {
      long bound = Math.round(Math.pow(10, step / 10.0));
      if (bound > bounds.get(bounds.size() - 1)) {
        bounds.add(bound);
      }
    }
    return bounds.stream().mapToLong(Long::longValue).toArray();
  }

  /** The SQL expression of the bucket index of a duration column (an integer expression, numbers only). */
  public static String sql(String column) {
    StringBuilder sql = new StringBuilder();
    append(sql, column, 0, SIZE - 1);
    return sql.toString();
  }

  private static void append(StringBuilder sql, String column, int first, int last) {
    if (first == last) {
      sql.append(first);
      return;
    }
    int middle = (first + last) / 2;
    sql.append("case when ").append(column).append(" < ").append(BOUNDS[middle]).append(" then ");
    append(sql, column, first, middle);
    sql.append(" else ");
    append(sql, column, middle + 1, last);
    sql.append(" end");
  }

  /** The bucket of a duration, as the SQL expression computes it. */
  static int bucketOf(long duration) {
    int index = Arrays.binarySearch(BOUNDS, duration);
    return index >= 0 ? index + 1 : -index - 1;
  }

  static long lower(int bucket) {
    return bucket == 0 ? 0 : BOUNDS[bucket - 1];
  }

  static Long upper(int bucket) {
    return bucket < BOUNDS.length ? BOUNDS[bucket] : null;
  }

  /** Statistics of durations read one by one: exact percentiles (nearest rank), and the histogram. */
  public static DurationStatistics exact(long[] durations, Long target) {
    if (durations.length == 0) {
      return DurationStatistics.empty(target);
    }
    long[] sorted = durations.clone();
    Arrays.sort(sorted);
    Histogram histogram = new Histogram();
    long within = 0;
    for (long duration : sorted) {
      histogram.add(duration);
      if (target != null && duration <= target) {
        within++;
      }
    }
    DurationStatistics buckets = histogram.toStatistics(null, false);
    return new DurationStatistics(sorted.length, histogram.total, sorted[0], sorted[sorted.length - 1],
        rank(sorted, 0.5), rank(sorted, 0.75), rank(sorted, 0.9), rank(sorted, 0.95), rank(sorted, 0.99), false,
        target == null ? null : within, buckets.getBuckets());
  }

  private static long rank(long[] sorted, double share) {
    int index = (int) Math.ceil(share * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
  }

  /** Counts per bucket plus the exact aggregates, as read from the database. */
  public static final class Histogram {

    final long[] counts = new long[SIZE];
    long count;
    long total;
    Long minimum;
    Long maximum;
    long withinTarget;

    public void add(int bucket, long count, long total, Long minimum, Long maximum, Long withinTarget) {
      if (bucket < 0 || bucket >= SIZE || count <= 0) {
        return;
      }
      counts[bucket] += count;
      this.count += count;
      this.total += total;
      if (minimum != null) {
        this.minimum = this.minimum == null ? minimum : Math.min(this.minimum, minimum);
      }
      if (maximum != null) {
        this.maximum = this.maximum == null ? maximum : Math.max(this.maximum, maximum);
      }
      if (withinTarget != null) {
        this.withinTarget += withinTarget;
      }
    }

    /** Adds one exact duration (used where the values are read row by row). */
    public void add(long duration) {
      add(bucketOf(duration), 1, duration, duration, duration, null);
    }

    public long getCount() {
      return count;
    }

    /** The value below which the given share (0..1) of the durations lie. */
    public Long percentile(double share) {
      if (count == 0) {
        return null;
      }
      double rank = share * count;
      long seen = 0;
      for (int bucket = 0; bucket < SIZE; bucket++) {
        long inBucket = counts[bucket];
        if (inBucket == 0) {
          continue;
        }
        if (seen + inBucket >= rank) {
          double low = Math.max(lower(bucket), minimum);
          Long upper = upper(bucket);
          double high = Math.min(upper == null ? maximum : upper, maximum);
          if (high <= low) {
            return Math.round(low);
          }
          double within = (rank - seen) / inBucket;
          double value = low <= 0 ? low + (high - low) * within : low * Math.pow(high / low, within);
          return Math.max(minimum, Math.min(maximum, Math.round(value)));
        }
        seen += inBucket;
      }
      return maximum;
    }

    /** The time beyond the median, summed over all durations (estimated from the buckets). */
    public Long timeAbove(Long median) {
      if (count == 0 || median == null) {
        return null;
      }
      double above = 0;
      for (int bucket = 0; bucket < SIZE; bucket++) {
        long inBucket = counts[bucket];
        if (inBucket == 0) {
          continue;
        }
        double low = Math.max(lower(bucket), minimum);
        Long upper = upper(bucket);
        double high = Math.min(upper == null ? maximum : upper, maximum);
        double middle = low <= 0 || high <= low ? (low + high) / 2 : Math.sqrt(low * high);
        if (middle > median) {
          above += inBucket * (middle - median);
        }
      }
      return Math.round(above);
    }

    public DurationStatistics toStatistics(Long target, boolean approximate) {
      if (count == 0) {
        return DurationStatistics.empty(target);
      }
      List<Bucket> buckets = new ArrayList<>();
      for (int bucket = 0; bucket < SIZE; bucket++) {
        if (counts[bucket] > 0) {
          buckets.add(new Bucket(lower(bucket), upper(bucket), counts[bucket]));
        }
      }
      return new DurationStatistics(count, total, minimum, maximum, percentile(0.5), percentile(0.75),
          percentile(0.9), percentile(0.95), percentile(0.99), approximate, target == null ? null : withinTarget,
          buckets);
    }
  }
}
