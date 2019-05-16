// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.RepositorySizeQuotaEnforcer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

public class LazyPostReceiveHookChain implements PostReceiveHook {
  interface Factory {
    LazyPostReceiveHookChain create(CurrentUser user, Project.NameKey project);
  }

  private final PluginSetContext<PostReceiveHook> hooks;
  private final RepositorySizeQuotaEnforcer repoSizeEnforcer;
  private final CurrentUser user;
  private final Project.NameKey project;

  @Inject
  LazyPostReceiveHookChain(
      PluginSetContext<PostReceiveHook> hooks,
      RepositorySizeQuotaEnforcer repoSizeEnforcer,
      @Assisted CurrentUser user,
      @Assisted Project.NameKey project) {
    this.hooks = hooks;
    this.repoSizeEnforcer = repoSizeEnforcer;
    this.user = user;
    this.project = project;
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    hooks.runEach(h -> h.onPostReceive(rp, commands));
    if (needPack(commands)) {
      repoSizeEnforcer.requestSize(user, project, rp.getPackSize());
    }
  }

  public static boolean needPack(Collection<ReceiveCommand> commands) {
    for (ReceiveCommand cmd : commands) {
      if (cmd.getType() != ReceiveCommand.Type.DELETE) {
        return true;
      }
    }
    return false;
  }
}
