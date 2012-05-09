// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.server.documentation.MarkdownFormatter;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.gerrit.sshd.SshCommand;

import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;

import java.util.List;

final class GetDocsCommand extends SshCommand {
  @Inject
  private PluginLoader loader;

  @Argument(index = 0, required = true, metaVar = "PLUGIN_NAME", usage = "Plugin name")
  private String pluginName;

  @Argument(index = 1, required = true, metaVar = "CMD_NAME", usage = "Command name to get docs for")
  private String cmdName;

  @Override
  protected void run() throws Failure {
    List<Plugin> running = loader.getPlugins();
    for (Plugin plugin : running) {
      if (plugin.getName() == pluginName) {
        // Get the doc (from the jar file?)
        stdout.print("Plugin name " + pluginName + "\n");
        stdout.print("Command name " + cmdName + "\n");
      }
    }
  }
}
