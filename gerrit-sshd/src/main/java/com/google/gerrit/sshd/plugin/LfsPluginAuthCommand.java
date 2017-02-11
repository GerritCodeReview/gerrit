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

package com.google.gerrit.sshd.plugin;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Argument;

public class LfsPluginAuthCommand extends SshCommand {
  public interface LfsSshPluginAuth {
    String authenticate(CurrentUser user, List<String> args) throws UnloggedFailure, Failure;
  }

  public static class Module extends CommandModule {
    private final boolean pluginProvided;

    @Inject
    Module(@GerritServerConfig Config cfg) {
      pluginProvided = cfg.getString("lfs", null, "plugin") != null;
    }

    @Override
    protected void configure() {
      if (pluginProvided) {
        command("git-lfs-authenticate").to(LfsPluginAuthCommand.class);
        DynamicItem.itemOf(binder(), LfsSshPluginAuth.class);
      }
    }
  }

  private final DynamicItem<LfsSshPluginAuth> auth;
  private final Provider<CurrentUser> user;

  @Argument(index = 0, multiValued = true, metaVar = "PARAMS")
  private List<String> args = new ArrayList<>();

  @Inject
  LfsPluginAuthCommand(DynamicItem<LfsSshPluginAuth> auth, Provider<CurrentUser> user) {
    this.auth = auth;
    this.user = user;
  }

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    LfsSshPluginAuth pluginAuth = auth.get();
    if (pluginAuth == null) {
      throw new Failure(
          1, "Server configuration error:" + " LFS auth over SSH is not properly configured.");
    }

    stdout.print(pluginAuth.authenticate(user.get(), args));
  }
}
