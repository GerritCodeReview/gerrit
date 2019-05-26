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

package com.google.gerrit.server.auth.ldap;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectCache;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;

class LdapGroupMembership implements GroupMembership {
  private final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache;
  private final ProjectCache projectCache;
  private final String id;
  private final boolean guessRelevantGroups;
  private GroupMembership membership;

  LdapGroupMembership(
      LoadingCache<String, Set<AccountGroup.UUID>> membershipCache,
      ProjectCache projectCache,
      String id,
      Config gerritConfig) {
    this.membershipCache = membershipCache;
    this.projectCache = projectCache;
    this.id = id;
    this.guessRelevantGroups = gerritConfig.getBoolean("ldap", "guessRelevantGroups", true);
  }

  @Override
  public boolean contains(AccountGroup.UUID groupId) {
    return get().contains(groupId);
  }

  @Override
  public boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds) {
    return get().containsAnyOf(groupIds);
  }

  @Override
  public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupIds) {
    return get().intersection(groupIds);
  }

  @Override
  public Set<AccountGroup.UUID> getKnownGroups() {
    Set<AccountGroup.UUID> g = new HashSet<>(get().getKnownGroups());
    if (guessRelevantGroups) {
      g.retainAll(projectCache.guessRelevantGroupUUIDs());
    }
    return g;
  }

  private synchronized GroupMembership get() {
    if (membership == null) {
      try {
        membership = new ListGroupMembership(membershipCache.get(id));
      } catch (ExecutionException e) {
        LdapGroupBackend.logger
            .atWarning()
            .withCause(e)
            .log("Cannot lookup membershipsOf %s in LDAP", id);
        membership = GroupMembership.EMPTY;
      }
    }
    return membership;
  }
}
