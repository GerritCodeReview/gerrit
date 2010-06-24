// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReplicationUser extends CurrentUser {
  /** Magic set of groups enabling read of any project and reference. */
  public static final Set<AccountGroup.Id> EVERYTHING_VISIBLE =
      Collections.unmodifiableSet(new HashSet<AccountGroup.Id>(0));

  public interface Factory {
    ReplicationUser create(@Assisted Set<AccountGroup.Id> authGroups);
  }

  private final Set<AccountGroup.Id> effectiveGroups;

  @Inject
  protected ReplicationUser(AuthConfig authConfig,
      @Assisted Set<AccountGroup.Id> authGroups) {
    super(AccessPath.REPLICATION, authConfig);

    if (authGroups == EVERYTHING_VISIBLE) {
      effectiveGroups = EVERYTHING_VISIBLE;

    } else if (authGroups.isEmpty()) {
      // Only include the registered groups if no specific groups
      // were provided. This allows an administrator to configure
      // a replication user with a narrower view of the system than
      // all other users, such as when replicating from an internal
      // company server to a public open source distribution site.
      //
      effectiveGroups = authConfig.getRegisteredGroups();

    } else {
      effectiveGroups = copy(authGroups);
    }
  }

  private static Set<AccountGroup.Id> copy(Set<AccountGroup.Id> groups) {
    return Collections.unmodifiableSet(new HashSet<AccountGroup.Id>(groups));
  }

  @Override
  public Set<AccountGroup.Id> getEffectiveGroups() {
    return effectiveGroups;
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    return Collections.emptySet();
  }

  @Override
  public Set<NameKey> getWatchedProjects() {
    return Collections.emptySet();
  }

  public boolean isEverythingVisible() {
    return getEffectiveGroups() == EVERYTHING_VISIBLE;
  }
}
