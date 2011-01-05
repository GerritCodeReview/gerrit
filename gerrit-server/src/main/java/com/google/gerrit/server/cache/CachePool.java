// Copyright (C) 2009 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Pool of all declared caches created by {@link CacheModule}s. */
@Singleton
public class CachePool {
  private static final Logger log = LoggerFactory.getLogger(CachePool.class);

  public static class Lifecycle implements LifecycleListener {
    private final CachePool cachePool;

    @Inject
    Lifecycle(final CachePool cachePool) {
      this.cachePool = cachePool;
    }

    @Override
    public void start() {
      cachePool.start();
    }

    @Override
    public void stop() {
      cachePool.stop();
    }
  }

  private final Config config;
  private final SitePaths site;

  private final Object lock = new Object();
  private final Map<String, CacheProvider<?, ?>> caches;
  private CacheManager manager;

  @Inject
  CachePool(@GerritServerConfig final Config cfg, final SitePaths site) {
    this.config = cfg;
    this.site = site;
    this.caches = new HashMap<String, CacheProvider<?, ?>>();
  }

  private void start() {
    synchronized (lock) {
      if (manager != null) {
        throw new IllegalStateException("Cache pool has already been started");
      }

      try {
        System.setProperty("net.sf.ehcache.skipUpdateCheck", "" + true);
      } catch (SecurityException e) {
        // Ignore it, the system is just going to ping some external page
        // using a background thread and there's not much we can do about
        // it now.
      }

      manager = new CacheManager(new Factory().toConfiguration());
      for (CacheProvider<?, ?> p : caches.values()) {
        p.bind(manager.getEhcache(p.getName()));
      }
    }
  }

  private void stop() {
    synchronized (lock) {
      if (manager != null) {
        manager.shutdown();
      }
    }
  }

  /** <i>Discouraged</i> Get the underlying cache descriptions, for statistics. */
  public CacheManager getCacheManager() {
    synchronized (lock) {
      return manager;
    }
  }

  <K, V> ProxyEhcache register(final CacheProvider<K, V> provider) {
    synchronized (lock) {
      if (manager != null) {
        throw new IllegalStateException("Cache pool has already been started");
      }

      final String n = provider.getName();
      if (caches.containsKey(n) && caches.get(n) != provider) {
        throw new IllegalStateException("Cache \"" + n + "\" already defined");
      }
      caches.put(n, provider);
      return new ProxyEhcache(n);
    }
  }

  private class Factory {
    private static final int MB = 1024 * 1024;
    private final Configuration mgr = new Configuration();

    Configuration toConfiguration() {
      configureDiskStore();
      configureDefaultCache();

      for (CacheProvider<?, ?> p : caches.values()) {
        final String name = p.getName();
        final CacheConfiguration c = newCache(name);
        c.setMemoryStoreEvictionPolicyFromObject(toPolicy(p.evictionPolicy()));

        c.setMaxElementsInMemory(getInt(name, "memorylimit", p.memoryLimit()));

        long ttl = getSeconds(name, "maxage", p.maxAge());
        c.setEternal(ttl == 0);
        if (ttl != 0) {
          c.setTimeToIdleSeconds(0);
          c.setTimeToLiveSeconds(ttl);
        }

        if (p.disk() && mgr.getDiskStoreConfiguration() != null) {
          c.setMaxElementsOnDisk(getInt(name, "disklimit", p.diskLimit()));

          int v = c.getDiskSpoolBufferSizeMB() * MB;
          v = getInt(name, "diskbuffer", v) / MB;
          c.setDiskSpoolBufferSizeMB(Math.max(1, v));
          c.setOverflowToDisk(c.getMaxElementsOnDisk() > 0);
          c.setDiskPersistent(c.getMaxElementsOnDisk() > 0);
        }

        mgr.addCache(c);
      }

      return mgr;
    }

    private MemoryStoreEvictionPolicy toPolicy(final EvictionPolicy policy) {
      switch (policy) {
        case LFU:
          return MemoryStoreEvictionPolicy.LFU;

        case LRU:
          return MemoryStoreEvictionPolicy.LRU;

        default:
          throw new IllegalArgumentException("Unsupported " + policy);
      }
    }

    private int getInt(String n, String s, int d) {
      return config.getInt("cache", n, s, d);
    }

    private long getSeconds(String n, String s, long d) {
      d = MINUTES.convert(d, SECONDS);
      long m = ConfigUtil.getTimeUnit(config, "cache", n, s, d, MINUTES);
      return SECONDS.convert(m, MINUTES);
    }

    private void configureDiskStore() {
      boolean needDisk = false;
      for (CacheProvider<?, ?> p : caches.values()) {
        if (p.disk()) {
          needDisk = true;
          break;
        }
      }
      if (!needDisk) {
        return;
      }

      File loc = site.resolve(config.getString("cache", null, "directory"));
      if (loc == null) {
      } else if (loc.exists() || loc.mkdirs()) {
        if (loc.canWrite()) {
          final DiskStoreConfiguration c = new DiskStoreConfiguration();
          c.setPath(loc.getAbsolutePath());
          mgr.addDiskStore(c);
          log.info("Enabling disk cache " + loc.getAbsolutePath());
        } else {
          log.warn("Can't write to disk cache: " + loc.getAbsolutePath());
        }
      } else {
        log.warn("Can't create disk cache: " + loc.getAbsolutePath());
      }
    }

    private void configureDefaultCache() {
      final CacheConfiguration c = new CacheConfiguration();

      c.setMaxElementsInMemory(1024);
      c.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU);

      c.setEternal(true);

      if (mgr.getDiskStoreConfiguration() != null) {
        c.setMaxElementsOnDisk(16384);
        c.setOverflowToDisk(false);
        c.setDiskPersistent(false);

        c.setDiskSpoolBufferSizeMB(5);
        c.setDiskExpiryThreadIntervalSeconds(60 * 60);
      }

      mgr.setDefaultCacheConfiguration(c);
    }

    private CacheConfiguration newCache(final String name) {
      final CacheConfiguration c;
      c = mgr.getDefaultCacheConfiguration().clone();
      c.setName(name);
      return c;
    }
  }
}
