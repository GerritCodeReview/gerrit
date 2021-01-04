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
          .getNames(ConfigConstants.CONFIG_RECEIVE_SECTION)
          .contains(ConfigConstants.CONFIG_KEY_AUTOGC)) {
        jgitConfig.setBoolean(
            ConfigConstants.CONFIG_RECEIVE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOGC, false);
        jgitConfig.save();
        ui.error(
            "Auto-configured \"receive.autogc = false\" to disable auto-gc after git-receive-pack.");
      } else if (jgitConfig.getBoolean(
          ConfigConstants.CONFIG_RECEIVE_SECTION, ConfigConstants.CONFIG_KEY_AUTOGC, true)) {
        ui.error(
            "WARNING: JGit option \"receive.autogc = true\". This is not recommended in Gerrit.\n"
                + "git-receive-pack will run auto gc after receiving data from "
                + "git-push and updating refs.\n"
                + "Disable this behavior to avoid the additional load it creates: "
                + "gc should be configured in gc config section or run as a separate process.");
      }

      if (jgitConfig
          .getNames(ConfigConstants.CONFIG_PROTOCOL_SECTION)
          .contains(ConfigConstants.CONFIG_KEY_VERSION)) {
        String version =
            jgitConfig.getString(
                ConfigConstants.CONFIG_PROTOCOL_SECTION, null, ConfigConstants.CONFIG_KEY_VERSION);
        if (!TransferConfig.ProtocolVersion.V2.version().equals(version)) {
          ui.error(
              String.format(
                  "HINT: JGit option \"%s.%s = %s\". It's recommended to activate git\n"
                      + "wire protocol version 2 to improve git fetch performance.",
                  ConfigConstants.CONFIG_PROTOCOL_SECTION,
                  ConfigConstants.CONFIG_KEY_VERSION,
                  version));
        }
      }
    } catch (IOException e) {
      throw die(String.format("Handling JGit configuration %s failed", sitePaths.jgit_config), e);
    } catch (ConfigInvalidException e) {
      throw die(String.format("Invalid JGit configuration %s", sitePaths.jgit_config), e);
    }
  }
}
