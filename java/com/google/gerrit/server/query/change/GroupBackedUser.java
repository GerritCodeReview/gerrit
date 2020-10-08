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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import java.util.Set;

/**
 * Representation of a user that does not have a Gerrit account.
 *
 * <p>This user representation is intended to be used for two purposes:
 *
 * <ol>
 *   <li>Checking permissions for groups: There are occasions where we need to check if a resource -
 *       such as a change - is accessible by a group. Our entire {@link
 *       com.google.gerrit.server.permissions.PermissionBackend} works solely with {@link
 *       CurrentUser}. This class can be used to check permissions on a fake user with the given
 *       group memberships. Any real Gerrit user with the same group memberships would receive the
 *       same permission check results.
 *   <li>Checking permissions for an external user: In installations with external group systems,
 *       one might want to check what Gerrit permissions a user has, before or even without creating
 *       a Gerrit account. Such an external user has external group memberships only as well as
 *       internal groups that contain the user's external groups as subgroups. This class can be
 *       used to represent such an external user.
 * </ol>
 */
public final class GroupBackedUser extends CurrentUser {
  private final GroupMembership groups;

  /**
   * Creates a new instance
   *
   * @param groups this set has to include all parent groups the user is contained through subgroup
   *     membership. Given a set of groups that contains the user directly, callers can use {@link
   *     com.google.gerrit.server.account.GroupIncludeCache#parentGroupsOf(AccountGroup.UUID)} to
   *     resolve parent groups.
   */
  public GroupBackedUser(Set<AccountGroup.UUID> groups) {
    this.groups = new ListGroupMembership(groups);
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    return groups;
  }

  @Override
  public String getLoggableName() {
    return "GroupBackedUser with memberships: " + groups.getKnownGroups();
  }
}
