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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectCache;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

class LdapGroupMembership implements GroupMembership {
  private final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache;
  private final ProjectCache projectCache;
  private final String id;
  private GroupMembership membership;

  LdapGroupMembership(
      LoadingCache<String, Set<AccountGroup.UUID>> membershipCache,
      ProjectCache projectCache,
      String id) {
    this.membershipCache = membershipCache;
    this.projectCache = projectCache;
    this.id = id;
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
    g.retainAll(projectCache.guessRelevantGroupUUIDs());
    return g;
  }

  private synchronized GroupMembership get() {
    if (membership == null) {
      try {
        membership = new ListGroupMembership(membershipCache.get(id));
      } catch (ExecutionException e) {
        LdapGroupBackend.log.warn(String.format("Cannot lookup membershipsOf %s in LDAP", id), e);
        membership = GroupMembership.EMPTY;
      }
    }
    return membership;
  }
}
