// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
public class GroupResolver {
  private final GroupBackend groupBackend;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;

  @Inject
  GroupResolver(
      GroupBackend groupBackend, GroupCache groupCache, GroupControl.Factory groupControlFactory) {
    this.groupBackend = groupBackend;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
  }

  /**
   * Parses a group ID from a request body and returns the group.
   *
   * @param id ID of the group, can be a group UUID, a group name or a legacy group ID
   * @return the group
   * @throws UnprocessableEntityException thrown if the group ID cannot be resolved or if the group
   *     is not visible to the calling user
   */
  public GroupDescription.Basic parse(String id) throws UnprocessableEntityException {
    GroupDescription.Basic group = parseId(id);
    if (group == null || !groupControlFactory.controlFor(group).isVisible()) {
      throw new UnprocessableEntityException(String.format("Group Not Found: %s", id));
    }
    return group;
  }

  /**
   * Parses a group ID from a request body and returns the group if it is a Gerrit internal group.
   *
   * @param id ID of the group, can be a group UUID, a group name or a legacy group ID
   * @return the group
   * @throws UnprocessableEntityException thrown if the group ID cannot be resolved, if the group is
   *     not visible to the calling user or if it's an external group
   */
  public GroupDescription.Internal parseInternal(String id) throws UnprocessableEntityException {
    GroupDescription.Basic group = parse(id);
    if (group instanceof GroupDescription.Internal) {
      return (GroupDescription.Internal) group;
    }

    throw new UnprocessableEntityException(String.format("External Group Not Allowed: %s", id));
  }

  /**
   * Parses a group ID and returns the group without making any permission check whether the current
   * user can see the group.
   *
   * @param id ID of the group, can be a group UUID, a group name or a legacy group ID
   * @return the group, null if no group is found for the given group ID
   */
  public GroupDescription.Basic parseId(String id) {
    AccountGroup.UUID uuid = AccountGroup.uuid(id);
    if (groupBackend.handles(uuid)) {
      GroupDescription.Basic d = groupBackend.get(uuid);
      if (d != null) {
        return d;
      }
    }

    // Might be a numeric AccountGroup.Id. -> Internal group.
    if (id.matches("^[1-9][0-9]*$")) {
      try {
        AccountGroup.Id groupId = AccountGroup.Id.parse(id);
        Optional<InternalGroup> group = groupCache.get(groupId);
        if (group.isPresent()) {
          return new InternalGroupDescription(group.get());
        }
      } catch (IllegalArgumentException e) {
        // Ignored
      }
    }

    // Might be a group name, be nice and accept unique names.
    GroupReference ref = GroupBackends.findExactSuggestion(groupBackend, id);
    if (ref != null) {
      GroupDescription.Basic d = groupBackend.get(ref.getUUID());
      if (d != null) {
        return d;
      }
    }

    return null;
  }
}
