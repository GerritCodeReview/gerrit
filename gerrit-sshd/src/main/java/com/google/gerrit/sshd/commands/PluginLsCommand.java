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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.gerrit.sshd.RequiresCapability;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
final class PluginLsCommand extends SshCommand {
  @Inject
  private PluginLoader loader;

  @Override
  protected void run() {
    List<Plugin> running = loader.getPlugins();
    Collections.sort(running, new Comparator<Plugin>() {
      @Override
      public int compare(Plugin a, Plugin b) {
        return a.getName().compareTo(b.getName());
      }
    });

    stdout.format("%-30s %-10s\n", "Name", "Version");
    stdout.print("----------------------------------------------------------------------\n");
    for (Plugin p : running) {
      stdout.format("%-30s %-10s\n", p.getName(),
          Strings.nullToEmpty(p.getVersion()));
    }
  }
}
