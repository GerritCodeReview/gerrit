// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.util.concurrent;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.hash.Funnels;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;

public class ConcurrentBloomFilterTest {
  private static int NUM_THREADS = 100;
  private static int ITERATIONS = 1000;

  @Test
  public void build() {
    ConcurrentBloomFilter<Integer> filter =
        create(
            b -> {
              assertThat(b.getEstimatedSize()).isEqualTo(0);
              int s = 10;
              b.setEstimatedSize(s);
              assertThat(b.getEstimatedSize()).isEqualTo(s);
              b.buildPut(1);
              b.build();
            });
    filter.initIfNeeded();
    assertThat(filter.mightContain(1)).isTrue();
  }

  @Test
  public void initRunsBuilderOnce() {
    AtomicInteger cnt = new AtomicInteger();
    ConcurrentBloomFilter<Integer> filter =
        create(
            b -> {
              b.build();
              cnt.incrementAndGet();
            });
    assertThat(cnt.get()).isEqualTo(0);
    filter.initIfNeeded();
    assertThat(cnt.get()).isEqualTo(1);
    filter.initIfNeeded();
    assertThat(cnt.get()).isEqualTo(1);
  }

  @Test
  public void mightContainWorksWithoutInit() {
    ConcurrentBloomFilter<Integer> filter =
        new ConcurrentBloomFilter<>(Funnels.integerFunnel(), () -> {});
    filter.mightContain(1); // Passes if no exception
  }

  @Test
  public void putAndClear() {
    ConcurrentBloomFilter<Integer> filter = create(b -> b.build());
    filter.initIfNeeded();
    assertThat(filter.mightContain(1)).isFalse();
    filter.put(1);
    assertThat(filter.mightContain(1)).isTrue();
    filter.clear();
    assertThat(filter.mightContain(1)).isFalse();
  }

  @Test
  public void initIsConcurrent() {
    AtomicInteger buildCnt = new AtomicInteger();
    try (ConcurrentTest c = new ConcurrentTest(NUM_THREADS)) {
      c.builder =
          b -> {
            b.build();
            buildCnt.incrementAndGet();
          };
      c.setup(
          b -> {
            await(c.latch);
            b.initIfNeeded();
          });
      assertThat(buildCnt.get()).isEqualTo(0);
      c.latch.countDown();
      c.assertSuccess();
      assertThat(buildCnt.get()).isEqualTo(1);
    }
  }

  @Test
  public void mightContainsIsConcurrent() {
    try (ConcurrentTest c = new ConcurrentTest(NUM_THREADS)) {
      c.setup(b -> b.mightContain(1));
      c.assertSuccess();
    }
  }

  @Test
  public void putIsConcurrent() {
    try (ConcurrentTest c = new ConcurrentTest(NUM_THREADS)) {
      c.setup(b -> b.put(1));
      c.assertSuccess();
    }
  }

  @Test
  public void clearIsConcurrent() {
    try (ConcurrentTest c = new ConcurrentTest(NUM_THREADS)) {
      c.setup(b -> b.clear());
      c.assertSuccess();
    }
  }

  @Test
  public void initIsConcurrentWitMightContain() {
    try (ConcurrentTest c = new ConcurrentTest(2)) {
      c.run(b -> b.initIfNeeded(), b -> b.mightContain(1));
      c.assertSuccess();
    }
  }

  @Test
  public void initIsConcurrentWithPut() {
    try (ConcurrentTest c = new ConcurrentTest(2)) {
      c.run(b -> b.initIfNeeded(), b -> b.put(1));
      c.assertSuccess();
    }
  }

  @Test
  public void initIsConcurrentWithClear() {
    try (ConcurrentTest c = new ConcurrentTest(2)) {
      c.run(b -> b.initIfNeeded(), b -> b.clear());
      c.assertSuccess();
    }
  }

  @Test
  public void mightContansIsConcurrentWithPut() {
    try (ConcurrentTest c = new ConcurrentTest(2)) {
      c.run(b -> b.mightContain(1), b -> b.put(1));
      c.assertSuccess();
    }
  }

  @Test
  public void mightContansIsConcurrentWithClear() {
    try (ConcurrentTest c = new ConcurrentTest(2)) {
      c.run(b -> b.mightContain(1), b -> b.clear());
      c.assertSuccess();
    }
  }

  @Test
  public void putIsConcurrentWithClear() {
    try (ConcurrentTest c = new ConcurrentTest(2)) {
      c.run(b -> b.put(1), b -> b.clear());
      c.assertSuccess();
    }
  }

  private static class ConcurrentTest implements AutoCloseable {
    AtomicInteger success = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>(ITERATIONS * 2);
    Consumer<ConcurrentBloomFilter<Integer>> builder = b -> b.build();

    int threads;
    CountDownLatch latch;
    ExecutorService executor;
    ConcurrentBloomFilter<Integer> filter;

    ConcurrentTest(int threads) {
      this.threads = threads;
    }

    void init() {
      latch = new CountDownLatch(1);
      if (executor != null) {
        executor.shutdown();
      }
      executor = Executors.newFixedThreadPool(threads);
      filter = new ConcurrentBloomFilter<>(Funnels.integerFunnel(), () -> builder.accept(filter));
    }

    void setup(Consumer<ConcurrentBloomFilter<Integer>> consumer) {
      init();
      for (int i = 0; i < NUM_THREADS * 2; i++) {
        submit(consumer);
      }
      latch.countDown();
    }

    void run(
        Consumer<ConcurrentBloomFilter<Integer>> consumer1,
        Consumer<ConcurrentBloomFilter<Integer>> consumer2) {
      for (int i = 0; i < ITERATIONS; i++) {
        init();
        submit(consumer1);
        submit(consumer2);
        latch.countDown();
        futures.forEach(f -> get(f));
      }
    }

    private void submit(Consumer<ConcurrentBloomFilter<Integer>> consumer) {
      futures.add(
          executor.submit(
              () -> {
                boolean isSuccess = await(latch);
                consumer.accept(filter);
                if (isSuccess) {
                  success.incrementAndGet();
                }
              }));
    }

    void assertSuccess() {
      executor.shutdown();
      assertThat(futures.stream().map(f -> get(f)).reduce(true, (r, v) -> r && v)).isTrue();
      assertThat(success.get()).isEqualTo(futures.size());
    }

    @Override
    public void close() {
      if (executor != null) {
        executor.close();
      }
    }
  }

  private static ConcurrentBloomFilter<Integer> create(
      Consumer<ConcurrentBloomFilter<Integer>> builder) {
    AtomicReference<Runnable> buiderRef = new AtomicReference<>();
    ConcurrentBloomFilter<Integer> b =
        new ConcurrentBloomFilter<>(Funnels.integerFunnel(), () -> buiderRef.getPlain().run());
    buiderRef.setPlain(() -> builder.accept(b));
    return b;
  }

  private static boolean await(CountDownLatch latch) {
    try {
      return latch.await(100, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      return false;
    }
  }

  private static boolean get(Future<?> future) {
    try {
      future.get(100, TimeUnit.MILLISECONDS);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
