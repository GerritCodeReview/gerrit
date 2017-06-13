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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** Access control management for a group of accounts managed in Gerrit. */
public class GroupControl {

  @Singleton
  public static class GenericFactory {
    private final GroupBackend groupBackend;

    @Inject
    GenericFactory(GroupBackend gb) {
      groupBackend = gb;
    }

    public GroupControl controlFor(CurrentUser who, AccountGroup.UUID groupId)
        throws NoSuchGroupException {
      final GroupDescription.Basic group = groupBackend.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return new GroupControl(who, group, groupBackend);
    }
  }

  public static class Factory {
    private final GroupCache groupCache;
    private final Provider<CurrentUser> user;
    private final GroupBackend groupBackend;

    @Inject
    Factory(GroupCache gc, Provider<CurrentUser> cu, GroupBackend gb) {
      groupCache = gc;
      user = cu;
      groupBackend = gb;
    }

    public GroupControl controlFor(AccountGroup.Id groupId) throws NoSuchGroupException {
      final AccountGroup group = groupCache.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return controlFor(GroupDescriptions.forAccountGroup(group));
    }

    public GroupControl controlFor(AccountGroup.UUID groupId) throws NoSuchGroupException {
      final GroupDescription.Basic group = groupBackend.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return controlFor(group);
    }

    public GroupControl controlFor(AccountGroup group) {
      return controlFor(GroupDescriptions.forAccountGroup(group));
    }

    public GroupControl controlFor(GroupDescription.Basic group) {
      return new GroupControl(user.get(), group, groupBackend);
    }

    public GroupControl validateFor(AccountGroup.Id groupId) throws NoSuchGroupException {
      final GroupControl c = controlFor(groupId);
      if (!c.isVisible()) {
        throw new NoSuchGroupException(groupId);
      }
      return c;
    }

    public GroupControl validateFor(AccountGroup.UUID groupUUID) throws NoSuchGroupException {
      final GroupControl c = controlFor(groupUUID);
      if (!c.isVisible()) {
        throw new NoSuchGroupException(groupUUID);
      }
      return c;
    }
  }

  private final CurrentUser user;
  private final GroupDescription.Basic group;
  private Boolean isOwner;
  private final GroupBackend groupBackend;

  GroupControl(CurrentUser who, GroupDescription.Basic gd, GroupBackend gb) {
    user = who;
    group = gd;
    groupBackend = gb;
  }

  public GroupDescription.Basic getGroup() {
    return group;
  }

  public CurrentUser getUser() {
    return user;
  }

  /** Can this user see this group exists? */
  public boolean isVisible() {
    /* Check for canAdministrateServer may seem redundant, but allows
     * for visibility of all groups that are not an internal group to
     * server administrators.
     */
    return user.isInternalUser()
        || groupBackend.isVisibleToAll(group.getGroupUUID())
        || user.getEffectiveGroups().contains(group.getGroupUUID())
        || user.getCapabilities().isAdmin_DoNotUse()
        || isOwner();
  }

  public boolean isOwner() {
    AccountGroup accountGroup = GroupDescriptions.toAccountGroup(group);
    if (accountGroup == null) {
      isOwner = false;
    } else if (isOwner == null) {
      AccountGroup.UUID ownerUUID = accountGroup.getOwnerGroupUUID();
      isOwner =
          getUser().getEffectiveGroups().contains(ownerUUID)
              || getUser().getCapabilities().isAdmin_DoNotUse();
    }
    return isOwner;
  }

  public boolean canAddMember() {
    return isOwner();
  }

  public boolean canRemoveMember() {
    return isOwner();
  }

  public boolean canSeeMember(Account.Id id) {
    if (user.isIdentifiedUser() && user.getAccountId().equals(id)) {
      return true;
    }
    return canSeeMembers();
  }

  public boolean canAddGroup() {
    return isOwner();
  }

  public boolean canRemoveGroup() {
    return isOwner();
  }

  public boolean canSeeGroup() {
    return canSeeMembers();
  }

  private boolean canSeeMembers() {
    AccountGroup accountGroup = GroupDescriptions.toAccountGroup(group);
    return (accountGroup != null && accountGroup.isVisibleToAll()) || isOwner();
  }
}
