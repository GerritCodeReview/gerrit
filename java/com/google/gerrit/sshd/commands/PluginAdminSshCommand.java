// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public abstract class PluginAdminSshCommand extends SshCommand {
  @Inject protected PluginLoader loader;

  abstract void doRun() throws UnloggedFailure;

  @Override
  protected final void run() throws UnloggedFailure {
    enableGracefulStop();
    if (!loader.isRemoteAdminEnabled()) {
      throw die("remote plugin administration is disabled");
    }
    doRun();
  }
}
