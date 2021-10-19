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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.server.ModuleImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.junit.Test;

public class PersistentCacheFactoryIT extends AbstractDaemonTest {

  @Inject PersistentCacheFactory persistentCacheFactory;

  @ModuleImpl(name = CacheModule.PERSISTENT_MODULE)
  public static class TestModule extends AbstractModule {

    @Override
    protected void configure() {
      bind(PersistentCacheFactory.class).to(TestCacheFactory.class);
    }
  }

  @Override
  public com.google.inject.Module createModule() {
    return new TestModule();
  }

  @Test
  public void shouldH2PersistentCacheBeReplaceableByADifferentCacheImplementation() {
    assertThat(persistentCacheFactory).isInstanceOf(TestCacheFactory.class);
  }

  public static class TestCacheFactory implements PersistentCacheFactory {

    private final MemoryCacheFactory memoryCacheFactory;

    @Inject
    TestCacheFactory(MemoryCacheFactory memoryCacheFactory) {
      this.memoryCacheFactory = memoryCacheFactory;
    }

    @Override
    public <K, V> com.google.common.cache.Cache<K, V> build(
        PersistentCacheDef<K, V> def, CacheBackend backend) {
      return memoryCacheFactory.build(def, backend);
    }

    @Override
    public <K, V> LoadingCache<K, V> build(
        PersistentCacheDef<K, V> def, CacheLoader<K, V> loader, CacheBackend backend) {
      return memoryCacheFactory.build(def, loader, backend);
    }

    @Override
    public void onStop(String plugin) {}
  }
}
