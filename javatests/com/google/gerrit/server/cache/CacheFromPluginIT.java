// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ModuleImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

@TestPlugin(
    name = "cache-from-plugin",
    sysModule = "com.google.gerrit.server.cache.CacheFromPluginIT$Module")
public class CacheFromPluginIT extends LightweightPluginDaemonTest {

  @ModuleImpl(name = CacheModule.PERSISTENT_MODULE)
  public static class Module extends AbstractModule {

    @Override
    protected void configure() {}
  }

  @Test
  @Sandboxed
  public void shouldBindH2CacheFactoryByDefault() {
    assertThat(getPersistentCacheFactory().get().getClass().getSimpleName())
        .isEqualTo("H2CacheFactory");
  }

  @Test
  @Sandboxed
  public void shouldBeReplaceableByADifferentCache() throws RestApiException {
    getPersistentCacheFactory().set(new TestCacheFactory(), "gerrit");
    assertThat(getPersistentCacheFactory().get()).isInstanceOf(TestCacheFactory.class);
    // TODO: assert test cache gets hit
    //    gApi.projects().create("TestProject");
  }

  private DynamicItem<PersistentCacheFactory> getPersistentCacheFactory() {
    return plugin.getSysInjector().getInstance(new Key<DynamicItem<PersistentCacheFactory>>() {});
  }

  public static class TestCache<K, V> extends AbstractLoadingCache<K, V>
      implements PersistentCache {

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final Map<K, V> cache;

    @Inject
    TestCache() {
      cache = new ConcurrentHashMap<>();
    }

    @Override
    public V get(K k) throws ExecutionException {
      if (!cache.containsKey(k)) {
        throw new ExecutionException(new Exception(String.format("key %s not found in cache", k)));
      }
      return cache.get(k);
    }

    @Override
    public V getIfPresent(Object o) {
      if (cache.containsKey(o)) {
        hitCount.incrementAndGet();
      } else {
        missCount.incrementAndGet();
      }
      return cache.get(o);
    }

    @Override
    public void put(K k, V v) {
      cache.put(k, v);
    }

    @Override
    public DiskStats diskStats() {
      return new DiskStats(cache.size(), cache.size(), hitCount.get(), missCount.get());
    }
  }

  public static class TestCacheFactory implements PersistentCacheFactory {

    @Override
    public <K, V> com.google.common.cache.Cache<K, V> build(
        PersistentCacheDef<K, V> def, CacheBackend backend) {
      return new TestCache<>();
    }

    @Override
    public <K, V> LoadingCache<K, V> build(
        PersistentCacheDef<K, V> def, CacheLoader<K, V> loader, CacheBackend backend) {
      return new TestCache<>();
    }

    @Override
    public void onStop(String plugin) {}
  }
}
