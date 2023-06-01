// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.metrics.proc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.metrics.proc.JGitMetricModule.CACHE_USED_PER_REPO_METRIC_NAME;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import org.eclipse.jgit.storage.file.WindowCacheStats;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class JgitMetricModuleTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Inject MetricRegistry registry;

  private final TestWindowCache testWindowCache = new TestWindowCache();
  private final LifecycleManager lifeCycleManager = new LifecycleManager();

  @Test
  public void shouldExposeBlockCacheUsedPerRepoWithHighestValueWhenMetricNameAlreadyExists() {
    testWindowCache.setOpenByteCountsPerRepository(
        new HashMap<String, Long>() {
          {
            put("some/repo", 1024L);
            put("some-repo", 2048L);
          }
        });

    lifeCycleManager.start();

    SortedMap<String, Gauge> someRepoCaches =
        registry.getGauges(MetricFilter.startsWith(CACHE_USED_PER_REPO_METRIC_NAME));

    assertThat(someRepoCaches).hasSize(1);
    assertThat(someRepoCaches.get(CACHE_USED_PER_REPO_METRIC_NAME + "/some-repo").getValue())
        .isEqualTo(2048L);
  }

  @Test
  public void shouldExposeAllBlockCacheUsedPerRepo() {
    testWindowCache.setOpenByteCountsPerRepository(
        new HashMap<String, Long>() {
          {
            put("foo-repo", 1024L);
            put("bar-repo", 2048L);
          }
        });

    lifeCycleManager.start();

    SortedMap<String, Gauge> someRepoCaches =
        registry.getGauges(MetricFilter.startsWith(CACHE_USED_PER_REPO_METRIC_NAME));

    assertThat(someRepoCaches).hasSize(2);

    assertThat(someRepoCaches.get(CACHE_USED_PER_REPO_METRIC_NAME + "/foo-repo").getValue())
        .isEqualTo(1024L);
    assertThat(someRepoCaches.get(CACHE_USED_PER_REPO_METRIC_NAME + "/bar-repo").getValue())
        .isEqualTo(2048L);
  }

  @Before
  public void setup() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(MetricRegistry.class).in(Scopes.SINGLETON);
                bind(DropWizardMetricMaker.class).in(Scopes.SINGLETON);
                bind(MetricMaker.class).to(DropWizardMetricMaker.class);

                install(new JGitMetricModule(testWindowCache));
              }
            });

    lifeCycleManager.add(injector);

    injector.injectMembers(this);
  }

  private static class TestWindowCache implements WindowCacheStats {

    private Map<String, Long> openByteCountsPerRepository;

    public TestWindowCache() {
      this.openByteCountsPerRepository = Collections.emptyMap();
    }

    public void setOpenByteCountsPerRepository(Map<String, Long> cacheMap) {
      this.openByteCountsPerRepository = cacheMap;
    }

    @Override
    public long getHitCount() {
      return 0;
    }

    @Override
    public long getMissCount() {
      return 0;
    }

    @Override
    public long getLoadSuccessCount() {
      return 0;
    }

    @Override
    public long getLoadFailureCount() {
      return 0;
    }

    @Override
    public long getEvictionCount() {
      return 0;
    }

    @Override
    public long getTotalLoadTime() {
      return 0;
    }

    @Override
    public long getOpenFileCount() {
      return 0;
    }

    @Override
    public long getOpenByteCount() {
      return 0;
    }

    @Override
    public Map<String, Long> getOpenByteCountPerRepository() {
      return openByteCountsPerRepository;
    }

    @Override
    public void resetCounters() {}
  }
}
