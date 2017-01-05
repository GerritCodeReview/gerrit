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

import com.google.common.base.Optional;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Argument;

import java.util.ArrayList;
import java.util.List;

public class LfsPluginAuthCommand extends SshCommand {
  public interface LfsSshPluginAuth {
    String authenticate(CurrentUser user, List<String> args)
        throws UnloggedFailure, Failure, Exception;
  }

  public static class Module extends CommandModule {
    @Override
    protected void configure() {
      command("git-lfs-authenticate").to(LfsPluginAuthCommand.class);
      DynamicItem.itemOf(binder(), LfsSshPluginAuth.class);
    }
  }

  private static final LfsSshPluginAuth NOT_IMPLEMENTED =
      new LfsSshPluginAuth() {
        @Override
        public String authenticate(CurrentUser user, List<String> args)
            throws UnloggedFailure, Failure, Exception {
          throw new Failure(1, "Server configuration error:"
              + " LFS auth over SSH is not properly configured.");
        }
      };

  private final LfsSshPluginAuth auth;
  private final CurrentUser user;

  @Argument(index = 0, multiValued = true, metaVar = "PARAMS")
  private List<String> args = new ArrayList<>();

  @Inject
  LfsPluginAuthCommand(DynamicItem<LfsSshPluginAuth> auth,
      Provider<CurrentUser> user) {
    this.auth = Optional.fromNullable(auth.get()).or(NOT_IMPLEMENTED);
    this.user = user.get();
  }

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    stdout.print(auth.authenticate(user, args));
  }
}
