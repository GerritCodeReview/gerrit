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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.plugincontext.PluginContext;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
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
import org.junit.After;
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
  private CountDownLatch evictionReceived;
  private ForwardingRemovalTrackerListener forwardingRemovalListener;
  private WorkQueue workQueue;

  @Before
  public void setUp() {
    IdGenerator idGenerator = Guice.createInjector().getInstance(IdGenerator.class);
    workQueue =
        new WorkQueue(
            idGenerator,
            10,
            new DisabledMetricMaker(),
            new PluginMapContext<>(
                DynamicMap.emptyMap(), PluginContext.PluginMetrics.DISABLED_INSTANCE));
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
    evictionReceived = new CountDownLatch(1);
  }

  @After
  public void shutDown() {
    executor.close();
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

    assertThat(evictionReceived.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
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

  @Test
  public void shouldNotifyWhenInvalidatingAbsentEntry() {
    Cache<Integer, Integer> cache = memoryCacheFactoryDirectExecutor.build(newCacheDef(1));

    cache.invalidate(TEST_CACHE_KEY);

    assertExplicitRemoval(TEST_CACHE_KEY);
  }

  @Test
  @SuppressWarnings("IncompatibleArgumentType")
  public void shouldNotNotifyWhenInvalidatingWithWrongKeyType() {
    Cache<Integer, Integer> cache = memoryCacheFactoryDirectExecutor.build(newCacheDef(1));

    cache.invalidate("wrong key type");

    assertThat(forwardingRemovalListener.removalEvents).isEmpty();
  }

  @Test
  public void shouldStopBulkInvalidationAtNullKey() {
    int keyBeforeNull = 1;
    int keyAfterNull = 2;
    Cache<Integer, Integer> cache = memoryCacheFactoryDirectExecutor.build(newCacheDef(2));
    cache.put(keyBeforeNull, TEST_CACHE_VALUE);
    cache.put(keyAfterNull, TEST_CACHE_VALUE);

    assertThrows(
        NullPointerException.class,
        () -> cache.invalidateAll(Arrays.asList(keyBeforeNull, null, keyAfterNull)));

    assertThat(cache.getIfPresent(keyBeforeNull)).isNull();
    assertThat(cache.getIfPresent(keyAfterNull)).isEqualTo(TEST_CACHE_VALUE);
    assertThat(forwardingRemovalListener.removalEvents).hasSize(1);
    assertThat(forwardingRemovalListener.contains(keyBeforeNull, TEST_CACHE_VALUE)).isTrue();
  }

  @Test
  public void shouldNotifyWhenInvalidatingAbsentLoadingCacheEntry() {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactoryDirectExecutor.build(newCacheDef(1), newCacheLoader(identity()));

    cache.invalidate(TEST_CACHE_KEY);

    assertExplicitRemoval(TEST_CACHE_KEY);
  }

  @Test
  public void shouldNotifyWhenInvalidatingCacheWithZeroMemoryLimit() {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactoryDirectExecutor.build(newCacheDef(0), newCacheLoader(identity()));

    cache.invalidate(TEST_CACHE_KEY);

    assertExplicitRemoval(TEST_CACHE_KEY);
  }

  @Test
  public void shouldNotifyForPresentAndAbsentKeysInBulkInvalidation() {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactoryDirectExecutor.build(newCacheDef(1), newCacheLoader(identity()));
    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);

    cache.invalidateAll(List.of(TEST_CACHE_KEY, TEST_CACHE_KEY + 1));

    assertThat(forwardingRemovalListener.removalEvents).hasSize(2);
    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isTrue();
    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY + 1, null)).isTrue();
    for (RemovalNotification<Object, Object> notification :
        forwardingRemovalListener.removalEvents) {
      assertThat(notification.getCause()).isEqualTo(RemovalCause.EXPLICIT);
    }
  }

  @Test
  public void shouldContinueBulkInvalidationWhenExecutorIsShutDown() {
    Cache<Integer, Integer> cache = memoryCacheFactoryWithThreadPool.build(newCacheDef(1));
    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);
    workQueue.getExecutor(DefaultMemoryCacheFactory.CACHE_EXECUTOR_PREFIX).shutdown();

    cache.invalidateAll(List.of(TEST_CACHE_KEY + 1, TEST_CACHE_KEY));

    assertThat(cache.getIfPresent(TEST_CACHE_KEY)).isNull();
    assertThat(forwardingRemovalListener.removalEvents).hasSize(2);
    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY + 1, null)).isTrue();
    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isTrue();
    assertThat(forwardingRemovalListener.removalThreadName(TEST_CACHE_KEY + 1))
        .isEqualTo(Thread.currentThread().getName());
  }

  @Test
  public void shouldContinueBulkInvalidationWhenListenerThrows() {
    ForwardingRemovalTrackerListener failingListener =
        new ForwardingRemovalTrackerListener() {
          @Override
          public void onRemoval(RemovalNotification<Object, Object> notification) {
            super.onRemoval(notification);
            throw new IllegalStateException("Listener failed");
          }
        };
    DefaultMemoryCacheFactory factory =
        new DefaultMemoryCacheFactory(
            memoryCacheConfigDirectExecutor, cache -> failingListener, workQueue);
    Cache<Integer, Integer> cache = factory.build(newCacheDef(1));
    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);

    cache.invalidateAll(List.of(TEST_CACHE_KEY + 1, TEST_CACHE_KEY));

    assertThat(cache.getIfPresent(TEST_CACHE_KEY)).isNull();
    assertThat(failingListener.removalEvents).hasSize(2);
  }

  @Test
  public void shouldNotifyAbsentEntryInBackgroundByDefault() throws Exception {
    assertAbsentEntryNotificationThread(memoryCacheFactory, "ForkJoinPool");
  }

  @Test
  public void shouldNotifyAbsentEntryInConfiguredThreadPool() throws Exception {
    assertAbsentEntryNotificationThread(
        memoryCacheFactoryWithThreadPool, DefaultMemoryCacheFactory.CACHE_EXECUTOR_PREFIX);
  }

  private void assertAbsentEntryNotificationThread(
      DefaultMemoryCacheFactory factory, String threadName) throws Exception {
    Cache<Integer, Integer> cache = factory.build(newCacheDef(1));

    cache.invalidate(TEST_CACHE_KEY);

    assertThat(evictionReceived.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
    assertExplicitRemoval(TEST_CACHE_KEY);
    assertThat(forwardingRemovalListener.removalThreadName(TEST_CACHE_KEY)).startsWith(threadName);
  }

  private void assertExplicitRemoval(int key) {
    assertThat(forwardingRemovalListener.removalEvents).hasSize(1);
    RemovalNotification<Object, Object> notification =
        forwardingRemovalListener.removalEvents.iterator().next();
    assertThat(notification.getKey()).isEqualTo(key);
    assertThat(notification.getValue()).isNull();
    assertThat(notification.getCause()).isEqualTo(RemovalCause.EXPLICIT);
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
    private final Queue<RemovalNotification<Object, Object>> removalEvents;
    private final ConcurrentHashMap<Object, String> removalThreads;

    public ForwardingRemovalTrackerListener() {
      super(null, null);
      removalEvents = new ConcurrentLinkedQueue<>();
      removalThreads = new ConcurrentHashMap<>();
    }

    @Override
    public void onRemoval(RemovalNotification<Object, Object> notification) {
      removalEvents.add(notification);
      removalThreads.put(notification.getKey(), Thread.currentThread().getName());
      evictionReceived.countDown();
    }

    private boolean contains(Object key, Object value) {
      return removalEvents.stream()
          .anyMatch(n -> Objects.equals(n.getKey(), key) && Objects.equals(n.getValue(), value));
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
        return TypeLiteral.get(Integer.class);
      }

      @Override
      @Nullable
      public TypeLiteral<Integer> valueType() {
        return null;
      }

      @Override
      public long maximumWeight() {
        return maximumWeight;
      }

      @Override
      @Nullable
      public Duration expireAfterWrite() {
        return null;
      }

      @Override
      @Nullable
      public Duration expireFromMemoryAfterAccess() {
        return null;
      }

      @Override
      @Nullable
      public Duration refreshAfterWrite() {
        return null;
      }

      @Override
      @Nullable
      public Weigher<Integer, Integer> weigher() {
        return null;
      }

      @Override
      @Nullable
      public CacheLoader<Integer, Integer> loader() {
        return null;
      }
    };
  }
}
