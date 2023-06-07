// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.cache.h2;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.junit.Test;

public class H2CacheTest {
  private static final TypeLiteral<String> KEY_TYPE = new TypeLiteral<String>() {};
  private static final int DEFAULT_VERSION = 1234;
  private static int dbCnt;

  private static int nextDbId() {
    return ++dbCnt;
  }

  private static H2CacheImpl<String, String> newH2CacheImpl(
      SqlStore<String, String> store, Cache<String, ValueHolder<String>> mem) {
    return new H2CacheImpl<>(MoreExecutors.directExecutor(), store, KEY_TYPE, mem);
  }

  private static SqlStore<String, String> newStore(
      int id,
      int version,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite) {
    return new SqlStore<>(
        "jdbc:h2:mem:Test_" + id,
        KEY_TYPE,
        StringCacheSerializer.INSTANCE,
        StringCacheSerializer.INSTANCE,
        version,
        1 << 20,
        expireAfterWrite,
        refreshAfterWrite,
        true);
  }

  @Test
  public void get() throws ExecutionException {
    Cache<String, ValueHolder<String>> mem = CacheBuilder.newBuilder().build();
    H2CacheImpl<String, String> impl =
        newH2CacheImpl(newStore(nextDbId(), DEFAULT_VERSION, null, null), mem);

    assertThat(impl.getIfPresent("foo")).isNull();

    AtomicBoolean called = new AtomicBoolean();
    assertThat(
            impl.get(
                "foo",
                () -> {
                  called.set(true);
                  return "bar";
                }))
        .isEqualTo("bar");
    assertWithMessage("Callable was called").that(called.get()).isTrue();
    assertWithMessage("in-memory value").that(impl.getIfPresent("foo")).isEqualTo("bar");
    mem.invalidate("foo");
    assertWithMessage("persistent value").that(impl.getIfPresent("foo")).isEqualTo("bar");

    called.set(false);
    assertWithMessage("cached value")
        .that(
            impl.get(
                "foo",
                () -> {
                  called.set(true);
                  return "baz";
                }))
        .isEqualTo("bar");
    assertWithMessage("Callable was called").that(called.get()).isFalse();
  }

  @Test
  public void getAll_WithLoadingCache_LoaderNotImplementingLoadAll() throws ExecutionException {
    Cache<String, ValueHolder<String>> mem =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, ValueHolder<String>>() {
                  @Override
                  public ValueHolder<String> load(String s) throws Exception {
                    return new ValueHolder<>(s + "_loaded", Instant.now());
                  }
                });

    H2CacheImpl<String, String> impl =
        newH2CacheImpl(newStore(nextDbId(), DEFAULT_VERSION, null, null), mem);

    assertThat(impl.getAll(Arrays.asList("S1", "S2")))
        .containsExactlyEntriesIn(ImmutableMap.of("S1", "S1_loaded", "S2", "S2_loaded"));

