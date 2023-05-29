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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** Access control management for a group of accounts managed in Gerrit. */
public class GroupControl {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class GenericFactory {
    private final PermissionBackend permissionBackend;
    private final GroupBackend groupBackend;

    @Inject
    GenericFactory(PermissionBackend permissionBackend, GroupBackend gb) {
      this.permissionBackend = permissionBackend;
      groupBackend = gb;
    }

    public GroupControl controlFor(CurrentUser who, AccountGroup.UUID groupId)
        throws NoSuchGroupException {
      GroupDescription.Basic group = groupBackend.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return new GroupControl(who, group, permissionBackend, groupBackend);
    }
  }

  public static class Factory {
    private final PermissionBackend permissionBackend;
    private final Provider<CurrentUser> user;
    private final GroupBackend groupBackend;

    @Inject
    Factory(PermissionBackend permissionBackend, Provider<CurrentUser> cu, GroupBackend gb) {
      this.permissionBackend = permissionBackend;
      user = cu;
      groupBackend = gb;
    }

    public GroupControl controlFor(AccountGroup.UUID groupId) throws NoSuchGroupException {
      final GroupDescription.Basic group = groupBackend.get(groupId);
      if (group == null) {
        throw new NoSuchGroupException(groupId);
      }
      return controlFor(group);
    }

    public GroupControl controlFor(GroupDescription.Basic group) {
      return new GroupControl(user.get(), group, permissionBackend, groupBackend);
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
  private final PermissionBackend.WithUser perm;
  private final GroupBackend groupBackend;

  GroupControl(
      CurrentUser who,
      GroupDescription.Basic gd,
      PermissionBackend permissionBackend,
      GroupBackend gb) {
    user = who;
    group = gd;
    this.perm = permissionBackend.user(user);
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
    if (user.isInternalUser()) {
      logger.atFine().log(
          "group %s is visible to internal user %s",
          group.getGroupUUID().get(), user.getLoggableName());
      return true;
    }

    if (groupBackend.isVisibleToAll(group.getGroupUUID())) {
      logger.atFine().log(
          "group %s is visible to user %s (group is visible to all users)",
          group.getGroupUUID().get(), user.getLoggableName());
      return true;
    }

    if (user.getEffectiveGroups().contains(group.getGroupUUID())) {
      logger.atFine().log(
          "group %s is visible to user %s (user is member of the group)",
          group.getGroupUUID().get(), user.getLoggableName());
      return true;
    }

    if (isOwner()) {
      logger.atFine().log(
          "group %s is visible to user %s (user is owner of the group)",
          group.getGroupUUID().get(), user.getLoggableName());
      return true;
    }

    // The check for canAdministrateServer may seem redundant, but it's needed to make external
    // groups visible to server administrators.
    if (canAdministrateServer()) {
      logger.atFine().log(
          "group %s is visible to user %s (user is admin)",
          group.getGroupUUID().get(), user.getLoggableName());
      return true;
    }

    logger.atFine().log(
        "group %s is not visible to user %s", group.getGroupUUID().get(), user.getLoggableName());
    return false;
  }

  public boolean isOwner() {
    if (isOwner != null) {
      return isOwner;
    }

    // Keep this logic in sync with DefaultRefFilter#isGroupOwner(...).
    if (group instanceof GroupDescription.Internal) {
      AccountGroup.UUID ownerUUID = ((GroupDescription.Internal) group).getOwnerGroupUUID();
      if (getUser().getEffectiveGroups().contains(ownerUUID)) {
        logger.atFine().log(
            "user %s is owner of group %s", user.getLoggableName(), group.getGroupUUID().get());
        isOwner = true;
      } else if (canAdministrateServer()) {
        logger.atFine().log(
            "user %s is owner of group %s (user is admin)",
            user.getLoggableName(), group.getGroupUUID().get());
        isOwner = true;
      } else {
        logger.atFine().log(
            "user %s is not an owner of group %s",
            user.getLoggableName(), group.getGroupUUID().get());
        isOwner = false;
      }
    } else {
      logger.atFine().log(
          "user %s is not an owner of external group %s",
          user.getLoggableName(), group.getGroupUUID().get());
      isOwner = false;
    }
    return isOwner;
  }

  private boolean canAdministrateServer() {
    try {
      return perm.test(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (PermissionBackendException e) {
      logger.atFine().log(
          "Failed to check %s global capability for user %s",
          GlobalPermission.ADMINISTRATE_SERVER, user.getLoggableName());
      return false;
    }
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
    if (group instanceof GroupDescription.Internal) {
      return ((GroupDescription.Internal) group).isVisibleToAll() || isOwner();
    }
    return canAdministrateServer();
  }

  public boolean canDeleteGroup() {
    return canAdministrateServer();
  }
}
