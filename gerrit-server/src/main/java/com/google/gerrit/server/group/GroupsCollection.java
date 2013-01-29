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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
import com.google.gerrit.server.account.GroupControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GroupsCollection implements
    RestCollection<TopLevelResource, GroupResource>,
    AcceptsCreate<TopLevelResource> {
  private final DynamicMap<RestView<GroupResource>> views;
  private final Provider<ListGroups> list;
  private final CreateGroup.Factory createGroup;
  private final GroupControl.Factory groupControlFactory;
  private final GroupBackend groupBackend;
  private final Provider<CurrentUser> self;

  @Inject
  GroupsCollection(final DynamicMap<RestView<GroupResource>> views,
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
  public RestView<TopLevelResource> list() throws ResourceNotFoundException,
      AuthException {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if(!(user instanceof IdentifiedUser)) {
      throw new ResourceNotFoundException();
    }

    return list.get();
  }

  @Override
  public GroupResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if(!(user instanceof IdentifiedUser)) {
      throw new ResourceNotFoundException(id);
    }
    return parse(id.get());
  }

  GroupResource parse(String id) throws ResourceNotFoundException {
    try {
      AccountGroup.UUID uuid = new AccountGroup.UUID(id);
      if (groupBackend.handles(uuid)) {
        return check(id, groupControlFactory.controlFor(uuid));
      }
    } catch (NoSuchGroupException noSuchGroup) {
    }

    // Might be a legacy AccountGroup.Id.
    if (id.matches("^[1-9][0-9]*$")) {
      try {
        AccountGroup.Id legacyId = AccountGroup.Id.parse(id);
        return check(id, groupControlFactory.controlFor(legacyId));
      } catch (IllegalArgumentException invalidId) {
      } catch (NoSuchGroupException e) {
      }
    }

    // Might be a group name, be nice and accept unique names.
    GroupReference ref = GroupBackends.findExactSuggestion(groupBackend, id);
    if (ref != null) {
      try {
        return check(id, groupControlFactory.controlFor(ref.getUUID()));
      } catch (NoSuchGroupException e) {
      }
    }

    throw new ResourceNotFoundException(id);
  }

  private static GroupResource check(String id, GroupControl ctl)
      throws ResourceNotFoundException {
    if (ctl.isVisible()) {
      return new GroupResource(ctl);
    }
    throw new ResourceNotFoundException(id);
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
