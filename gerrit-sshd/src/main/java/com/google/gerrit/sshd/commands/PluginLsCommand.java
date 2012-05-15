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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.plugins.ListPlugins;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;

import java.io.IOException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
final class PluginLsCommand extends BaseCommand {
  @Inject
  private ListPlugins impl;

  @Override
  public void start(Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine(impl);
        impl.display(out);
      }
    });
  }
}
