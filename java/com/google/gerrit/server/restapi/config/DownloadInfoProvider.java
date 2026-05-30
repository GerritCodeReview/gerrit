// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.DownloadInfo;
import com.google.gerrit.extensions.common.DownloadSchemeInfo;
import com.google.gerrit.extensions.config.CloneCommand;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.server.change.ArchiveFormatInternal;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.restapi.change.AllowedFormats;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.HashMap;

public class DownloadInfoProvider implements Provider<DownloadInfo> {
  private final PluginMapContext<DownloadScheme> downloadSchemes;
  private final PluginMapContext<DownloadCommand> downloadCommands;
  private final PluginMapContext<CloneCommand> cloneCommands;
  private final AllowedFormats archiveFormats;

  @Inject
  public DownloadInfoProvider(
      PluginMapContext<DownloadScheme> downloadSchemes,
      PluginMapContext<DownloadCommand> downloadCommands,
      PluginMapContext<CloneCommand> cloneCommands,
      AllowedFormats archiveFormats) {
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
    this.cloneCommands = cloneCommands;
    this.archiveFormats = archiveFormats;
  }

  @Override
  public DownloadInfo get() {
    DownloadInfo info = new DownloadInfo();
    info.schemes = new HashMap<>();
    downloadSchemes.runEach(
        extension -> {
          DownloadScheme scheme = extension.get();
          if (scheme.isEnabled() && !scheme.isHidden() && scheme.getUrl("${project}") != null) {
            info.schemes.put(extension.getExportName(), getDownloadSchemeInfo(scheme));
          }
        });
    info.archives =
        archiveFormats.getAllowed().stream()
            .map(ArchiveFormatInternal::getShortName)
            .collect(toList());
    return info;
  }

  private DownloadSchemeInfo getDownloadSchemeInfo(DownloadScheme scheme) {
    DownloadSchemeInfo info = new DownloadSchemeInfo();
    info.url = scheme.getUrl("${project}");
    info.description = scheme.getDescription();
    info.isAuthRequired = toBoolean(scheme.isAuthRequired());
    info.isAuthSupported = toBoolean(scheme.isAuthSupported());

    info.commands = new HashMap<>();
    downloadCommands.runEach(
        extension -> {
          String commandName = extension.getExportName();
          DownloadCommand command = extension.get();
          String c = command.getCommand(scheme, "${project}", "${ref}");
          if (c != null) {
            info.commands.put(commandName, c);
          }
        });

    info.cloneCommands = new HashMap<>();
    cloneCommands.runEach(
        extension -> {
          String commandName = extension.getExportName();
          CloneCommand command = extension.getProvider().get();
          String c = command.getCommand(scheme, "${project-path}/${project-base-name}");
          if (c != null) {
            c =
                c.replaceAll(
                    "\\$\\{project-path\\}/\\$\\{project-base-name\\}", "\\$\\{project\\}");
            info.cloneCommands.put(commandName, c);
          }
        });

    return info;
  }

  @Nullable
  private static Boolean toBoolean(boolean v) {
    return v ? Boolean.TRUE : null;
  }
}
