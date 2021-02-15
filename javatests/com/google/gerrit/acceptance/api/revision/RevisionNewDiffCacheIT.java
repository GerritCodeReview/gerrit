package com.google.gerrit.acceptance.api.revision;

import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;

public class RevisionNewDiffCacheIT extends RevisionDiffIT {
  @ConfigSuite.Default
  public static Config newDiffCacheConfig() {
    Config config = new Config();
    config.setBoolean("cache", "diff_cache", "useNewDiffCache", true);
    return config;
  }
}
