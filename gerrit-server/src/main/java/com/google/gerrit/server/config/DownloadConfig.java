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

package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Download protocol from {@code gerrit.config}. */
@Singleton
public class DownloadConfig {
  private final Set<DownloadScheme> downloadSchemes;
  private final Set<DownloadCommand> downloadCommands;

  @Inject
  DownloadConfig(@GerritServerConfig final Config cfg,
      final SystemConfig s) {
    List<DownloadScheme> allSchemes =
        ConfigUtil.getEnumList(cfg, "download", null, "scheme",
            DownloadScheme.DEFAULT_DOWNLOADS);
    downloadSchemes =
        Collections.unmodifiableSet(new HashSet<DownloadScheme>(allSchemes));

    List<DownloadCommand> allCommands =
        ConfigUtil.getEnumList(cfg, "download", null, "command",
            DownloadCommand.DEFAULT_DOWNLOADS);
    downloadCommands =
        Collections.unmodifiableSet(new HashSet<DownloadCommand>(allCommands));
  }

  /** Scheme used to download. */
  public Set<DownloadScheme> getDownloadSchemes() {
    return downloadSchemes;
  }

  /** Command used to download. */
  public Set<DownloadCommand> getDownloadCommands() {
    return downloadCommands;
  }
}
