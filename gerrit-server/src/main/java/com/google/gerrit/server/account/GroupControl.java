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

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Access control management for a group of accounts managed in Gerrit. */
public class GroupControl {
  public static class Factory {
    private final GroupCache groupCache;
    private final Provider<CurrentUser> user;
    private final GroupBackend groupBackend;

    @Inject
    Factory(final GroupCache gc, final Provider<CurrentUser> cu,
        final GroupBackend gb) {
      groupCache = gc;
      user = cu;
      groupBackend = gb;
    }

    public GroupControl controlFor(final AccountGroup.Id groupId)
        throws NoSuchGroupException {
      final AccountGroup group = groupCache.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return new GroupControl(user.get(), group);
    }

    public GroupControl controlFor(final AccountGroup.UUID groupId)
        throws NoSuchGroupException {
      final GroupDescription.Basic group = groupBackend.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return new GroupControl(user.get(), group);
    }

    public GroupControl controlFor(final AccountGroup group) {
      return new GroupControl(user.get(), group);
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

  private final CurrentUser user;
  private final GroupDescription.Basic group;
  private Boolean isOwner;

  GroupControl(CurrentUser who, GroupDescription.Basic gd) {
    user = who;
    group =  gd;
  }

  GroupControl(CurrentUser who, AccountGroup ag) {
    this(who, GroupDescriptions.forAccountGroup(ag));
  }

  public CurrentUser getCurrentUser() {
    return user;
  }

  /** Can this user see this group exists? */
  public boolean isVisible() {
    return group.isVisibleToAll()
      || user.getEffectiveGroups().contains(group.getGroupUUID())
      || isOwner();
  }

  public boolean isOwner() {
    AccountGroup accountGroup = GroupDescriptions.toAccountGroup(group);
    if (accountGroup == null) {
      isOwner = false;
    } else if (isOwner == null) {
      AccountGroup.UUID ownerUUID = accountGroup.getOwnerGroupUUID();
      isOwner = getCurrentUser().getEffectiveGroups().contains(ownerUUID)
             || getCurrentUser().getCapabilities().canAdministrateServer();
    }
    return isOwner;
  }

  public boolean canAddMember(Account.Id id) {
    return isOwner();
  }

  public boolean canRemoveMember(Account.Id id) {
    return isOwner();
  }

  public boolean canSeeMember(Account.Id id) {
    if (user instanceof IdentifiedUser
        && ((IdentifiedUser) user).getAccountId().equals(id)) {
      return true;
    }
    return canSeeMembers();
  }

  public boolean canAddGroup(AccountGroup.UUID uuid) {
    return isOwner();
  }

  public boolean canRemoveGroup(AccountGroup.UUID uuid) {
    return isOwner();
  }

  public boolean canSeeGroup(AccountGroup.UUID uuid) {
    return canSeeMembers();
  }

  private boolean canSeeMembers() {
    return group.isVisibleToAll() || isOwner();
  }
}
