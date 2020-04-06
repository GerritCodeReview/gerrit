// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/**
 * Low-level storage API to load Gerrit's default config from {@code All-Users}. Should not be used
 * directly.
 */
public class VersionedDefaultPreferences extends VersionedMetaData {
  private static final String PREFERENCES_CONFIG = "preferences.config";

  private Config cfg;

  @Override
  protected String getRefName() {
    return RefNames.REFS_USERS_DEFAULT;
  }

  public Config getConfig() {
    checkState(cfg != null, "Default preferences not loaded yet.");
    return cfg;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    cfg = readConfig(PREFERENCES_CONFIG);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Update default preferences\n");
    }
    saveConfig(PREFERENCES_CONFIG, cfg);
    return true;
  }
}
