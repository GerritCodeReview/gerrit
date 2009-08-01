package com.google.gerrit.server.config;

import com.google.gerrit.client.reviewdb.SystemConfig.LoginType;
import com.google.inject.Inject;
import com.google.inject.Provider;

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

  private final Config cfg;
  private final File sitePath;
  private final AuthConfig authConfig;

  @Inject
  CacheManagerProvider(@GerritServerConfig final Config cfg,
      @SitePath final File sitePath, final AuthConfig authConfig) {
    this.cfg = cfg;
    this.sitePath = sitePath;
    this.authConfig = authConfig;
  }

  @Override
  public CacheManager get() {
    return new CacheManager(toConfiguration(cfg, sitePath, authConfig));
  }

  private Configuration toConfiguration(final Config cfg, final File sitePath,
      final AuthConfig authConfig) {
    final Configuration mgrCfg = new Configuration();
    configureDiskStore(cfg, sitePath, mgrCfg);
    configureDefaultCache(cfg, mgrCfg);

    if (authConfig.getLoginType() == LoginType.OPENID) {
      final CacheConfiguration c;
      c = configureNamedCache(cfg, mgrCfg, "openid", false, 5);
      c.setTimeToLiveSeconds(c.getTimeToIdleSeconds());
      mgrCfg.addCache(c);
    }

    mgrCfg.addCache(configureNamedCache(cfg, mgrCfg, "diff", true, 0));
    for (String n : new String[] {"sshkeys"}) {
      mgrCfg.addCache(configureNamedCache(cfg, mgrCfg, n, false, 0));
    }
    return mgrCfg;
  }

  private void configureDiskStore(final Config cfg, final File sitePath,
      final Configuration mgrCfg) {
    String path = cfg.getString("cache", null, "directory");
    if (path == null || path.length() == 0) {
      path = "disk_cache";
    }

    final File loc = new File(sitePath, path);
    if (loc.exists() || loc.mkdirs()) {
      if (loc.canWrite()) {
        final DiskStoreConfiguration c = new DiskStoreConfiguration();
        c.setPath(loc.getAbsolutePath());
        mgrCfg.addDiskStore(c);
        log.info("Enabling disk cache " + loc.getAbsolutePath());
      } else {
        log.warn("Can't write to disk cache: " + loc.getAbsolutePath());
      }
    } else {
      log.warn("Can't create disk cache: " + loc.getAbsolutePath());
    }
  }

  private void configureDefaultCache(final Config i, final Configuration mgrCfg) {
    final CacheConfiguration c = new CacheConfiguration();

    c.setMaxElementsInMemory(i.getInt("cache", "memorylimit", 1024));
    c.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU);

    c.setTimeToLiveSeconds(0);
    final int oneday = 24 * 60;
    c.setTimeToIdleSeconds(i.getInt("cache", "maxage", 3 * 30 * oneday) * 60);
    c.setEternal(c.getTimeToIdleSeconds() == 0);

    if (mgrCfg.getDiskStoreConfiguration() != null) {
      c.setMaxElementsOnDisk(i.getInt("cache", "disklimit", 16384));
      c.setOverflowToDisk(false);
      c.setDiskPersistent(false);

      int diskbuffer = i.getInt("cache", "diskbuffer", 5 * 1024 * 1024);
      diskbuffer /= 1024 * 1024;
      c.setDiskSpoolBufferSizeMB(Math.max(1, diskbuffer));
      c.setDiskExpiryThreadIntervalSeconds(60 * 60);
    }

    mgrCfg.setDefaultCacheConfiguration(c);
  }

  private CacheConfiguration configureNamedCache(final Config i,
      final Configuration mgrCfg, final String name, final boolean disk,
      final int defaultAge) {
    final CacheConfiguration def = mgrCfg.getDefaultCacheConfiguration();
    final CacheConfiguration cfg;
    try {
      cfg = def.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("Cannot configure cache " + name, e);
    }
    cfg.setName(name);

    cfg.setMaxElementsInMemory(i.getInt("cache", name, "memorylimit", def
        .getMaxElementsInMemory()));

    cfg.setTimeToIdleSeconds(i.getInt("cache", name, "maxage", defaultAge > 0
        ? defaultAge : (int) (def.getTimeToIdleSeconds() / 60)) * 60);
    cfg.setEternal(cfg.getTimeToIdleSeconds() == 0);

    if (disk && mgrCfg.getDiskStoreConfiguration() != null) {
      cfg.setMaxElementsOnDisk(i.getInt("cache", name, "disklimit", def
          .getMaxElementsOnDisk()));

      final int m = 1024 * 1024;
      final int diskbuffer =
          i.getInt("cache", name, "diskbuffer", def.getDiskSpoolBufferSizeMB()
              * m)
              / m;
      cfg.setDiskSpoolBufferSizeMB(Math.max(1, diskbuffer));
      cfg.setOverflowToDisk(true);
      cfg.setDiskPersistent(true);
    }

    return cfg;
  }
}
