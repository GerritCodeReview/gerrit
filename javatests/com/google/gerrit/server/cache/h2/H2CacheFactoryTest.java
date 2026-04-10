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

package com.google.gerrit.server.cache.h2;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class H2CacheFactoryTest {
  private static final TypeLiteral<String> STRING_TYPE = new TypeLiteral<>() {};

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void newSqlStore_buildsExpectedUrlAndTimings() throws Exception {
    Config cfg = new Config();
    cfg.setLong("cache", null, "h2CacheSize", 2048);
    cfg.setBoolean("cache", null, "h2AutoServer", true);
    cfg.setString("cache", "test-cache", "refreshAfterWrite", "5s");
    cfg.setString("cache", "test-cache", "maxAge", "7s");
    cfg.setInt("cache", null, "h2MaxInvalidated", 44);

    Path cacheDir = temporaryFolder.newFolder("cache;dir").toPath();
    H2CacheFactory factory =
        new H2CacheFactory(
            new NoOpMemoryCacheFactory(),
            cfg,
            DynamicMap.emptyMap(),
            null,
            null,
            cacheDir,
            EnumSet.of(H2CacheFactory.CacheOptions.BUILD_BLOOM_FILTER),
            new AtomicBoolean(false));

    PersistentCacheDef<String, String> def =
        new TestPersistentCacheDef(
            "test-cache", Duration.ofSeconds(1), Duration.ofSeconds(2), 23, 99L);

    SqlStore<String, String> store = invokeNewSqlStore(factory, def, 99L);

    assertThat(readField(store, "url"))
        .isEqualTo(
            "jdbc:h2:file:"
                + cacheDir.resolve("test-cache-v2").toAbsolutePath().toString().replace(";", "\\;")
                + ";CACHE_SIZE=2;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
    assertThat(readField(store, "maxSize")).isEqualTo(99L);
    assertThat(readField(store, "version")).isEqualTo(23);
    assertThat(readField(store, "expireAfterWrite")).isEqualTo(Duration.ofSeconds(7));
    assertThat(readField(store, "refreshAfterWrite")).isEqualTo(Duration.ofSeconds(5));
    assertThat(readField(store, "buildBloomFilter")).isEqualTo(true);
    assertThat(readField(store, "trackLastAccess")).isEqualTo(false);
  }

  @Test
  public void newSqlStore_usesDefinitionDefaultsWhenConfigDoesNotOverride() throws Exception {
    Path cacheDir = temporaryFolder.newFolder("cache").toPath();
    H2CacheFactory factory =
        new H2CacheFactory(
            new NoOpMemoryCacheFactory(),
            new Config(),
            DynamicMap.emptyMap(),
            null,
            null,
            cacheDir,
            EnumSet.of(H2CacheFactory.CacheOptions.TRACK_LAST_ACCESS),
            new AtomicBoolean(true));

    PersistentCacheDef<String, String> def =
        new TestPersistentCacheDef(
            "test-cache", Duration.ofSeconds(11), Duration.ofSeconds(13), 7, 99L);

    SqlStore<String, String> store = invokeNewSqlStore(factory, def, 99L);

    assertThat(readField(store, "url"))
        .isEqualTo(
            "jdbc:h2:file:"
                + cacheDir.resolve("test-cache-v2").toAbsolutePath()
                + ";DB_CLOSE_DELAY=-1");
    assertThat(readField(store, "expireAfterWrite")).isEqualTo(Duration.ofSeconds(11));
    assertThat(readField(store, "refreshAfterWrite")).isEqualTo(Duration.ofSeconds(13));
    assertThat(readField(store, "buildBloomFilter")).isEqualTo(false);
    assertThat(readField(store, "trackLastAccess")).isEqualTo(true);
    assertThat(readField(store, "isDiskCacheReadOnly")).isEqualTo(new AtomicBoolean(true));
  }

  @SuppressWarnings("unchecked")
  private static SqlStore<String, String> invokeNewSqlStore(
      H2CacheFactory factory, PersistentCacheDef<String, String> def, long maxSize)
      throws Exception {
    Method method =
        H2CacheFactory.class.getDeclaredMethod("newSqlStore", PersistentCacheDef.class, long.class);
    method.setAccessible(true);
    return (SqlStore<String, String>) method.invoke(factory, def, maxSize);
  }

  private static Object readField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private static class NoOpMemoryCacheFactory implements MemoryCacheFactory {
    @Override
    public <K, V> Cache<K, V> build(CacheDef<K, V> def) {
      return CacheBuilder.newBuilder().build();
    }

    @Override
    public <K, V> LoadingCache<K, V> build(CacheDef<K, V> def, CacheLoader<K, V> loader) {
      return CacheBuilder.newBuilder().build(loader);
    }
  }

  private static class TestPersistentCacheDef implements PersistentCacheDef<String, String> {
    private final String name;
    @Nullable private final Duration expireAfterWrite;
    @Nullable private final Duration refreshAfterWrite;
    private final int version;
    private final long diskLimit;

    private TestPersistentCacheDef(
        String name,
        @Nullable Duration expireAfterWrite,
        @Nullable Duration refreshAfterWrite,
        int version,
        long diskLimit) {
      this.name = name;
      this.expireAfterWrite = expireAfterWrite;
      this.refreshAfterWrite = refreshAfterWrite;
      this.version = version;
      this.diskLimit = diskLimit;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String configKey() {
      return name;
    }

    @Override
    public TypeLiteral<String> keyType() {
      return STRING_TYPE;
    }

    @Override
    public TypeLiteral<String> valueType() {
      return STRING_TYPE;
    }

    @Override
    public long maximumWeight() {
      return 0;
    }

    @Override
    @Nullable
    public Duration expireAfterWrite() {
      return expireAfterWrite;
    }

    @Override
    @Nullable
    public Duration expireFromMemoryAfterAccess() {
      return null;
    }

    @Override
    @Nullable
    public Duration refreshAfterWrite() {
      return refreshAfterWrite;
    }

    @Override
    @Nullable
    public Weigher<String, String> weigher() {
      return null;
    }

    @Override
    @Nullable
    public CacheLoader<String, String> loader() {
      return null;
    }

    @Override
    public long diskLimit() {
      return diskLimit;
    }

    @Override
    public int version() {
      return version;
    }

    @Override
    public CacheSerializer<String> keySerializer() {
      return StringCacheSerializer.INSTANCE;
    }

    @Override
    public CacheSerializer<String> valueSerializer() {
      return StringCacheSerializer.INSTANCE;
    }
  }
}
