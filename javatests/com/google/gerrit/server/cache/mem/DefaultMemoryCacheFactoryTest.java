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
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
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
import org.apache.mina.util.ConcurrentHashSet;
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
  private Config memoryCacheConfig;
  private Config memoryCacheConfigDirectExecutor;
  private ScheduledExecutorService executor;
  private CyclicBarrier cacheGetStarted;
  private CyclicBarrier cacheGetCompleted;
  private ForwardingRemovalTrackerListener forwardingRemovalListener;

  @Before
  public void setUp() {
    memoryCacheConfig = new Config();
    memoryCacheConfigDirectExecutor = new Config();
    memoryCacheConfigDirectExecutor.setBoolean("cache", null, "directExecutor", true);

    forwardingRemovalListener = new ForwardingRemovalTrackerListener();
    memoryCacheFactory =
        new DefaultMemoryCacheFactory(memoryCacheConfig, (cache) -> forwardingRemovalListener);
    memoryCacheFactoryDirectExecutor =
        new DefaultMemoryCacheFactory(
            memoryCacheConfigDirectExecutor, (cache) -> forwardingRemovalListener);
    executor = Executors.newScheduledThreadPool(1);
    cacheGetStarted = new CyclicBarrier(2);
    cacheGetCompleted = new CyclicBarrier(2);
  }

  public static class ForwardingRemovalTrackerListener
      extends ForwardingRemovalListener<Object, Object> {
    private ConcurrentHashMap<Object, Set<Object>> removalEvents;

    public ForwardingRemovalTrackerListener() {
      super(null, null);

      removalEvents = new ConcurrentHashMap<>();
    }

    @Override
    public void onRemoval(RemovalNotification<Object, Object> notification) {
      Set<Object> setOfValues =
          removalEvents.computeIfAbsent(
              notification.getKey(),
              (key) -> {
                Set<Object> elements = new ConcurrentHashSet<>();
                return elements;
              });
      setOfValues.add(notification.getValue());
    }

    public boolean contains(Object key, Object value) {
      return Optional.ofNullable(removalEvents.get(key))
          .map(sv -> sv.contains(value))
          .orElse(false);
    }
  }

  @Test
  public void shouldNotBlockEvictionsWhenCacheIsDisabledByDefault() throws Exception {
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(0), newCacheLoader(identity()), CacheBackend.CAFFEINE);

    assertCacheEvictionIsNotBlocking(disabledCache);
  }

  @Test
  public void shouldRunEvictionListenerInBackgroundByDefault() throws Exception {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactory.build(newCacheDef(1), newCacheLoader(identity()), CacheBackend.CAFFEINE);

    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);
    cache.invalidate(TEST_CACHE_KEY);

    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isFalse();
  }

  @Test
  public void shouldRunEvictionListenerWithDirectExecutor() throws Exception {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactoryDirectExecutor.build(
            newCacheDef(1), newCacheLoader(identity()), CacheBackend.CAFFEINE);

    cache.put(TEST_CACHE_KEY, TEST_CACHE_VALUE);
    cache.invalidate(TEST_CACHE_KEY);

    assertThat(forwardingRemovalListener.contains(TEST_CACHE_KEY, TEST_CACHE_VALUE)).isTrue();
  }

  @Test
  public void shouldNotBlockEvictionsWhenCacheIsDisabledByConfiguration() throws Exception {
    memoryCacheConfig.setInt("cache", TEST_CACHE, "memoryLimit", 0);
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(1), newCacheLoader(identity()), CacheBackend.CAFFEINE);

    assertCacheEvictionIsNotBlocking(disabledCache);
  }

  @Test
  public void shouldBlockEvictionsWhenCacheIsEnabled() throws Exception {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactory.build(newCacheDef(1), newCacheLoader(identity()), CacheBackend.CAFFEINE);

    ScheduledFuture<Integer> cacheValue =
        executor.schedule(() -> cache.getUnchecked(TEST_CACHE_KEY), 0, TimeUnit.SECONDS);

    cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    cache.invalidate(TEST_CACHE_KEY);

    assertThat(cacheValue.isDone()).isTrue();
    assertThat(cacheValue.get()).isEqualTo(TEST_CACHE_KEY);
  }

  @Test
  public void shouldLoadAllKeysWithDisabledCache() throws Exception {
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(0), newCacheLoader(identity()), CacheBackend.CAFFEINE);

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
    return new CacheLoader<Integer, Integer>() {

      @Override
      public Integer load(Integer n) throws Exception {
        Integer v = 0;
        try {
          cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
          v = loadFunc.apply(n);
          cacheGetCompleted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException | BrokenBarrierException e) {
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

  private CacheDef<Integer, Integer> newCacheDef(long maximumWeight) {
    return new CacheDef<Integer, Integer>() {

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
