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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.util.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GroupsCollection implements
    RestCollection<TopLevelResource, GroupResource> {
  private final DynamicMap<RestView<GroupResource>> views;
  private final Provider<ListGroups> list;
  private final GroupControl.Factory groupControlFactory;
  private final Provider<CurrentUser> self;

  @Inject
  GroupsCollection(final DynamicMap<RestView<GroupResource>> views,
      final Provider<ListGroups> list,
      final GroupControl.Factory groupControlFactory,
      final Provider<CurrentUser> self) {
    this.views = views;
    this.list = list;
    this.groupControlFactory = groupControlFactory;
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
  public GroupResource parse(TopLevelResource parent, String id)
      throws ResourceNotFoundException, Exception {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if(!(user instanceof IdentifiedUser)) {
      throw new ResourceNotFoundException(id);
    }
    return parse(id, groupControlFactory);
  }

  public static GroupResource parse(String id, GroupControl.Factory factory)
      throws ResourceNotFoundException {
    String uuid = Url.decode(id);
    GroupControl ctl;
    try {
      ctl = factory.controlFor(new AccountGroup.UUID(uuid));
    } catch (NoSuchGroupException noSuchGroup) {
      if (uuid.matches("^[1-9][0-9]*$")) {
        // Might be a legacy AccountGroup.Id.
        try {
          ctl = factory.controlFor(AccountGroup.Id.parse(uuid));
        } catch (IllegalArgumentException invalidId) {
          throw new ResourceNotFoundException(id);
        } catch (NoSuchGroupException e) {
          throw new ResourceNotFoundException(id);
        }
      } else {
        throw new ResourceNotFoundException(id);
      }
    }
    if (ctl.isOwner()) {
      return new GroupResource(ctl);
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<GroupResource>> views() {
    return views;
  }
}
