// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.patch.IntraLineWeigher;
import com.google.gerrit.server.patch.PatchListWeigher;
import com.google.gerrit.server.vhost.SiteCacheFactory.SiteKey;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** JVM wide caches shared by multiple Gerrit virtual hosts. */
@Singleton
class GlobalCachePool {
  /**
   * Names of caches Gerrit manages validity of entries for and don't need to
   * expire to ensure correctness. These are special cases, either the key is a
   * strong key (e.g. is computed from the value stored so new values get new
   * keys) or Gerrit has another method of verifying the element's validity and
   * reloads the entry on demand. Any cache <strong>NOT</strong> on this list
   * must use expireAfterWrite to limit how stale the data can be.
   */
  private static final ImmutableSet<String> IMMUTABLE_CACHE_NAMES = ImmutableSet.of(
      "conflicts",
      "diff",
      "diff_intraline",
      "git_tags",
      "permission_sort",
      "projects",
      "project_list");

  /**
   * Names of caches that are explicitly disabled in a virtual hosted server.
   * These caches probably exist in a single server Gerrit Code Review
   * environment, but are known to be broken or disabled in the virtual hosted
   * version.
   */
  private static final ImmutableSet<String> DISABLED_CACHE_NAMES = ImmutableSet.of(
      "adv_bases",
      "changes",
      "change_kind");

  private static final ImmutableMap<String, Weigher<?, ?>> WEIGHERS = ImmutableMap.of(
      "diff", (Weigher<?, ?>) new PatchListWeigher(),
      "diff_intraline", new IntraLineWeigher());

  private final ImmutableMap<String, Cache<?, ?>> caches;

  @Inject
  GlobalCachePool(@GerritGlobalConfig Config config) {
    Map<String, Cache<?, ?>> m = Maps.newHashMap();

    // Estimate the cache names used by this process, these will be created
    // ahead of time so they do not incur a lock when starting up a site.
    Set<String> names = Sets.newTreeSet(config.getSubsections("cache"));
    names.addAll(IMMUTABLE_CACHE_NAMES);
    names.addAll(DISABLED_CACHE_NAMES);
    names.addAll(ImmutableSet.of(
        "accounts",
        "accounts_byemail",
        "accounts_byname",
        "groups",
        "groups_byinclude",
        "groups_byname",
        "groups_byuuid",
        "groups_external",
        "groups_members"));
    names.removeAll(m.keySet());
    for (String cacheName : names) {
      m.put(cacheName, createCache(config, cacheName));
    }
    this.caches = ImmutableMap.copyOf(m);
  }

  /**
   * Get the cache with the given name.
   *
   * @param name cache name.
   * @return a preexisting cache if one exists. The configuration comes from the
   *         global server configuration.
   * @throws ProvisionException the cache was not configured.
   */
  public <K, V> Cache<K, V> get(String name) {
    Cache<?, ?> cache = caches.get(name);
    if (cache == null) {
      throw new ProvisionException(String.format(
          "Cache %s must be declared in configuration or %s",
          name, GlobalCachePool.class.getName()));
    }

    // Due to erasure we can't really guarantee type safety, but the current
    // cache injection process happens to be typesafe.
    @SuppressWarnings("unchecked")
    Cache<K, V> result = (Cache<K, V>) cache;
    return result;
  }

  private Cache<?, ?> createCache(Config config, String name) {
    CacheBuilder<Object, Object> b = newBuilder(config, name);
    Cache<?, ?> cache = b.build(new Loader<Object, Object>(name));
    return cache;
  }

  private CacheBuilder<Object, Object> newBuilder(Config cfg, String name) {
    if (DISABLED_CACHE_NAMES.contains(name)) {
      CacheBuilder<Object, Object> b = CacheBuilder.newBuilder();
      b.maximumSize(0);
      return b;
    }

    CacheBuilder<Object, Object> b = CacheBuilder.newBuilder().recordStats();
    long time = ConfigUtil.getTimeUnit(cfg,
        "cache", name, "maxAge",
        -1, TimeUnit.SECONDS);
    if (time >= 0) {
      b.expireAfterWrite(time, TimeUnit.SECONDS);
    } else if ((time < 0) && !IMMUTABLE_CACHE_NAMES.contains(name)) {
      b.expireAfterWrite(15, TimeUnit.MINUTES);
    }

    if (WEIGHERS.containsKey(name)) {
      @SuppressWarnings("unchecked")
      Weigher<Object, Object> impl = (Weigher<Object, Object>) WEIGHERS.get(name);
      b.weigher(wrap(impl));
      b.maximumWeight(cfg.getLong("cache", name, "memoryLimit", 20 << 20));
    } else {
      b.maximumSize(cfg.getLong("cache", name, "memoryLimit", 1024));
    }

    return b;
  }

  private static <K, V> Weigher<SiteKey<K, V>, V> wrap(final Weigher<K, V> impl) {
    return new Weigher<SiteKey<K, V>, V>() {
      @Override
      public int weigh(SiteKey<K, V> k, V v) {
        return impl.weigh(k.getKey(), v);
      }
    };
  }

  private static class Loader<K, V> extends CacheLoader<SiteKey<K, V>, V> {
    private final String name;

    Loader(String name) {
      this.name = name;
    }

    @Override
    public V load(SiteKey<K, V> key) throws Exception {
      CacheLoader<K, V> loader = key.getLoader();
      checkNotNull(loader, "CacheLoader required for %s", name);
      return loader.load(key.getKey());
    }

    @Override
    public Map<SiteKey<K, V>, V> loadAll(Iterable<? extends SiteKey<K, V>> keys)
        throws Exception {
      SiteKey<K, V> first = Iterables.getFirst(keys, null);
      checkNotNull(first, "expected non-empty keys collection");

      CacheLoader<K, V> loader = first.getLoader();
      checkNotNull(loader, "CacheLoader required for %s", name);

      Map<K, V> tmp = loader.loadAll(Iterables.transform(
          keys,
          new Function<SiteKey<K, V>, K> () {
            @Override
            public K apply(SiteKey<K, V> in) {
              return in.getKey();
            }
          }));
      Map<SiteKey<K, V>, V> result = Maps.newHashMapWithExpectedSize(tmp.size());
      for (Map.Entry<K, V> e : tmp.entrySet()) {
        result.put(new SiteKey<K, V>(first.getSiteName(), e.getKey(), null), e.getValue());
      }
      return result;
    }
  }
}
