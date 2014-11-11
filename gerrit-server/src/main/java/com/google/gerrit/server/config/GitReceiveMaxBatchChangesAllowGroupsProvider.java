// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.util.ServerRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;

public class GitReceiveMaxBatchChangesAllowGroupsProvider extends
    GroupSetProvider {
  @Inject
  public GitReceiveMaxBatchChangesAllowGroupsProvider(GroupBackend gb,
      @GerritServerConfig Config config,
      ThreadLocalRequestContext threadContext, ServerRequestContext serverCtx) {
    super(gb, config, threadContext, serverCtx, "receive", null,
        "maxBatchChangesAllowGroup");

    // If no group was set, default to "Project Owners".
    //
    if (groupIds.isEmpty()) {
      groupIds = Collections.singleton(SystemGroupBackend.PROJECT_OWNERS);
    }
  }
}
