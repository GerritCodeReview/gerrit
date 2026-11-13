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

package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.PackOutputCache;
import org.eclipse.jgit.transport.UploadPack;

/** Shares generated fetch packs after each request passes authorization and validation. */
@Singleton
public class UploadPackOutputCache implements UploadPackInitializer, LifecycleListener {
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicSet.setOf(binder(), UploadPackInitializer.class);
      DynamicSet.bind(binder(), UploadPackInitializer.class).to(UploadPackOutputCache.class);
      listener().to(UploadPackOutputCache.class);
    }
  }

  private final Provider<CurrentUser> userProvider;
  private final PackOutputCache cache;

  @Inject
  UploadPackOutputCache(
      @GerritServerConfig Config config,
      SitePaths site,
      Provider<CurrentUser> userProvider,
      MetricMaker metrics)
      throws IOException {
    this.userProvider = userProvider;
    Path directory = site.resolve(config.getString("pack", null, "outputCacheDirectory"));
    cache =
        directory == null
            ? null
            : new PackOutputCache(
                directory,
                config.getLong("pack", "outputCacheMaxBytes", 1L << 30),
                config.getInt("pack", "outputCacheMaxEntries", 128),
                Duration.ofSeconds(
                    ConfigUtil.getTimeUnit(
                        config, "pack", null, "outputCacheMaxAge", 300, TimeUnit.SECONDS)),
                config.getInt("pack", "outputCacheMinOccurrences", 1),
                config.getInt("pack", "outputCacheMaxConcurrentProducers", 8));
    metrics.newConstantMetric(
        "git/upload-pack/output_cache/enabled",
        cache == null ? 0 : 1,
        new Description("Whether generated fetch pack caching is enabled"));
    if (cache != null) {
      registerMetrics(metrics);
    }
  }

  private void registerMetrics(MetricMaker metrics) {
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/hits",
        Long.class,
        new Description("Requests reusing an in-flight or completed pack")
            .setCumulative()
            .setUnit("requests"),
        () -> cache.getStats().hits());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/misses",
        Long.class,
        new Description("Requests starting a cached pack producer")
            .setCumulative()
            .setUnit("requests"),
        () -> cache.getStats().misses());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/bypasses",
        Long.class,
        new Description("Requests bypassing admission or cache capacity limits")
            .setCumulative()
            .setUnit("requests"),
        () -> cache.getStats().bypasses());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/generation_failures",
        Long.class,
        new Description("Failed cached pack generations").setCumulative().setUnit("failures"),
        () -> cache.getStats().generationFailures());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/generated_bytes",
        Long.class,
        new Description("Pack bytes generated, including uncached bypasses")
            .setCumulative()
            .setUnit("bytes"),
        () -> cache.getStats().generatedBytes());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/served_bytes",
        Long.class,
        new Description("Pack bytes served, including uncached bypasses")
            .setCumulative()
            .setUnit("bytes"),
        () -> cache.getStats().servedBytes());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/removed_files",
        Long.class,
        new Description(
                "Cache data files removed by expiration, eviction or abandoned-instance cleanup")
            .setCumulative()
            .setUnit("files"),
        () -> cache.getStats().removedFiles());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/active_producers",
        Long.class,
        new Description("Pack generators currently running").setGauge().setUnit("producers"),
        () -> (long) cache.getStats().activeProducers());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/entries",
        Long.class,
        new Description("Cache files including active files pending deletion")
            .setGauge()
            .setUnit("entries"),
        () -> (long) cache.getStats().entries());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/disk_bytes",
        Long.class,
        new Description("Logical bytes in all cache files including active spools")
            .setGauge()
            .setUnit("bytes"),
        () -> cache.getStats().bytes());
    metrics.newCallbackMetric(
        "git/upload-pack/output_cache/retained_bytes",
        Long.class,
        new Description("Completed pack bytes retained for reuse").setGauge().setUnit("bytes"),
        () -> cache.getStats().retainedBytes());
  }

  @Override
  public void init(Project.NameKey project, UploadPack uploadPack) {
    if (cache == null) {
      return;
    }
    CurrentUser user = userProvider.get().getUserForPermission();
    if (!user.isIdentifiedUser() && !(user instanceof AnonymousUser)) {
      // Internal or plugin-defined identities may have request-specific authority.
      return;
    }
    uploadPack.setPackOutputCache(cache, project.get());
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    if (cache != null) {
      cache.close();
    }
  }
}
