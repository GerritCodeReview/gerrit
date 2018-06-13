// Copyright (C) 2013 The Android Open Source Project
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

import org.eclipse.jgit.lib.Config;

public class GerritOptions {
  private final boolean headless;
  private final boolean slave;
  private final boolean enableGwtUi;
  private final boolean forcePolyGerritDev;

  public GerritOptions(Config cfg, boolean headless, boolean slave, boolean forcePolyGerritDev) {
    this.slave = slave;
    this.enableGwtUi = cfg.getBoolean("gerrit", null, "enableGwtUi", true);
    this.forcePolyGerritDev = forcePolyGerritDev;
    this.headless = headless;
  }

  public boolean headless() {
    return headless;
  }

  public boolean enableGwtUi() {
    return !headless && enableGwtUi;
  }

  public boolean enableMasterFeatures() {
    return !slave;
  }

  public boolean forcePolyGerritDev() {
    return !headless && forcePolyGerritDev;
  }
}
