// Copyright (C) 2011 The Android Open Source Project
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

import org.eclipse.jgit.lib.Config;

/** Remote dashboard urls from {@code gerrit.config}. */
public class RemoteDashboardConfig {
  private String[] remoteDashboardUrls;

  @Inject
  public RemoteDashboardConfig(@GerritServerConfig final Config cfg) {
    remoteDashboardUrls = cfg.getStringList("remotedashboard", null, "url");
  }

  public String[] getRemoteDashboardUrl() {
    return remoteDashboardUrls;
  }
}
