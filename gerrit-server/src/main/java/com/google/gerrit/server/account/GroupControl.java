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

package com.google.gerrit.server.account;

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Access control management for a group of accounts managed in Gerrit. */
public class GroupControl {
  public static class Factory {
    private final GroupCache groupCache;
    private final Provider<CurrentUser> user;

    @Inject
    Factory(final GroupCache gc, final Provider<CurrentUser> cu) {
      groupCache = gc;
      user = cu;
    }

    public GroupControl controlFor(final AccountGroup.Id groupId)
        throws NoSuchGroupException {
      final AccountGroup group = groupCache.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return new GroupControl(groupCache, user.get(), group);
    }

    public GroupControl controlFor(final AccountGroup.UUID groupId)
        throws NoSuchGroupException {
      final AccountGroup group = groupCache.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return new GroupControl(groupCache, user.get(), group);
    }

    public GroupControl controlFor(final AccountGroup group) {
      return new GroupControl(groupCache, user.get(), group);
    }

    public GroupControl validateFor(final AccountGroup.Id groupId)
        throws NoSuchGroupException {
      final GroupControl c = controlFor(groupId);
      if (!c.isVisible()) {
        throw new NoSuchGroupException(groupId);
      }
      return c;
    }
  }

  private final GroupCache groupCache;
  private final CurrentUser user;
  private final AccountGroup group;
  private Boolean isOwner;

  GroupControl(GroupCache g, CurrentUser who, AccountGroup gc) {
    groupCache = g;
    user = who;
    group = gc;
  }

  public CurrentUser getCurrentUser() {
    return user;
  }

  public AccountGroup getAccountGroup() {
    return group;
  }

  /** Can this user see this group exists? */
  public boolean isVisible() {
    return group.isVisibleToAll() || isOwner();
  }

  public boolean isOwner() {
    if (isOwner == null) {
      AccountGroup g = groupCache.get(group.getOwnerGroupId());
      AccountGroup.UUID ownerUUID = g != null ? g.getGroupUUID() : null;
      isOwner = getCurrentUser().getEffectiveGroups().contains(ownerUUID)
             || getCurrentUser().isAdministrator();
    }
    return isOwner;
  }

  public boolean canAddMember(final Account.Id id) {
    return isOwner();
  }

  public boolean canRemoveMember(final Account.Id id) {
    return isOwner();
  }

  public boolean canSeeMember(Account.Id id) {
    return isVisible();
  }

  public boolean canAddGroup(final AccountGroup.Id id) {
    return isOwner();
  }

  public boolean canRemoveGroup(final AccountGroup.Id id) {
    return isOwner();
  }

  public boolean canSeeGroup(AccountGroup.Id id) {
    return isVisible();
  }
}
