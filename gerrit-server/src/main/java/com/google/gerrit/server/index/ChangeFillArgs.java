// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

/** Arguments needed to fill in missing data in the input object. */
public class ChangeFillArgs {
  public final TrackingFooters trackingFooters;
  public final boolean allowsDrafts;
  public final AllUsersName allUsers;

  @Inject
  ChangeFillArgs(
      TrackingFooters trackingFooters, @GerritServerConfig Config cfg, AllUsersName allUsers) {
    this.trackingFooters = trackingFooters;
    this.allowsDrafts = cfg == null ? true : cfg.getBoolean("change", "allowDrafts", true);
    this.allUsers = allUsers;
  }
}
