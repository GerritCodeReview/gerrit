package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.Config;

import java.io.File;

public class CacheManagerProvider implements Provider<CacheManager> {
  private static final Logger log =
      LoggerFactory.getLogger(CacheManagerProvider.class);

  private final Config config;
  private final File sitePath;
  private final AuthConfig authConfig;

  @Inject
  CacheManagerProvider(@GerritServerConfig final Config cfg,
      @SitePath final File sitePath, final AuthConfig authConfig) {
    this.config = cfg;
    this.sitePath = sitePath;
    this.authConfig = authConfig;
  }

  @Override
  public CacheManager get() {
    return new CacheManager(new Factory().toConfiguration());
  }

  private class Factory {
    private static final int MB = 1024 * 1024;
    private static final int ONE_DAY = 24 * 60;
    private static final int D_MAXAGE = 3 * 30 * ONE_DAY;
    private final Configuration mgr = new Configuration();

    Configuration toConfiguration() {
      configureDiskStore();
      configureDefaultCache();

      switch (authConfig.getLoginType()) {
        case OPENID:
          mgr.addCache(ttl(named("openid"), 5));
          break;
      }

      mgr.addCache(disk(named("diff")));
      mgr.addCache(named("sshkeys"));
      mgr.addCache(named("accounts_byemail"));

      return mgr;
    }

    private void configureDiskStore() {
      String path = config.getString("cache", null, "directory");
      if (path == null || path.length() == 0) {
        path = "disk_cache";
      }

      final File loc = new File(sitePath, path);
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

      c.setMaxElementsInMemory(config.getInt("cache", "memorylimit", 1024));
      c.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU);

      c.setTimeToIdleSeconds(config.getInt("cache", "maxage", D_MAXAGE) * 60);
      c.setTimeToLiveSeconds(c.getTimeToIdleSeconds());
      c.setEternal(c.getTimeToIdleSeconds() == 0);

      if (mgr.getDiskStoreConfiguration() != null) {
        c.setMaxElementsOnDisk(config.getInt("cache", "disklimit", 16384));
        c.setOverflowToDisk(false);
        c.setDiskPersistent(false);

        final int diskbuffer = config.getInt("cache", "diskbuffer", 5 * MB);
        c.setDiskSpoolBufferSizeMB(Math.max(1, diskbuffer / MB));
        c.setDiskExpiryThreadIntervalSeconds(60 * 60);
      }

      mgr.setDefaultCacheConfiguration(c);
    }

    private CacheConfiguration named(final String name) {
      final CacheConfiguration c = newCache(name);

      int e = c.getMaxElementsInMemory();
      c.setMaxElementsInMemory(config.getInt("cache", name, "memorylimit", e));

      ttl(c, (int) c.getTimeToIdleSeconds() / 60);

      return c;
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

    private CacheConfiguration ttl(final CacheConfiguration c, final int age) {
      final String name = c.getName();
      c.setTimeToIdleSeconds(config.getInt("cache", name, "maxage", age) * 60);
      c.setTimeToLiveSeconds(c.getTimeToIdleSeconds());
      c.setEternal(c.getTimeToIdleSeconds() == 0);
      return c;
    }

    private CacheConfiguration disk(final CacheConfiguration c) {
      final String name = c.getName();
      if (mgr.getDiskStoreConfiguration() != null) {
        int e = c.getMaxElementsOnDisk();
        c.setMaxElementsOnDisk(config.getInt("cache", name, "disklimit", e));

        int buffer = c.getDiskSpoolBufferSizeMB() * MB;
        buffer = config.getInt("cache", name, "diskbuffer", buffer) / MB;
        c.setDiskSpoolBufferSizeMB(Math.max(1, buffer));
        c.setOverflowToDisk(true);
        c.setDiskPersistent(true);
      }
      return c;
    }
  }
}
