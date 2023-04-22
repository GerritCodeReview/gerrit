// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.cache.mem;

import static com.google.common.base.Functions.identity;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class DefaultMemoryCacheFactoryTest {

  private static final String TEST_CACHE = "test-cache";
  private static final long TEST_TIMEOUT_SEC = 1;
  private static final int TEST_CACHE_KEY = 1;
  private static final int TEST_CACHE_VALUE = 2;

  private DefaultMemoryCacheFactory memoryCacheFactory;
  private DefaultMemoryCacheFactory memoryCacheFactoryDirectExecutor;
  private DefaultMemoryCacheFactory memoryCacheFactoryWithThreadPool;
  private Config memoryCacheConfig;
  private ScheduledExecutorService executor;
  private Config memoryCacheConfigDirectExecutor;
  private Config memoryCacheConfigWithThreadPool;
  private CyclicBarrier cacheGetStarted;
  private CyclicBarrier cacheGetCompleted;
  private CyclicBarrier evictionReceived;
  private ForwardingRemovalTrackerListener forwardingRemovalListener;
  private WorkQueue workQueue;

  @Before
  public void setUp() {
    IdGenerator idGenerator = Guice.createInjector().getInstance(IdGenerator.class);
    workQueue = new WorkQueue(idGenerator, 10, new DisabledMetricMaker());
    memoryCacheConfig = new Config();
    memoryCacheConfigDirectExecutor = new Config();
    memoryCacheConfigDirectExecutor.setInt("cache", null, "threads", 0);
    memoryCacheConfigWithThreadPool = new Config();
    memoryCacheConfigWithThreadPool.setInt("cache", null, "threads", 1);
    forwardingRemovalListener = new ForwardingRemovalTrackerListener();
    memoryCacheFactory =
        new DefaultMemoryCacheFactory(
            memoryCacheConfig, (cache) -> forwardingRemovalListener, workQueue);
    memoryCacheFactoryDirectExecutor =
        new DefaultMemoryCacheFactory(
            memoryCacheConfigDirectExecutor, (cache) -> forwardingRemovalListener, workQueue);
    memoryCacheFactoryWithThreadPool =
        new DefaultMemoryCacheFactory(
            memoryCacheConfigWithThreadPool, (cache) -> forwardingRemovalListener, workQueue);
    executor = Executors.newScheduledThreadPool(1);
    cacheGetStarted = new CyclicBarrier(2);
    cacheGetCompleted = new CyclicBarrier(2);
    evictionReceived = new CyclicBarrier(2);
  }

  @Test
  public void shouldNotBlockEvictionsWhenCacheIsDisabledByDefault() throws Exception {
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(0), newCacheLoader(identity()));

    assertCacheEvictionIsNotBlocking(disabledCache);
  }

  @Test
  public void shouldNotBlockEvictionsWhenCacheIsDisabledByConfiguration() throws Exception {
    memoryCacheConfig.setInt("cache", TEST_CACHE, "memoryLimit", 0);
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(1), newCacheLoader(identity()));

    assertCacheEvictionIsNotBlocking(disabledCache);
  }

  @Test
  public void shouldBlockEvictionsWhenCacheIsEnabled() throws Exception {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactory.build(newCacheDef(1), newCacheLoader(identity()));

    ScheduledFuture<Integer> cacheValue =
        executor.schedule(() -> cache.getUnchecked(TEST_CACHE_KEY), 0, TimeUnit.SECONDS);

    cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    cache.invalidate(TEST_CACHE_KEY);

    assertThat(cacheValue.isDone()).isTrue();
    assertThat(cacheValue.get()).isEqualTo(TEST_CACHE_KEY);
  }

  @Test
  public void shouldRunEvictionListenerInBackgroundByDefault() throws Exception {
    shouldRunEvictionListenerInThreadPool(memoryCacheFactory, "ForkJoinPool");
  }

  @Test
  public void shouldRunEvictionListenerInThreadPool() throws Exception {
    shouldRunEvictionListenerInThreadPool(
        memoryCacheFactoryWithThreadPool, DefaultMemoryCacheFactory.CACHE_EXECUTOR_PREFIX);
  }

  private void shouldRunEvictionListenerInThreadPool(
      DefaultMemoryCacheFactory cacheFactory, String threadPoolPrefix) throws Exception {
    LoadingCache<Integer, Integer> cache =
        cacheFactory.build(newCacheDef(1), newCacheLoader(identity()));

    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);

    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isFalse();
    cache.invalidate(TEST_CACHE_KEY);

    evictionReceived.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isTrue();
    assertThat(forwardingRemovalListener.removalThreadName(TEST_CACHE_KEY))
        .startsWith(threadPoolPrefix);
  }

  @Test
  public void shouldRunEvictionListenerWithDirectExecutor() throws Exception {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactoryDirectExecutor.build(newCacheDef(1), newCacheLoader(identity()));

    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);
    cache.invalidate(TEST_CACHE_KEY);

    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isTrue();
  }

  @Test
  public void shouldLoadAllKeysWithDisabledCache() throws Exception {
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(0), newCacheLoader(identity()));

    List<Integer> keys = Arrays.asList(1, 2);
    ImmutableMap<Integer, Integer> entries = disabledCache.getAll(keys);

    assertThat(entries).containsExactly(1, 1, 2, 2);
  }

  private void assertCacheEvictionIsNotBlocking(LoadingCache<Integer, Integer> disabledCache)
      throws InterruptedException, BrokenBarrierException, TimeoutException, ExecutionException {
    ScheduledFuture<Integer> cacheValue =
        executor.schedule(() -> disabledCache.getUnchecked(TEST_CACHE_KEY), 0, TimeUnit.SECONDS);
    cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    disabledCache.invalidate(TEST_CACHE_KEY);

    // The invalidate did not wait for the cache loader to finish, therefore the cacheValue isn't
    // done yet
    assertThat(cacheValue.isDone()).isFalse();

    // The cache loader completes after the invalidation
    cacheGetCompleted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertThat(cacheValue.get()).isEqualTo(TEST_CACHE_KEY);
  }

  private CacheLoader<Integer, Integer> newCacheLoader(Function<Integer, Integer> loadFunc) {
    return new CacheLoader<>() {

      @Override
      public Integer load(Integer n) throws Exception {
        Integer v = 0;
        try {
          cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
          v = loadFunc.apply(n);
          cacheGetCompleted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException | BrokenBarrierException e) {
          // Just continue
        }
        return v;
      }

      @Override
      public Map<Integer, Integer> loadAll(Iterable<? extends Integer> keys) throws Exception {
        return StreamSupport.stream(keys.spliterator(), false)
            .collect(Collectors.toMap(identity(), identity()));
      }
    };
  }

  private class ForwardingRemovalTrackerListener extends ForwardingRemovalListener<Object, Object> {
    private final ConcurrentHashMap<Object, Set<Object>> removalEvents;
    private final ConcurrentHashMap<Object, String> removalThreads;

    public ForwardingRemovalTrackerListener() {
      super(null, null);

      removalEvents = new ConcurrentHashMap<>();
      removalThreads = new ConcurrentHashMap<>();
    }

    @Override
    public void onRemoval(RemovalNotification<Object, Object> notification) {
      Set<Object> setOfValues =
          removalEvents.computeIfAbsent(
              notification.getKey(),
              (key) -> {
                Set<Object> elements = ConcurrentHashMap.newKeySet();
                return elements;
              });
      setOfValues.add(notification.getValue());

      removalThreads.put(notification.getKey(), Thread.currentThread().getName());

      try {
        evictionReceived.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
      } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
        throw new IllegalStateException(e);
      }
    }

    private boolean contains(Object key, Object value) {
      return Optional.ofNullable(removalEvents.get(key))
          .map(sv -> sv.contains(value))
          .orElse(false);
    }

    private String removalThreadName(Object key) {
      return removalThreads.get(key);
    }
  }

  private CacheDef<Integer, Integer> newCacheDef(long maximumWeight) {
    return new CacheDef<>() {

      @Override
      public String name() {
        return TEST_CACHE;
      }

      @Override
      public String configKey() {
        return TEST_CACHE;
      }

      @Override
      public TypeLiteral<Integer> keyType() {
        return null;
      }

      @Override
      public TypeLiteral<Integer> valueType() {
        return null;
      }

      @Override
      public long maximumWeight() {
        return maximumWeight;
      }

      @Override
      public Duration expireAfterWrite() {
        return null;
      }

      @Override
      public Duration expireFromMemoryAfterAccess() {
        return null;
      }

      @Override
      public Duration refreshAfterWrite() {
        return null;
      }

      @Override
      public Weigher<Integer, Integer> weigher() {
        return null;
      }

      @Override
      public CacheLoader<Integer, Integer> loader() {
        return null;
      }
    };
  }
}
