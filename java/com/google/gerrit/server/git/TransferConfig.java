// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.config.ConfigUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.pack.PackConfig;

@Singleton
public class TransferConfig {
  private final int timeout;
  private final PackConfig packConfig;
  private final long maxObjectSizeLimit;
  private final String maxObjectSizeLimitFormatted;

  @Inject
  TransferConfig(@GerritServerConfig Config cfg) {
    timeout =
        (int)
            ConfigUtil.getTimeUnit(
                cfg,
                "transfer",
                null,
                "timeout", //
                0,
                TimeUnit.SECONDS);
    maxObjectSizeLimit = cfg.getLong("receive", "maxObjectSizeLimit", 0);
    maxObjectSizeLimitFormatted = cfg.getString("receive", null, "maxObjectSizeLimit");

    packConfig = new PackConfig();
    packConfig.setDeltaCompress(false);
    packConfig.setThreads(1);
    packConfig.fromConfig(cfg);
  }

  /** @return configured timeout, in seconds. 0 if the timeout is infinite. */
  public int getTimeout() {
    return timeout;
  }

  public PackConfig getPackConfig() {
    return packConfig;
  }

  public long getMaxObjectSizeLimit() {
    return maxObjectSizeLimit;
  }

  public String getFormattedMaxObjectSizeLimit() {
    return maxObjectSizeLimitFormatted;
  }

  public long getEffectiveMaxObjectSizeLimit(ProjectState p) {
    long global = getMaxObjectSizeLimit();
    long local = p.getMaxObjectSizeLimit();
    if (global > 0 && local > 0) {
      return Math.min(global, local);
    }
    // zero means "no limit", in this case the max is more limiting
    return Math.max(global, local);
  }
}
