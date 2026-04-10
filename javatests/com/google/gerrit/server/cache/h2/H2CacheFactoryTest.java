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
import javax.annotation.Nullable;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class H2CacheFactoryTest {
  private static final TypeLiteral<String> STRING_TYPE = new TypeLiteral<>() {};

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void newSqlStore_setsUrl() throws Exception {
    Path cacheDir = temporaryFolder.newFolder("cache").toPath();
    H2CacheFactory factory =
        new H2CacheFactory(
            new NoOpMemoryCacheFactory(),
            new Config(),
            DynamicMap.emptyMap(),
            null,
            null,
            cacheDir,
            EnumSet.noneOf(CacheOptions.class),
            new AtomicBoolean(false));

    PersistentCacheDef<String, String> def = new SimplePersistentCacheDef("demo-cache");
    SqlStore<String, String> store = invokeNewSqlStore(factory, def, 1L);

    assertThat(readField(store, "url"))
        .isEqualTo(
            "jdbc:h2:file:"
                + cacheDir.resolve("demo-cache-v2").toAbsolutePath()
                + ";DB_CLOSE_DELAY=-1");
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

  private static class SimplePersistentCacheDef implements PersistentCacheDef<String, String> {
    private final String name;

    private SimplePersistentCacheDef(String name) {
      this.name = name;
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
      return 1;
    }

    @Override
    public int version() {
      return 1;
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
