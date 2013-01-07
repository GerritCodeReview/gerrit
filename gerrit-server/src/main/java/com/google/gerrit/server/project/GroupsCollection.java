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

package com.google.gerrit.server.project;

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.util.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GroupsCollection implements
    RestCollection<TopLevelResource, GroupResource> {
  public final static String UUID_PREFIX = "uuid-";

  private final DynamicMap<RestView<GroupResource>> views;
  private final Provider<ListGroups> list;
  private final GroupControl.Factory groupControlFactory;

  @Inject
  GroupsCollection(final DynamicMap<RestView<GroupResource>> views,
      final Provider<ListGroups> list, final GroupControl.Factory groupControlFactory) {
    this.views = views;
    this.list = list;
    this.groupControlFactory = groupControlFactory;
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public GroupResource parse(TopLevelResource parent, String id)
      throws ResourceNotFoundException, Exception {
    String decodedId = Url.decode(id);
    GroupControl ctl;
    try {
      if (decodedId.startsWith(UUID_PREFIX)) {
        final String uuid = decodedId.substring(UUID_PREFIX.length());
        ctl = groupControlFactory.controlFor(new AccountGroup.UUID(uuid));
      } else {
        try {
          ctl = groupControlFactory.controlFor(new AccountGroup.Id(Integer.parseInt(decodedId)));
        } catch (NumberFormatException e) {
          throw new ResourceNotFoundException(id);
        }
      }
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(id);
    }
    if (!ctl.isVisible() && !ctl.isOwner()) {
      throw new ResourceNotFoundException(id);
    }
    return new GroupResource(ctl);
  }

  @Override
  public DynamicMap<RestView<GroupResource>> views() {
    return views;
  }
}
