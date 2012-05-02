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
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public abstract class GroupSetProvider implements
    Provider<Set<AccountGroup.UUID>> {
  private static final Logger log =
      LoggerFactory.getLogger(GroupSetProvider.class);

  protected Set<AccountGroup.UUID> groupIds;

  @Inject
  protected GroupSetProvider(GroupBackend groupBackend,
      @GerritServerConfig Config config, String section,
      String subsection, String name) {
    String[] groupNames = config.getStringList(section, subsection, name);
    ImmutableSet.Builder<AccountGroup.UUID> builder = ImmutableSet.builder();
    for (String n : groupNames) {
      GroupReference g = GroupBackends.findBestSuggestion(groupBackend, n);
      if (g == null) {
        log.warn("Group \"{0}\" not in database, skipping.", n);
      } else {
        builder.add(g.getUUID());
      }
    }
    groupIds = builder.build();
  }

  @Override
  public Set<AccountGroup.UUID> get() {
    return groupIds;
  }
}
