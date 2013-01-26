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
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class IncludedGroupsCollection implements
    ChildCollection<GroupResource, IncludedGroupResource> {
  private final DynamicMap<RestView<IncludedGroupResource>> views;
  private final Provider<ListIncludedGroups> list;
  private final Provider<GroupsCollection> groupsCollection;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  IncludedGroupsCollection(DynamicMap<RestView<IncludedGroupResource>> views,
      Provider<ListIncludedGroups> list,
      Provider<GroupsCollection> groupsCollection,
      Provider<ReviewDb> dbProvider) {
    this.views = views;
    this.list = list;
    this.groupsCollection = groupsCollection;
    this.dbProvider = dbProvider;
  }

  @Override
  public RestView<GroupResource> list() {
    return list.get();
  }

  @Override
  public IncludedGroupResource parse(GroupResource parent, String id)
      throws ResourceNotFoundException, OrmException {
    if (!parent.isInternal()) {
      throw new ResourceNotFoundException(id);
    }

    GroupDescription.Internal p = (GroupDescription.Internal) parent.getGroup();
    GroupResource included = groupsCollection.get().parse(id);
    AccountGroupIncludeByUuid in = dbProvider.get()
        .accountGroupIncludesByUuid().get(
          new AccountGroupIncludeByUuid.Key(
              p.getAccountGroup().getId(),
              included.getGroupUUID()));
    if (in != null) {
      return new IncludedGroupResource(included.getControl());
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<IncludedGroupResource>> views() {
    return views;
  }
}
