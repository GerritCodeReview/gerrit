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

package com.google.gerrit.sshd;

import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

@Singleton
public class TransferConfig {
  private final int timeout;

  @Inject
  TransferConfig(@GerritServerConfig final Config cfg) {
    timeout = (int) ConfigUtil.getTimeUnit(cfg, "transfer", null, "timeout", //
        0, TimeUnit.SECONDS);
  }

  /** @return configured timeout, in seconds. 0 if the timeout is infinite. */
  public int getTimeout() {
    return timeout;
  }
}
