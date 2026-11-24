package dev.fasti.engine.analytics.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;

import dev.fasti.engine.analytics.DurationStatistics;
import dev.fasti.engine.analytics.impl.DurationBuckets.Histogram;
import org.junit.Test;

public class DurationBucketsTest {

  @Test
  public void shouldCoverMillisecondsToYearsInAscendingBounds() {
    assertThat(DurationBuckets.BOUNDS[0]).isEqualTo(1);
    assertThat(DurationBuckets.BOUNDS[DurationBuckets.BOUNDS.length - 1]).isGreaterThan(3L * 365 * 24 * 3600 * 1000);
    for (int index = 1; index < DurationBuckets.BOUNDS.length; index++) {
      assertThat(DurationBuckets.BOUNDS[index]).isGreaterThan(DurationBuckets.BOUNDS[index - 1]);
    }
  }

  @Test
  public void shouldPutEveryDurationIntoTheBucketTheSqlComputes() {
    long[] samples = { 0, 1, 2, 3, 9, 10, 11, 99, 100, 101, 12_345, 3_600_000, 86_400_000, Long.MAX_VALUE / 4 };
    for (long duration : samples) {
      assertThat(evaluate(DurationBuckets.sql("D"), duration)).as("duration %s", duration)
          .isEqualTo(DurationBuckets.bucketOf(duration));
    }
  }

  @Test
  public void shouldKeepTheSqlShallow() {
    // SQL Server allows ten levels of nested CASE
    String sql = DurationBuckets.sql("D");
    int depth = 0;
    int deepest = 0;
    for (String token : sql.split(" ")) {
      if (token.equals("case")) {
        deepest = Math.max(deepest, ++depth);
      } else if (token.equals("end")) {
        depth--;
      }
    }
    assertThat(deepest).isLessThanOrEqualTo(8);
  }

  @Test
  public void shouldEstimatePercentilesWithinTwelvePercent() {
    Random random = new Random(42);
    for (int run = 0; run < 20; run++) {
      long[] values = new long[2000];
      for (int index = 0; index < values.length; index++) {
        values[index] = 1 + (long) Math.exp(random.nextGaussian() * 2 + 10);
      }
      Histogram histogram = new Histogram();
      for (long value : values) {
        histogram.add(value);
      }
      long[] sorted = values.clone();
      Arrays.sort(sorted);
      for (double share : new double[] { 0.5, 0.75, 0.9, 0.95, 0.99 }) {
        long exact = sorted[(int) Math.ceil(share * sorted.length) - 1];
        assertThat((double) histogram.percentile(share)).as("P%s", share * 100)
            .isBetween(exact * 0.87, exact * 1.13 + 1);
      }
    }
  }

  @Test
  public void shouldComputeExactStatisticsFromValues() {
    DurationStatistics statistics = DurationBuckets.exact(new long[] { 5, 1, 4, 2, 3 }, 3L);
    assertThat(statistics.isApproximate()).isFalse();
    assertThat(statistics.getP50()).isEqualTo(3);
    assertThat(statistics.getMinimum()).isEqualTo(1);
    assertThat(statistics.getMaximum()).isEqualTo(5);
    assertThat(statistics.getTotal()).isEqualTo(15);
    assertThat(statistics.getWithinTarget()).isEqualTo(3);
    assertThat(statistics.getBuckets()).extracting(DurationStatistics.Bucket::getCount).containsOnly(1L);
  }

  /** A tiny evaluator of the generated nested CASE for one value of column D. */
  static int evaluate(String sql, long value) {
    String[] tokens = sql.split(" ");
    int[] position = { 0 };
    return evaluate(tokens, position, value);
  }

  private static int evaluate(String[] tokens, int[] position, long value) {
    String token = tokens[position[0]++];
    if (!token.equals("case")) {
      return Integer.parseInt(token);
    }
    position[0]++; // when
    position[0]++; // D
    position[0]++; // <
    long bound = Long.parseLong(tokens[position[0]++]);
    position[0]++; // then
    int whenTrue = evaluate(tokens, position, value);
    position[0]++; // else
    int whenFalse = evaluate(tokens, position, value);
    position[0]++; // end
    return value < bound ? whenTrue : whenFalse;
  }
}
