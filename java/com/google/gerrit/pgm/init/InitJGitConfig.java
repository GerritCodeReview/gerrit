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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.api.InitUtil.die;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.TransferConfig;
import org.eclipse.jgit.util.FS;

/** Initialize the JGit configuration. */
@Singleton
class InitJGitConfig implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths sitePaths;

  @Inject
  InitJGitConfig(ConsoleUI ui, SitePaths sitePaths) {
    this.ui = ui;
    this.sitePaths = sitePaths;
  }

  @Override
  public void run() {
    ui.header("JGit Configuration");
    FileBasedConfig jgitConfig = new FileBasedConfig(sitePaths.jgit_config.toFile(), FS.DETECTED);
    try {
      jgitConfig.load();
      if (!jgitConfig
          .getNames(ConfigConstants.CONFIG_PROTOCOL_SECTION)
          .contains(ConfigConstants.CONFIG_KEY_VERSION)) {
        jgitConfig.setString(
            ConfigConstants.CONFIG_PROTOCOL_SECTION,
            null,
            ConfigConstants.CONFIG_KEY_VERSION,
            TransferConfig.ProtocolVersion.V0.version());
        jgitConfig.save();
        ui.error(
            String.format(
                "Auto-configured \"%s.%s = %s\" to git wire protocol version 0.",
                ConfigConstants.CONFIG_PROTOCOL_SECTION,
                ConfigConstants.CONFIG_KEY_VERSION,
                TransferConfig.ProtocolVersion.V0.version()));
      }
    } catch (IOException e) {
      throw die(String.format("Handling JGit configuration %s failed", sitePaths.jgit_config), e);
    } catch (ConfigInvalidException e) {
      throw die(String.format("Invalid JGit configuration %s", sitePaths.jgit_config), e);
    }
  }
}
