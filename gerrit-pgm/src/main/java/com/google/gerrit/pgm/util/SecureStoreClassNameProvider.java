// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.pgm.util;

import com.google.common.base.Strings;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.IOException;

public class SecureStoreClassNameProvider implements Provider<String> {
  private Config config;

  @Inject
  SecureStoreClassNameProvider(SitePaths sitePath) {
    FileBasedConfig cfg = new FileBasedConfig(sitePath.gerrit_config, FS.DETECTED);
    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    this.config = cfg;
  }

  @Override
  public String get() {
    return Strings.nullToEmpty(config.getString("gerrit", null, "secureStoreClass"));
  }
}
