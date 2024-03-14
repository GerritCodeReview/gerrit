// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.git.meta;

import com.google.common.base.Strings;
import com.google.gerrit.entities.RefNames;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/**
 * Versioned configuration file living in git
 *
 * <p>This class is a low-level API that allows callers to read the config directly from a
 * repository and make updates to it.
 */
public class VersionedConfigFile extends VersionedMetaData {
  protected final String ref;
  protected final String fileName;
  protected final String defaultOnSaveMessage;
  protected Config cfg;

  public VersionedConfigFile(String fileName) {
    this(RefNames.REFS_CONFIG, fileName);
  }

  public VersionedConfigFile(String ref, String fileName) {
    this(ref, fileName, "Updated configuration\n");
  }

  public VersionedConfigFile(String ref, String fileName, String defaultOnSaveMessage) {
    this.ref = ref;
    this.fileName = fileName;
    this.defaultOnSaveMessage = defaultOnSaveMessage;
  }

  public Config getConfig() {
    if (cfg == null) {
      cfg = new Config();
    }
    return cfg;
  }

  protected String getFileName() {
    return fileName;
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    cfg = readConfig(fileName);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage(defaultOnSaveMessage);
    }
    saveConfig(fileName, cfg);
    return true;
  }
}
