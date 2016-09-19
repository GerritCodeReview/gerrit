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

package com.google.gerrit.httpd;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Enums;

import org.eclipse.jgit.lib.Config;

public class GerritOptions {
  public enum UiPreference {
    NONE,
    GWT,
    POLYGERRIT;
  }

  private final boolean headless;
  private final boolean slave;
  private final boolean enablePolyGerrit;
  private final boolean enableGwtUi;
  private final boolean forcePolyGerritDev;
  private final UiPreference defaultUi;

  public GerritOptions(Config cfg, boolean headless, boolean slave,
      boolean forcePolyGerritDev) {
    this.slave = slave;
    this.enablePolyGerrit = forcePolyGerritDev
        || cfg.getBoolean("gerrit", null, "enablePolyGerrit", false);
    this.enableGwtUi = cfg.getBoolean("gerrit", null, "enableGwtUi", true);
    this.forcePolyGerritDev = forcePolyGerritDev;
    this.headless = headless || (!enableGwtUi && !enablePolyGerrit);

    UiPreference defaultUi = enablePolyGerrit && !enableGwtUi
        ? UiPreference.POLYGERRIT
        : UiPreference.GWT;
    String uiStr = firstNonNull(
        cfg.getString("gerrit", null, "ui"),
        defaultUi.name().toUpperCase());
    this.defaultUi =
        Enums.getIfPresent(UiPreference.class, uiStr).or(UiPreference.NONE);
    uiStr = defaultUi.name().toLowerCase();

    switch (defaultUi) {
      case GWT:
        checkArgument(enableGwtUi,
            "gerrit.ui = %s but GWT UI is disabled", uiStr);
        break;
      case POLYGERRIT:
        checkArgument(enablePolyGerrit,
            "gerrit.ui = %s but PolyGerrit is disabled", uiStr);
        break;
      case NONE:
      default:
        throw new IllegalArgumentException("invalid gerrit.ui: " + uiStr);
    }
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

  public boolean enablePolyGerrit() {
    return !headless && enablePolyGerrit;
  }

  public boolean forcePolyGerritDev() {
    return !headless && forcePolyGerritDev;
  }

  public UiPreference defaultUi() {
    return defaultUi;
  }
}
