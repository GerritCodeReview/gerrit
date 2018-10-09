// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.common.FetchInfo;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import java.util.TreeMap;

/** Populates the {@link FetchInfo} which is serialized to JSON afterwards. */
public class DownloadCommandsJson {

  private DownloadCommandsJson() {}

  /**
   * Populates the provided {@link FetchInfo} by calling all {@link DownloadCommand} extensions.
   * Will mutate {@link FetchInfo#commands}.
   */
  public static void populateFetchMap(
      DownloadScheme scheme,
      DynamicMap<DownloadCommand> commands,
      String projectName,
      String refName,
      FetchInfo fetchInfo) {
    for (Extension<DownloadCommand> ext : commands) {
      String commandName = ext.getExportName();
      DownloadCommand command = ext.getProvider().get();
      String c = command.getCommand(scheme, projectName, refName);
      if (c != null) {
        if (fetchInfo.commands == null) {
          fetchInfo.commands = new TreeMap<>();
        }
        fetchInfo.commands.put(commandName, c);
      }
    }
  }
}
