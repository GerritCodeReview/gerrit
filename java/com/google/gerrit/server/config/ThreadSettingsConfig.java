// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class ThreadSettingsConfig {
  private final int sshdThreads;
  private final int httpdMaxThreads;
  private final int sshdBatchThreads;
  private final int databasePoolLimit;

  @Inject
  ThreadSettingsConfig(@GerritServerConfig Config cfg) {
    int cores = Runtime.getRuntime().availableProcessors();
    sshdThreads = cfg.getInt("sshd", "threads", 2 * cores);
    httpdMaxThreads = cfg.getInt("httpd", "maxThreads", 25);
    int defaultDatabasePoolLimit = sshdThreads + httpdMaxThreads + 2;
    databasePoolLimit = cfg.getInt("database", "poolLimit", defaultDatabasePoolLimit);
    sshdBatchThreads = cores == 1 ? 1 : 2;
  }

  public int getDatabasePoolLimit() {
    return databasePoolLimit;
  }

  public int getHttpdMaxThreads() {
    return httpdMaxThreads;
  }

  public int getSshdThreads() {
    return sshdThreads;
  }

  public int getSshdBatchTreads() {
    return sshdBatchThreads;
  }
}
