package com.google.gerrit.acceptance.api.revision;

import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;

public class RevisionDiffIntralineIT extends RevisionDiffIT {
  @ConfigSuite.Default
  public static Config intralineConfig() {
    Config config = new Config();
    config.setBoolean(TEST_PARAMETER_MARKER, null, "intraline", true);
    return config;
  }
}
