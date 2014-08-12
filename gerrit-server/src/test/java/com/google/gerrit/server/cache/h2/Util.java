package com.google.gerrit.server.cache.h2;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.testutil.TempFileUtil;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Util {

  public static PersistentCacheFactory createH2Factory(DefaultCacheFactory factory, Config cfg) throws FileNotFoundException, IOException {
    return new H2CacheFactory(factory, cfg, createSitePath(),
        DynamicMap.<Cache<?, ?>> emptyMap());
  }

  private static SitePaths createSitePath() throws IOException, FileNotFoundException {
    File sitePath = TempFileUtil.createTempDirectory();
    System.out.println(sitePath.getAbsolutePath());
    SitePaths site = new SitePaths(sitePath);
    return site;
  }

}
