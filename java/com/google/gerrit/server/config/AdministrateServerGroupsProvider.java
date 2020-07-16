// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.config;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ServerRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

/** Loads {@link AdministrateServerGroups} from {@code gerrit.config}. */
public class AdministrateServerGroupsProvider implements Provider<ImmutableSet<GroupReference>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ImmutableSet<GroupReference> groups;

  @Inject
  public AdministrateServerGroupsProvider(
      GroupBackend groupBackend,
      @GerritServerConfig Config config,
      ThreadLocalRequestContext threadContext,
      ServerRequestContext serverCtx) {
    RequestContext ctx = threadContext.setContext(serverCtx);
    try {
      ImmutableSet.Builder<GroupReference> builder = ImmutableSet.builder();
      for (String value : config.getStringList("capability", null, "administrateServer")) {
        PermissionRule rule = PermissionRule.fromString(value, false);
        String name = rule.getGroup().getName();
        GroupReference g = GroupBackends.findBestSuggestion(groupBackend, name);
        if (g != null) {
          builder.add(g);
        } else {
          logger.atWarning().log("Group \"%s\" not available, skipping.", name);
        }
      }
      groups = builder.build();
    } finally {
      threadContext.setContext(ctx);
    }
  }

  @Override
  public ImmutableSet<GroupReference> get() {
    return groups;
  }
}
