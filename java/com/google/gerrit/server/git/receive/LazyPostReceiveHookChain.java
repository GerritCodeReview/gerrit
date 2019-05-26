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

import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Class is responsible for calling all registered post-receive hooks. In addition, in case when
 * repository size quota is defined, it requests tokens (pack size) that were received. This is the
 * final step of enforcing repository size quota that deducts token from available tokens.
 */
public class LazyPostReceiveHookChain implements PostReceiveHook {
  interface Factory {
    LazyPostReceiveHookChain create(CurrentUser user, Project.NameKey project);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<PostReceiveHook> hooks;
  private final QuotaBackend quotaBackend;
  private final CurrentUser user;
  private final Project.NameKey project;

  @Inject
  LazyPostReceiveHookChain(
      PluginSetContext<PostReceiveHook> hooks,
      QuotaBackend quotaBackend,
      @Assisted CurrentUser user,
      @Assisted Project.NameKey project) {
    this.hooks = hooks;
    this.quotaBackend = quotaBackend;
    this.user = user;
    this.project = project;
  }

  @Override
  public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    hooks.runEach(h -> h.onPostReceive(rp, commands));
    if (affectsSize(rp, commands)) {
      QuotaResponse.Aggregated a =
          quotaBackend
              .user(user)
              .project(project)
              .requestTokens(REPOSITORY_SIZE_GROUP, rp.getPackSize());
      if (a.hasError()) {
        String msg =
            String.format(
                "%s request failed for project %s with [%s]",
                REPOSITORY_SIZE_GROUP, project, a.errorMessage());
        logger.atWarning().log(msg);
        throw new RuntimeException(msg);
      }
    }
  }

  public static boolean affectsSize(ReceivePack rp, Collection<ReceiveCommand> commands) {
    if (rp.getPackSize() > 0L) {
      for (ReceiveCommand cmd : commands) {
        if (cmd.getType() != ReceiveCommand.Type.DELETE) {
          return true;
        }
      }
    }
    return false;
  }
}
