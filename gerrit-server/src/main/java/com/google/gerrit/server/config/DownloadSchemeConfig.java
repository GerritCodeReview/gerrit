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

import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadScheme;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Download protocol from {@code gerrit.config}. */
@Singleton
public class DownloadSchemeConfig {
  private final Set<DownloadScheme> downloadSchemes;

  @Inject
  DownloadSchemeConfig(@GerritServerConfig final Config cfg,
      final SystemConfig s) {
    List<DownloadScheme> all =
        ConfigUtil.getEnumList(cfg, "download", null, "scheme",
            DownloadScheme.DEFAULT_DOWNLOADS);

    downloadSchemes =
        Collections.unmodifiableSet(new HashSet<DownloadScheme>(all));
  }

  /** Scheme used to download. */
  public Set<DownloadScheme> getDownloadScheme() {
    return downloadSchemes;
  }
}
