package dev.fasti.engine.analytics.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.fasti.engine.analytics.AnalyticsBusyException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The turns of the analytics reads and the merging of the same reads. */
public class AnalyticsLimiterTest {

  private int maxConcurrent;
  private Duration maxWait;
  private Duration timeToLive;
  private ExecutorService threads;

  @Before
  public void remember() {
    maxConcurrent = AnalyticsLimiter.getMaxConcurrent();
    maxWait = AnalyticsLimiter.getMaxWait();
    timeToLive = AnalyticsCache.getTimeToLive();
    threads = Executors.newFixedThreadPool(8);
    AnalyticsCache.clear();
  }

  @After
  public void restore() throws InterruptedException {
    threads.shutdownNow();
    threads.awaitTermination(10, TimeUnit.SECONDS);
    AnalyticsLimiter.setMaxConcurrent(maxConcurrent);
    AnalyticsLimiter.setMaxWait(maxWait);
    AnalyticsCache.setTimeToLive(timeToLive);
    AnalyticsCache.clear();
  }

  @Test
  public void shouldRunNoMoreReadsAtOnceThanAllowed() throws Exception {
    AnalyticsLimiter.setMaxConcurrent(2);
    AnalyticsLimiter.setMaxWait(Duration.ofSeconds(30));
    AtomicInteger running = new AtomicInteger();
    AtomicInteger most = new AtomicInteger();
    List<Future<Integer>> reads = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      int value = index;
      reads.add(threads.submit(() -> AnalyticsLimiter.run(true, () -> {
        most.accumulateAndGet(running.incrementAndGet(), Math::max);
        sleep(50);
        running.decrementAndGet();
        return value;
      })));
    }
    for (int index = 0; index < reads.size(); index++) {
      assertThat(reads.get(index).get(10, TimeUnit.SECONDS)).isEqualTo(index);
    }
    assertThat(most.get()).isEqualTo(2);
  }

  @Test
  public void shouldGiveUpAfterTheLongestWait() throws Exception {
    AnalyticsLimiter.setMaxConcurrent(1);
    AnalyticsLimiter.setMaxWait(Duration.ofMillis(100));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Future<String> holder = threads.submit(() -> AnalyticsLimiter.run(true, () -> {
      started.countDown();
      await(release);
      return "first";
    }));
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> AnalyticsLimiter.run(true, () -> "second")).isInstanceOf(AnalyticsBusyException.class)
        .hasMessageContaining("busy");

    release.countDown();
    assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo("first");
    // the permit came back
    assertThat(AnalyticsLimiter.run(true, () -> "third")).isEqualTo("third");
  }

  @Test
  public void shouldNotLetTheQuickReadsWaitBehindTheScans() throws Exception {
    AnalyticsLimiter.setMaxConcurrent(1);
    AnalyticsLimiter.setMaxWait(Duration.ofMillis(100));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Future<String> scan = threads.submit(() -> AnalyticsLimiter.run(true, () -> {
      started.countDown();
      await(release);
      return "scan";
    }));
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

    assertThat(AnalyticsLimiter.run(false, () -> "quick")).isEqualTo("quick");
    assertThatThrownBy(() -> AnalyticsLimiter.run(true, () -> "second scan")).isInstanceOf(AnalyticsBusyException.class);
    // the quick reads have one turn of their own
    AnalyticsLimiter.setMaxConcurrent(3);
    CountDownLatch quickStarted = new CountDownLatch(1);
    CountDownLatch quickRelease = new CountDownLatch(1);
    Future<String> quick = threads.submit(() -> AnalyticsLimiter.run(false, () -> {
      quickStarted.countDown();
      await(quickRelease);
      return "quick";
    }));
    assertThat(quickStarted.await(10, TimeUnit.SECONDS)).isTrue();
    assertThatThrownBy(() -> AnalyticsLimiter.run(false, () -> "second quick")).isInstanceOf(AnalyticsBusyException.class);
    assertThat(AnalyticsLimiter.run(true, () -> "scan beside")).isEqualTo("scan beside");
    quickRelease.countDown();
    assertThat(quick.get(10, TimeUnit.SECONDS)).isEqualTo("quick");

    release.countDown();
    assertThat(scan.get(10, TimeUnit.SECONDS)).isEqualTo("scan");
  }

  @Test
  public void shouldRunWithoutLimit() {
    AnalyticsLimiter.setMaxConcurrent(0);
    assertThat(AnalyticsLimiter.getMaxConcurrent()).isZero();
    assertThat(AnalyticsLimiter.run(true, () -> "read")).isEqualTo("read");
  }

  @Test
  public void shouldReadTheSameOnceWhenAskedAtTheSameTime() throws Exception {
    shouldMergeTheSameReads(Duration.ofSeconds(60));
  }

  @Test
  public void shouldMergeTheSameReadsAlsoWithoutCache() throws Exception {
    shouldMergeTheSameReads(Duration.ZERO);
  }

  private void shouldMergeTheSameReads(Duration ttl) throws Exception {
    AnalyticsCache.setTimeToLive(ttl);
    AtomicInteger reads = new AtomicInteger();
    CountDownLatch release = new CountDownLatch(1);
    List<Future<String>> answers = new ArrayList<>();
    for (int index = 0; index < 6; index++) {
      answers.add(threads.submit(() -> AnalyticsCache.get("same", () -> {
        reads.incrementAndGet();
        await(release);
        return "answer";
      })));
    }
    // all six are waiting for the one read
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (reads.get() == 0 && System.nanoTime() < deadline) {
      sleep(5);
    }
    sleep(100);
    release.countDown();
    for (Future<String> answer : answers) {
      assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo("answer");
    }
    assertThat(reads.get()).isEqualTo(1);

    // later: from the cache, or read again without it
    AnalyticsCache.get("same", () -> {
      reads.incrementAndGet();
      return "again";
    });
    assertThat(reads.get()).isEqualTo(ttl.isZero() ? 2 : 1);
  }

  @Test
  public void shouldGiveTheFailureToAllWhoWaitedAndReadAgainLater() throws Exception {
    AnalyticsCache.setTimeToLive(Duration.ofSeconds(60));
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger reads = new AtomicInteger();
    List<Future<String>> answers = new ArrayList<>();
    for (int index = 0; index < 3; index++) {
      answers.add(threads.submit(() -> AnalyticsCache.<String>get("failing", () -> {
        reads.incrementAndGet();
        await(release);
        throw new IllegalStateException("database gone");
      })));
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (reads.get() == 0 && System.nanoTime() < deadline) {
      sleep(5);
    }
    sleep(100);
    release.countDown();
    for (Future<String> answer : answers) {
      assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).hasRootCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(reads.get()).isEqualTo(1);
    // a failure is not kept
    assertThat(AnalyticsCache.get("failing", () -> "now")).isEqualTo("now");
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("not released");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