    // Make sure the values were cached
    assertWithMessage("in-memory value").that(impl.getIfPresent("S1")).isEqualTo("S1_loaded");
    assertWithMessage("in-memory value").that(impl.getIfPresent("S2")).isEqualTo("S2_loaded");
  }

  @Test
  public void getAll_WithLoadingCache_LoaderImplementingLoadAll() throws ExecutionException {
    Cache<String, ValueHolder<String>> mem =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, ValueHolder<String>>() {
                  @Override
                  public ValueHolder<String> load(String s) throws Exception {
                    return new ValueHolder<>(s + "_loaded", Instant.now());
                  }

                  @Override
                  public Map<String, ValueHolder<String>> loadAll(Iterable<? extends String> keys)
                      throws Exception {
                    Map<String, ValueHolder<String>> result = new HashMap<>();
                    for (String k : keys) {
                      result.put(k, load(k));
                    }
                    return result;
                  }
                });

    H2CacheImpl<String, String> impl =
        newH2CacheImpl(newStore(nextDbId(), DEFAULT_VERSION, null, null), mem);

    assertThat(impl.getAll(Arrays.asList("S1", "S2")))
        .containsExactlyEntriesIn(ImmutableMap.of("S1", "S1_loaded", "S2", "S2_loaded"));

    // Make sure the values were cached
    assertWithMessage("in-memory value").that(impl.getIfPresent("S1")).isEqualTo("S1_loaded");
    assertWithMessage("in-memory value").that(impl.getIfPresent("S2")).isEqualTo("S2_loaded");
  }

  @Test
  public void stringSerializer() {
    String input = "foo";
    byte[] serialized = StringCacheSerializer.INSTANCE.serialize(input);
    assertThat(serialized).isEqualTo(new byte[] {'f', 'o', 'o'});
    assertThat(StringCacheSerializer.INSTANCE.deserialize(serialized)).isEqualTo(input);
  }

  @Test
  public void version() {
    int id = nextDbId();
    H2CacheImpl<String, String> oldImpl =
        newH2CacheImpl(newStore(id, DEFAULT_VERSION, null, null), disableMemCache());
    H2CacheImpl<String, String> newImpl =
        newH2CacheImpl(newStore(id, DEFAULT_VERSION + 1, null, null), disableMemCache());

    assertThat(oldImpl.diskStats().space()).isEqualTo(0);
    assertThat(newImpl.diskStats().space()).isEqualTo(0);
    oldImpl.put("key", "val");
    assertThat(oldImpl.getIfPresent("key")).isEqualTo("val");
    assertThat(oldImpl.diskStats().space()).isEqualTo(12);
    assertThat(oldImpl.diskStats().hitCount()).isEqualTo(1);

    // Can't find key in cache with wrong version, but the data is still there.
    assertThat(newImpl.diskStats().requestCount()).isEqualTo(0);
    assertThat(newImpl.diskStats().space()).isEqualTo(12);
    assertThat(newImpl.getIfPresent("key")).isNull();
    assertThat(newImpl.diskStats().space()).isEqualTo(12);

    // Re-putting it via the new cache works, and uses the same amount of space.
    newImpl.put("key", "val2");
    assertThat(newImpl.getIfPresent("key")).isEqualTo("val2");
    assertThat(newImpl.diskStats().hitCount()).isEqualTo(1);
    assertThat(newImpl.diskStats().space()).isEqualTo(14);

    // Now it's no longer in the old cache.
    assertThat(oldImpl.diskStats().space()).isEqualTo(14);
    assertThat(oldImpl.getIfPresent("key")).isNull();
  }

  @Test
  public void refreshAfterWrite_triggeredWhenConfigured() throws Exception {
    SqlStore<String, String> store =
        newStore(nextDbId(), DEFAULT_VERSION, null, Duration.ofMillis(10));

    // This is the loader that we configure for the cache when calling .loader(...)
    @SuppressWarnings("unchecked")
    CacheLoader<String, String> baseLoader = mock(CacheLoader.class);
    resetLoaderAndAnswerLoadAndRefreshCalls(baseLoader);

    // We wrap baseLoader just like H2CacheFactory is wrapping it. The wrapped version will call out
    // to the store for refreshing values.
    H2CacheImpl.Loader<String, String> wrappedLoader =
        new H2CacheImpl.Loader<>(MoreExecutors.directExecutor(), store, baseLoader);
    // memCache is the in-memory variant of the cache. Its loader is wrappedLoader which will call
    // out to the store to save or delete cached values.
    LoadingCache<String, ValueHolder<String>> memCache =
        CacheBuilder.newBuilder().maximumSize(10).build(wrappedLoader);

    // h2Cache puts it all together
    H2CacheImpl<String, String> h2Cache = newH2CacheImpl(store, memCache);

    // Initial load and cache retrieval do not trigger refresh
    // This works because we use a directExecutor() for refreshes
    TimeUtil.setCurrentMillisSupplier(() -> 0);
    assertThat(h2Cache.get("foo")).isEqualTo("load:foo");
    verify(baseLoader).load("foo");
    assertThat(h2Cache.get("foo")).isEqualTo("load:foo");
    verifyNoMoreInteractions(baseLoader);
    resetLoaderAndAnswerLoadAndRefreshCalls(baseLoader);

    // Load after refresh duration returns old value, triggers refresh and returns new value
    TimeUtil.setCurrentMillisSupplier(() -> 11);
    assertThat(h2Cache.get("foo")).isEqualTo("load:foo");
    assertThat(h2Cache.get("foo")).isEqualTo("reload:foo");
    verify(baseLoader).reload("foo", "load:foo");
    verifyNoMoreInteractions(baseLoader);
    resetLoaderAndAnswerLoadAndRefreshCalls(baseLoader);

    // Refreshed value was persisted
    memCache.invalidateAll(); // Invalidates only the memcache, not the store.
    assertThat(h2Cache.getIfPresent("foo")).isEqualTo("reload:foo");
  }

  @SuppressWarnings("unchecked")
  private static void resetLoaderAndAnswerLoadAndRefreshCalls(CacheLoader<String, String> loader)
      throws Exception {
    reset(loader);
    when(loader.load("foo")).thenReturn("load:foo");
    when(loader.reload("foo", "load:foo")).thenReturn(Futures.immediateFuture("reload:foo"));
  }

  private static <K, V> Cache<K, ValueHolder<V>> disableMemCache() {
    return CacheBuilder.newBuilder().maximumSize(0).build();
  }
}
