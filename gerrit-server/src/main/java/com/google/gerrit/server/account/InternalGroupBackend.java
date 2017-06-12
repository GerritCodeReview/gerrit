// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static java.util.stream.Collectors.toList;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import org.eclipse.jgit.lib.ObjectId;

/** Implementation of GroupBackend for the internal group system. */
@Singleton
public class InternalGroupBackend implements GroupBackend {
  private final GroupControl.Factory groupControlFactory;
  private final GroupCache groupCache;
  private final IncludingGroupMembership.Factory groupMembershipFactory;

  @Inject
  InternalGroupBackend(
      GroupControl.Factory groupControlFactory,
      GroupCache groupCache,
      IncludingGroupMembership.Factory groupMembershipFactory) {
    this.groupControlFactory = groupControlFactory;
    this.groupCache = groupCache;
    this.groupMembershipFactory = groupMembershipFactory;
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    // See AccountGroup.isInternalGroup
    return ObjectId.isId(uuid.get()); // [0-9a-f]{40};
  }

  @Override
  public GroupDescription.Internal get(AccountGroup.UUID uuid) {
    if (!handles(uuid)) {
      return null;
    }

    AccountGroup g = groupCache.get(uuid);
    if (g == null) {
      return null;
    }
    return GroupDescriptions.forAccountGroup(g);
  }

  @Override
  public Collection<GroupReference> suggest(final String name, final ProjectControl project) {
    return groupCache
        .all()
        .stream()
        .filter(
            group ->
                // startsWithIgnoreCase && isVisible
                group.getName().regionMatches(true, 0, name, 0, name.length())
                    && groupControlFactory.controlFor(group).isVisible())
        .map(GroupReference::forGroup)
        .collect(toList());
  }

  @Override
  public GroupMembership membershipsOf(IdentifiedUser user) {
    return groupMembershipFactory.create(user);
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    GroupDescription.Internal g = get(uuid);
    return g != null && g.getAccountGroup().isVisibleToAll();
  }
}
