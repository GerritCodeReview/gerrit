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

import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Pool of all declared caches created by {@link CacheModule}s. */
@Singleton
public class CachePool {
  private static final Logger log = LoggerFactory.getLogger(CachePool.class);

  private final Config config;
  private final File sitePath;

  private final Object lock = new Object();
  private final Map<String, CacheProvider<?, ?>> caches;
  private CacheManager manager;

  @Inject
  CachePool(@GerritServerConfig final Config cfg, @SitePath final File sitePath) {
    this.config = cfg;
    this.sitePath = sitePath;
    this.caches = new HashMap<String, CacheProvider<?, ?>>();
  }

  /** Start the cache pool. The pool must be started before any access occurs. */
  public void start() {
    synchronized (lock) {
      if (manager != null) {
        throw new IllegalStateException("Cache pool has already been started");
      }

      manager = new CacheManager(new Factory().toConfiguration());
      for (CacheProvider<?, ?> p : caches.values()) {
        p.bind(manager.getEhcache(p.getName()));
      }
    }
  }

  /** Stop the cache pool. The pool should be stopped before terminating. */
  public void stop() {
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

        c.setTimeToIdleSeconds(0);
        c.setTimeToLiveSeconds(getSeconds(name, "maxage", p.maxAge()));
        c.setEternal(c.getTimeToLiveSeconds() == 0);

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

      String path = config.getString("cache", null, "directory");
      if (path == null || path.length() == 0) {
        return;
      }

      File loc = new File(path);
      if (!loc.isAbsolute()) {
        loc = new File(sitePath, path);
      }
      if (loc.exists() || loc.mkdirs()) {
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

      c.setTimeToIdleSeconds(0);
      c.setTimeToLiveSeconds(0 /* infinite */);
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
      try {
        final CacheConfiguration c;
        c = mgr.getDefaultCacheConfiguration().clone();
        c.setName(name);
        return c;
      } catch (CloneNotSupportedException e) {
        throw new ProvisionException("Cannot configure cache " + name, e);
      }
    }
  }
}
