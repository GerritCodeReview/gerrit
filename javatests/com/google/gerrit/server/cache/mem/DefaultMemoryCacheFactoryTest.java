// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.CacheDef;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class DefaultMemoryCacheFactoryTest {

  private static final long TEST_TIMEOUT_SEC = 1;
  private static final int TEST_CACHE_KEY = 1;

  private DefaultMemoryCacheFactory memoryCacheFactory;
  private ScheduledExecutorService executor;
  private CyclicBarrier cacheGetStarted;
  private CyclicBarrier cacheGetCompleted;

  @Before
  public void setUp() {
    memoryCacheFactory = new DefaultMemoryCacheFactory(new Config(), null);
    executor = Executors.newScheduledThreadPool(1);
    cacheGetStarted = new CyclicBarrier(2);
    cacheGetCompleted = new CyclicBarrier(2);
  }

  @Test
  public void shouldNotBlockEvictionsWhenCacheIsDisabled() throws Exception {
    LoadingCache<Integer, Integer> disabledCache =
        memoryCacheFactory.build(newCacheDef(0), newCacheLoader(k -> k * 2), CacheBackend.CAFFEINE);

    ScheduledFuture<Integer> cacheValue =
        executor.schedule(() -> disabledCache.getUnchecked(TEST_CACHE_KEY), 0, TimeUnit.SECONDS);
    cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    disabledCache.invalidate(TEST_CACHE_KEY);

    // The invalidate did not wait for the cache loader to finish, therefore the cacheValue isn't
    // done yet
    assertThat(cacheValue.isDone()).isFalse();

    // The cache loader completes after the invalidation
    cacheGetCompleted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertThat(cacheValue.get()).isEqualTo(TEST_CACHE_KEY * 2);
  }

  @Test
  public void shouldBlockEvictionsWhenCacheIsEnabled() throws Exception {
    LoadingCache<Integer, Integer> cache =
        memoryCacheFactory.build(newCacheDef(1), newCacheLoader(k -> k * 2), CacheBackend.CAFFEINE);

    ScheduledFuture<Integer> cacheValue =
        executor.schedule(() -> cache.getUnchecked(TEST_CACHE_KEY), 0, TimeUnit.SECONDS);

    cacheGetStarted.await(TEST_TIMEOUT_SEC, TimeUnit.SECONDS);
    cache.invalidate(TEST_CACHE_KEY);

    assertThat(cacheValue.isDone()).isTrue();
    assertThat(cacheValue.get()).isEqualTo(TEST_CACHE_KEY * 2);
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
    };
  }

  private CacheDef<Integer, Integer> newCacheDef(long maximumWeight) {
    return new CacheDef<Integer, Integer>() {

      @Override
      public String name() {
        return "test-cache";
      }

      @Override
      public String configKey() {
        return "test-cache";
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
