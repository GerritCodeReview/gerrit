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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.server.CacheRefreshExecutor;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import javax.inject.Provider;

/**
 * Provides support for soy templates
 *
 * <p>Module loads templates with {@link MailSoySauceLoader} and caches compiled templates. The
 * cache refreshes automatically, so Gerrit does not need to be restarted if templates are changed.
 */
public class MailSoySauceModule extends CacheModule {
  static final String CACHE_NAME = "soy_sauce_compiled_templates";
  private static final String SOY_LOADING_CACHE_KEY = "KEY";

  @Override
  protected void configure() {
    // Cache stores only a single key-value pair (key is SOY_LOADING_CACHE_KEY). We are using
    // cache only for it refresh/expire logic.
    cache(CACHE_NAME, String.class, SoySauce.class)
        // Cache refreshes a value only on the access (if refreshAfterWrite interval is
        // passed). While the value is refreshed, cache returns old value.
        // Adding expireAfterWrite interval prevents cache from returning very old template.
        .refreshAfterWrite(Duration.ofSeconds(5))
        .expireAfterWrite(Duration.ofMinutes(1))
        .loader(SoySauceCacheLoader.class);
    bind(SoySauce.class).annotatedWith(MailTemplates.class).toProvider(SoySauceProvider.class);
  }

  @Singleton
  static class SoySauceProvider implements Provider<SoySauce> {
    private final LoadingCache<String, SoySauce> templateCache;

    @Inject
    SoySauceProvider(@Named(CACHE_NAME) LoadingCache<String, SoySauce> templateCache) {
      this.templateCache = templateCache;
    }

    @Override
    public SoySauce get() {
      try {
        return templateCache.get(SOY_LOADING_CACHE_KEY);
      } catch (ExecutionException e) {
        throw new ProvisionException("Can't get SoySauce from the cache", e);
      }
    }
  }

  @Singleton
  static class SoySauceCacheLoader extends CacheLoader<String, SoySauce> {
    private final ListeningExecutorService executor;
    private final MailSoySauceLoader loader;

    @Inject
    SoySauceCacheLoader(
        @CacheRefreshExecutor ListeningExecutorService executor, MailSoySauceLoader loader) {
      this.executor = executor;
      this.loader = loader;
    }

    @Override
    public SoySauce load(String key) throws Exception {
      checkArgument(
          SOY_LOADING_CACHE_KEY.equals(key),
          "Cache can have only one element with a key '%s'",
          SOY_LOADING_CACHE_KEY);
      return loader.load(this.getClass().getClassLoader());
    }

    @Override
    public ListenableFuture<SoySauce> reload(String key, SoySauce soySauce) {
      return executor.submit(() -> loader.load(this.getClass().getClassLoader()));
    }
  }
}
