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
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.AddIncludedGroups.PutIncludedGroup;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class IncludedGroupsCollection implements
    ChildCollection<GroupResource, IncludedGroupResource>,
    AcceptsCreate<GroupResource> {
  private final DynamicMap<RestView<IncludedGroupResource>> views;
  private final Provider<ListIncludedGroups> list;
  private final Provider<GroupsCollection> groupsCollection;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<AddIncludedGroups> put;

  @Inject
  IncludedGroupsCollection(DynamicMap<RestView<IncludedGroupResource>> views,
      Provider<ListIncludedGroups> list,
      Provider<GroupsCollection> groupsCollection,
      Provider<ReviewDb> dbProvider, Provider<AddIncludedGroups> put) {
    this.views = views;
    this.list = list;
    this.groupsCollection = groupsCollection;
    this.dbProvider = dbProvider;
    this.put = put;
  }

  @Override
  public RestView<GroupResource> list() {
    return list.get();
  }

  @Override
  public IncludedGroupResource parse(GroupResource resource, IdString id)
      throws ResourceNotFoundException, OrmException {
    AccountGroup parent = resource.toAccountGroup();
    if (parent == null) {
      throw new ResourceNotFoundException(id);
    }

    GroupDescription.Basic member = groupsCollection.get().parse(id.get());
    if (isMember(parent, member)
        && resource.getControl().canSeeGroup(member.getGroupUUID())) {
      return new IncludedGroupResource(resource, member);
    }
    throw new ResourceNotFoundException(id);
  }

  private boolean isMember(AccountGroup parent, GroupDescription.Basic member)
      throws OrmException {
    return dbProvider.get().accountGroupIncludesByUuid().get(
        new AccountGroupIncludeByUuid.Key(
            parent.getId(),
            member.getGroupUUID())) != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public PutIncludedGroup create(GroupResource group, IdString id) {
    return new PutIncludedGroup(put, id.get());
  }

  @Override
  public DynamicMap<RestView<IncludedGroupResource>> views() {
    return views;
  }
}
