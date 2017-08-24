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

package com.google.gerrit.pgm.util;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO(dborowitz): Not necessary once we switch to NoteDb.
/** Utility to limit threads used by a batch program. */
public class ThreadLimiter {
  private static final Logger log = LoggerFactory.getLogger(ThreadLimiter.class);

  public static int limitThreads(Injector dbInjector, int threads) {
    return limitThreads(
        dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class)),
        dbInjector.getInstance(DataSourceType.class),
        dbInjector.getInstance(ThreadSettingsConfig.class),
        threads);
  }

  private static int limitThreads(
      Config cfg, DataSourceType dst, ThreadSettingsConfig threadSettingsConfig, int threads) {
    boolean usePool = cfg.getBoolean("database", "connectionpool", dst.usePool());
    int poolLimit = threadSettingsConfig.getDatabasePoolLimit();
    if (usePool && threads > poolLimit) {
      log.warn("Limiting program to " + poolLimit + " threads due to database.poolLimit");
      return poolLimit;
    }
    return threads;
  }

  private ThreadLimiter() {}
}
