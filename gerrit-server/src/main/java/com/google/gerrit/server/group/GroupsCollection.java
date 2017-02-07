// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.account.GroupControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class GroupsCollection
    implements RestCollection<TopLevelResource, GroupResource>, AcceptsCreate<TopLevelResource> {
  private final DynamicMap<RestView<GroupResource>> views;
  private final Provider<ListGroups> list;
  private final CreateGroup.Factory createGroup;
  private final GroupControl.Factory groupControlFactory;
  private final GroupBackend groupBackend;
  private final Provider<CurrentUser> self;

  @Inject
  GroupsCollection(
      final DynamicMap<RestView<GroupResource>> views,
      final Provider<ListGroups> list,
      final CreateGroup.Factory createGroup,
      final GroupControl.Factory groupControlFactory,
      final GroupBackend groupBackend,
      final Provider<CurrentUser> self) {
    this.views = views;
    this.list = list;
    this.createGroup = createGroup;
    this.groupControlFactory = groupControlFactory;
    this.groupBackend = groupBackend;
    this.self = self;
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException, AuthException {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException();
    }

    return list.get();
  }

  @Override
  public GroupResource parse(TopLevelResource parent, IdString id)
      throws AuthException, ResourceNotFoundException {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException(id);
    }

    GroupDescription.Basic group = parseId(id.get());
    if (group == null) {
      throw new ResourceNotFoundException(id.get());
    }
    GroupControl ctl = groupControlFactory.controlFor(group);
    if (!ctl.isVisible()) {
      throw new ResourceNotFoundException(id);
    }
    return new GroupResource(ctl);
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
  public GroupDescription.Basic parseInternal(String id) throws UnprocessableEntityException {
    GroupDescription.Basic group = parse(id);
    if (GroupDescriptions.toAccountGroup(group) == null) {
      throw new UnprocessableEntityException(String.format("External Group Not Allowed: %s", id));
    }
    return group;
  }

  /**
   * Parses a group ID and returns the group without making any permission check whether the current
   * user can see the group.
   *
   * @param id ID of the group, can be a group UUID, a group name or a legacy group ID
   * @return the group, null if no group is found for the given group ID
   */
  public GroupDescription.Basic parseId(String id) {
    AccountGroup.UUID uuid = new AccountGroup.UUID(id);
    if (groupBackend.handles(uuid)) {
      GroupDescription.Basic d = groupBackend.get(uuid);
      if (d != null) {
        return d;
      }
    }

    // Might be a legacy AccountGroup.Id.
    if (id.matches("^[1-9][0-9]*$")) {
      try {
        AccountGroup.Id legacyId = AccountGroup.Id.parse(id);
        return groupControlFactory.controlFor(legacyId).getGroup();
      } catch (IllegalArgumentException | NoSuchGroupException e) {
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

  @SuppressWarnings("unchecked")
  @Override
  public CreateGroup create(TopLevelResource root, IdString name) {
    return createGroup.create(name.get());
  }

  @Override
  public DynamicMap<RestView<GroupResource>> views() {
    return views;
  }
}
