// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.CacheRefreshExecutor;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.nio.file.Paths;
import javax.inject.Provider;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class MailSoySauceModuleTest {
  @Test
  public void soySauceProviderReturnsCachedValue() throws Exception {
    SitePaths sitePaths = new SitePaths(Paths.get("."));
    Injector injector =
        Guice.createInjector(
            new WorkQueue.WorkQueueModule(),
            new MailSoySauceModule(),
            new AbstractModule() {
              @Override
              protected void configure() {
                super.configure();
                bind(ListeningExecutorService.class)
                    .annotatedWith(CacheRefreshExecutor.class)
                    .toInstance(newDirectExecutorService());
                bind(SitePaths.class).toInstance(sitePaths);
                bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(new Config());
                bind(MetricMaker.class).to(DisabledMetricMaker.class);
                install(new DefaultMemoryCacheModule());
              }
            });
    Provider<SoySauce> soySauceProvider =
        injector.getProvider(Key.get(SoySauce.class, MailTemplates.class));
    LoadingCache<String, SoySauce> cache =
        injector.getInstance(
            Key.get(
                new TypeLiteral<LoadingCache<String, SoySauce>>() {},
                Names.named(MailSoySauceModule.CACHE_NAME)));
    assertThat(cache.stats().loadCount()).isEqualTo(0);
    // Theoretically, this can be flaky, if the delay before the second get takes several seconds.
    // We assume that tests is fast enough.
    assertThat(soySauceProvider.get()).isNotNull();
    assertThat(soySauceProvider.get()).isNotNull();
    assertThat(cache.stats().loadCount()).isEqualTo(1);
  }
}
