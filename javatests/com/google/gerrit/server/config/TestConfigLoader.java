package com.google.gerrit.server.config;

import com.google.gerrit.common.Nullable;
import com.google.inject.Inject;

public class TestConfigLoader {

  String gerritInstanceId;

  @Inject
  TestConfigLoader(@Nullable @GerritInstanceId String gerritInstanceId) {
    this.gerritInstanceId = gerritInstanceId;
  }
}
