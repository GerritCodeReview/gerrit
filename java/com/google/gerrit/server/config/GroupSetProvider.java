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
// limitations under the License.

package com.google.gerrit.server.config;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ServerRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Provider;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses groups referenced in the {@code gerrit.config} file. */
public abstract class GroupSetProvider implements Provider<Set<AccountGroup.UUID>> {

  protected Set<AccountGroup.UUID> groupIds;

  protected GroupSetProvider(
      GroupBackend groupBackend,
      ThreadLocalRequestContext threadContext,
      ServerRequestContext serverCtx,
      List<String> groupNames) {
    RequestContext ctx = threadContext.setContext(serverCtx);
    try {
      ImmutableSet.Builder<AccountGroup.UUID> builder = ImmutableSet.builder();
      for (String n : groupNames) {
        GroupReference g = GroupBackends.findBestSuggestion(groupBackend, n);
        if (g != null) {
          builder.add(g.getUUID());
        } else {
          Logger log = LoggerFactory.getLogger(getClass());
          log.warn("Group \"{}\" not available, skipping.", n);
        }
      }
      groupIds = builder.build();
    } finally {
      threadContext.setContext(ctx);
    }
  }

  @Override
  public Set<AccountGroup.UUID> get() {
    return groupIds;
  }
}
