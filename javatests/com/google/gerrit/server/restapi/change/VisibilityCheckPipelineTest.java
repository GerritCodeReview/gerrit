// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.change;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.entities.Account;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VisibilityCheckPipelineTest {
  private static final Duration LONG_TIMEOUT = Duration.ofSeconds(30);

  private ExecutorService pool;
  private ExecutorService directExecutor;
  private ExecutorService callerExecutor;

  @Before
  public void setUp() {
    pool = Executors.newFixedThreadPool(25);
    directExecutor = MoreExecutors.newDirectExecutorService();
    callerExecutor = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() {
    pool.shutdownNow();
    directExecutor.shutdownNow();
    callerExecutor.shutdownNow();
  }

  /** Both executors Gerrit can use: the FanOut pool and a direct executor (pool size 0). */
  private ImmutableList<ExecutorService> executors() {
    return ImmutableList.of(pool, directExecutor);
  }

  @Test
  public void returnsVisibleCandidatesInRankOrder() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> id % 3 == 0);
      List<Account.Id> result = drain(pipeline(firstIds(20), check, executor, 4, LONG_TIMEOUT));
      assertThat(result).containsExactlyElementsIn(ids(0, 3, 6, 9)).inOrder();
    }
  }

  @Test
  public void eachCandidateCheckedAtMostOnce() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> id % 3 == 0);
      var unused = drain(pipeline(firstIds(20), check, executor, 3, LONG_TIMEOUT));
      assertThat(check.duplicateChecks()).isEmpty();

      FakeCheck noneVisible = new FakeCheck(id -> false);
      var unused2 = drain(pipeline(firstIds(10), noneVisible, executor, 3, LONG_TIMEOUT));
      assertThat(noneVisible.duplicateChecks()).isEmpty();
    }
  }

  @Test
  public void outOfOrderCompletionPreservesOrder() throws Exception {
    FakeCheck check = new FakeCheck(id -> true);
    CountDownLatch release0 = check.block(0);
    VisibilityCheckPipeline pipeline = pipeline(firstIds(10), check, pool, 3, LONG_TIMEOUT);

    Future<List<Account.Id>> result = callerExecutor.submit(() -> drain(pipeline));
    check.awaitCompleted(1);
    check.awaitCompleted(2);
    assertThat(result.isDone()).isFalse();

    release0.countDown();
    assertThat(result.get(10, SECONDS)).containsExactlyElementsIn(ids(0, 1, 2)).inOrder();
  }

  @Test
  public void stopsAtLimit() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> true);
      List<Account.Id> result = drain(pipeline(firstIds(10), check, executor, 3, LONG_TIMEOUT));
      assertThat(result).containsExactlyElementsIn(ids(0, 1, 2)).inOrder();
      // Initial window is limit + 2; visible results never start replacements.
      assertThat(check.totalChecks()).isAtMost(5);
    }
  }

  @Test
  public void invisibleCandidateStartsReplacement() throws Exception {
    FakeCheck check = new FakeCheck(id -> false);
    check.setDelayMillis(5);
    List<Account.Id> result = drain(pipeline(firstIds(20), check, pool, 3, LONG_TIMEOUT));
    assertThat(result).isEmpty();
    assertThat(check.checkedIds()).containsExactlyElementsIn(firstIds(20));
    assertThat(check.maxConcurrent()).isAtMost(5);
  }

  @Test
  public void slowCandidateDoesNotBlockRefill() throws Exception {
    // Candidate 0 is slow and visible, all others are quickly not visible.
    FakeCheck check = new FakeCheck(id -> id == 0);
    CountDownLatch release0 = check.block(0);
    VisibilityCheckPipeline pipeline = pipeline(firstIds(10), check, pool, 2, LONG_TIMEOUT);

    Future<List<Account.Id>> result = callerExecutor.submit(() -> drain(pipeline));
    // The initial window is 0..3. Candidate 9 is only started by refills triggered by
    // invisible results, which must happen while candidate 0 is still blocked.
    check.awaitCompleted(9);
    assertThat(result.isDone()).isFalse();

    release0.countDown();
    assertThat(result.get(10, SECONDS)).containsExactlyElementsIn(ids(0));
  }

  @Test
  public void checkThrows_treatedAsInvisible() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> true);
      check.throwFor(1);
      List<Account.Id> result = drain(pipeline(firstIds(10), check, executor, 3, LONG_TIMEOUT));
      assertThat(result).containsExactlyElementsIn(ids(0, 2, 3)).inOrder();
    }
  }

  @Test
  public void timeout_returnsPartialResults() throws Exception {
    FakeCheck check = new FakeCheck(id -> true);
    CountDownLatch release2 = check.block(2);
    try {
      long start = System.nanoTime();
      List<Account.Id> result =
          drain(pipeline(firstIds(10), check, pool, 5, Duration.ofMillis(100)));
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

      assertThat(result).containsExactlyElementsIn(ids(0, 1)).inOrder();
      assertThat(elapsedMillis).isLessThan(5_000L);
    } finally {
      release2.countDown();
    }
  }

  @Test
  public void getAfterStop_returnsNull() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> true);
      VisibilityCheckPipeline pipeline = pipeline(firstIds(10), check, executor, 3, LONG_TIMEOUT);
      pipeline.stop();
      assertThat(pipeline.get()).isNull();
      assertThat(check.totalChecks()).isEqualTo(0);
    }
  }

  @Test
  public void emptyCandidates() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> true);
      assertThat(drain(pipeline(ImmutableList.of(), check, executor, 3, LONG_TIMEOUT))).isEmpty();
      assertThat(check.totalChecks()).isEqualTo(0);
    }
  }

  @Test
  public void zeroLimit() throws Exception {
    for (ExecutorService executor : executors()) {
      FakeCheck check = new FakeCheck(id -> true);
      assertThat(drain(pipeline(firstIds(10), check, executor, 0, LONG_TIMEOUT))).isEmpty();
      assertThat(check.totalChecks()).isEqualTo(0);
    }
  }

  @Test
  public void interruptedCaller_propagates() throws Exception {
    FakeCheck check = new FakeCheck(id -> true);
    CountDownLatch release0 = check.block(0);
    try {
      VisibilityCheckPipeline pipeline = pipeline(firstIds(10), check, pool, 3, LONG_TIMEOUT);
      AtomicInteger interrupted = new AtomicInteger();
      Thread caller =
          new Thread(
              () -> {
                try {
                  var unused = pipeline.get();
                } catch (InterruptedException e) {
                  interrupted.incrementAndGet();
                }
              });
      caller.start();
      check.awaitStarted(0);
      caller.interrupt();
      caller.join(10_000);
      assertThat(caller.isAlive()).isFalse();
      assertThat(interrupted.get()).isEqualTo(1);
    } finally {
      release0.countDown();
    }
  }

  @Test
  public void interruptedCaller_getThrows() throws Exception {
    FakeCheck check = new FakeCheck(id -> true);
    CountDownLatch release0 = check.block(0);
    try {
      VisibilityCheckPipeline pipeline = pipeline(firstIds(10), check, pool, 3, LONG_TIMEOUT);
      Thread.currentThread().interrupt();
      assertThrows(InterruptedException.class, pipeline::get);
    } finally {
      Thread.interrupted();
      release0.countDown();
    }
  }

  private static VisibilityCheckPipeline pipeline(
      List<Account.Id> candidates,
      FakeCheck check,
      ExecutorService executor,
      int limit,
      Duration timeout) {
    return new VisibilityCheckPipeline(candidates, check, executor, limit, timeout);
  }

  private static List<Account.Id> drain(VisibilityCheckPipeline pipeline)
      throws InterruptedException {
    List<Account.Id> result = new ArrayList<>();
    try {
      Account.Id id;
      while ((id = pipeline.get()) != null) {
        result.add(id);
      }
    } finally {
      pipeline.stop();
    }
    return result;
  }

  private static ImmutableList<Account.Id> firstIds(int count) {
    return IntStream.range(0, count).mapToObj(Account::id).collect(toImmutableList());
  }

  private static ImmutableList<Account.Id> ids(int... values) {
    return IntStream.of(values).mapToObj(Account::id).collect(toImmutableList());
  }

  /** Records calls and lets tests block or fail checks for particular candidates. */
  private static class FakeCheck implements VisibilityCheckPipeline.CandidateCheck {
    private final IntPredicate visible;
    private final Map<Integer, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final Map<Integer, CountDownLatch> blockers = new ConcurrentHashMap<>();
    private final Map<Integer, CountDownLatch> started = new ConcurrentHashMap<>();
    private final Map<Integer, CountDownLatch> completed = new ConcurrentHashMap<>();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    private volatile ImmutableSet<Integer> throwFor = ImmutableSet.of();
    private volatile long delayMillis;

    FakeCheck(IntPredicate visible) {
      this.visible = visible;
    }

    CountDownLatch block(int id) {
      CountDownLatch latch = new CountDownLatch(1);
      blockers.put(id, latch);
      return latch;
    }

    void throwFor(int... ids) {
      throwFor = IntStream.of(ids).boxed().collect(ImmutableSet.toImmutableSet());
    }

    void setDelayMillis(long delayMillis) {
      this.delayMillis = delayMillis;
    }

    @Override
    public boolean isVisible(Account.Id accountId) throws Exception {
      int id = accountId.get();
      calls.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
      latch(started, id).countDown();
      int now = running.incrementAndGet();
      maxRunning.accumulateAndGet(now, Math::max);
      try {
        CountDownLatch blocker = blockers.get(id);
        if (blocker != null) {
          blocker.await();
        }
        if (delayMillis > 0) {
          Thread.sleep(delayMillis);
        }
        if (throwFor.contains(id)) {
          throw new IllegalStateException("check failed for " + id);
        }
        return visible.test(id);
      } finally {
        running.decrementAndGet();
        latch(completed, id).countDown();
      }
    }

    void awaitStarted(int id) throws InterruptedException {
      assertThat(latch(started, id).await(10, SECONDS)).isTrue();
    }

    void awaitCompleted(int id) throws InterruptedException {
      assertThat(latch(completed, id).await(10, SECONDS)).isTrue();
    }

    int totalChecks() {
      return calls.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    ImmutableList<Account.Id> checkedIds() {
      return calls.keySet().stream().sorted().map(Account::id).collect(toImmutableList());
    }

    ImmutableList<Integer> duplicateChecks() {
      return calls.entrySet().stream()
          .filter(e -> e.getValue().get() > 1)
          .map(Map.Entry::getKey)
          .sorted()
          .collect(toImmutableList());
    }

    int maxConcurrent() {
      return maxRunning.get();
    }

    private static CountDownLatch latch(Map<Integer, CountDownLatch> latches, int id) {
      return latches.computeIfAbsent(id, k -> new CountDownLatch(1));
    }
  }
}
