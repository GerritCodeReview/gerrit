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

package com.google.gerrit.server.cache.h2;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.CacheSpec;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CacheSpecTest {
  private static final String A = "name_a";

  @Rule public final TemporaryFolder tempDir = new TemporaryFolder();

  @Test
  public void defaultCacheSpec() {
    assertThat(
            getCacheSpec(
                new Config(),
                new CacheModule() {
                  @Override
                  public void configure() {
                    cache(A, String.class, String.class);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().build());
  }

  @Test
  public void overrideMaximumWeightInBinding() {
    assertThat(
            getCacheSpec(
                new Config(),
                new CacheModule() {
                  @Override
                  public void configure() {
                    cache(A, String.class, String.class).maximumWeight(512);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().maximumWeight(512).build());
  }

  @Test
  public void overrideMaximumWeightInConfig() {
    Config cfg = new Config();
    cfg.setLong("cache", A, "memoryLimit", 512);
    assertThat(
            getCacheSpec(
                cfg,
                new CacheModule() {
                  @Override
                  public void configure() {
                    cache(A, String.class, String.class);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().maximumWeight(512).build());
  }

  @Test
  public void overrideMaximumWeightPrefersConfig() {
    Config cfg = new Config();
    cfg.setLong("cache", A, "memoryLimit", 512);
    assertThat(
            getCacheSpec(
                cfg,
                new CacheModule() {
                  @Override
                  public void configure() {
                    cache(A, String.class, String.class).maximumWeight(3);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().maximumWeight(512).build());
  }

  @Test
  public void overrideDiskLimitInBinding() {
    assertThat(
            getCacheSpec(
                new Config(),
                new CacheModule() {
                  @Override
                  public void configure() {
                    persist(A, String.class, String.class).diskLimit(512);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().diskLimit(512).build());
  }

  @Test
  public void overrideDiskLimitInConfig() {
    Config cfg = new Config();
    cfg.setLong("cache", A, "diskLimit", 512);
    assertThat(
            getCacheSpec(
                cfg,
                new CacheModule() {
                  @Override
                  public void configure() {
                    persist(A, String.class, String.class);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().diskLimit(512).build());
  }

  @Test
  public void overrideDiskLimitPrefersConfig() {
    Config cfg = new Config();
    cfg.setLong("cache", A, "diskLimit", 512);
    assertThat(
            getCacheSpec(
                cfg,
                new CacheModule() {
                  @Override
                  public void configure() {
                    persist(A, String.class, String.class).diskLimit(3);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().diskLimit(512).build());
  }

  @Test
  public void overrideAllInBinding() {
    assertThat(
            getCacheSpec(
                new Config(),
                new CacheModule() {
                  @Override
                  public void configure() {
                    persist(A, String.class, String.class).maximumWeight(512).diskLimit(512);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().maximumWeight(512).diskLimit(512).build());
  }

  @Test
  public void overrideAllInConfig() {
    Config cfg = new Config();
    cfg.setLong("cache", A, "memoryLimit", 512);
    cfg.setLong("cache", A, "diskLimit", 512);
    assertThat(
            getCacheSpec(
                cfg,
                new CacheModule() {
                  @Override
                  public void configure() {
                    persist(A, String.class, String.class);
                  }
                },
                A))
        .isEqualTo(newCacheSpecBuilder().maximumWeight(512).diskLimit(512).build());
  }

  private CacheSpec getCacheSpec(Config cfg, CacheModule cacheModule, String name) {
    return Guice.createInjector(
            cacheModule,
            new DefaultCacheFactory.Module(),
            new AbstractModule() {
              @SuppressWarnings("rawtypes")
              @Override
              public void configure() {
                bind(Path.class)
                    .annotatedWith(SitePath.class)
                    .toInstance(tempDir.getRoot().toPath());
                bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(cfg);

                DynamicSet.setOf(binder(), new TypeLiteral<DynamicSet<CacheRemovalListener>>() {});
                DynamicMap.mapOf(binder(), new TypeLiteral<Cache<?, ?>>() {});
              }
            })
        .getInstance(Key.get(CacheSpec.class, Names.named(name)));
  }

  private static CacheSpec.Builder newCacheSpecBuilder() {
    return CacheSpec.builder().maximumWeight(1024).diskLimit(134217728);
  }
}
