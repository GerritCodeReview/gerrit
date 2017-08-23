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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.AddSubgroups.PutSubgroup;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class SubgroupsCollection
    implements ChildCollection<GroupResource, SubgroupResource>, AcceptsCreate<GroupResource> {
  private final DynamicMap<RestView<SubgroupResource>> views;
  private final ListSubgroups list;
  private final GroupsCollection groupsCollection;
  private final Provider<ReviewDb> dbProvider;
  private final Groups groups;
  private final AddSubgroups addSubgroups;

  @Inject
  SubgroupsCollection(
      DynamicMap<RestView<SubgroupResource>> views,
      ListSubgroups list,
      GroupsCollection groupsCollection,
      Provider<ReviewDb> dbProvider,
      Groups groups,
      AddSubgroups addSubgroups) {
    this.views = views;
    this.list = list;
    this.groupsCollection = groupsCollection;
    this.dbProvider = dbProvider;
    this.groups = groups;
    this.addSubgroups = addSubgroups;
  }

  @Override
  public RestView<GroupResource> list() {
    return list;
  }

  @Override
  public SubgroupResource parse(GroupResource resource, IdString id)
      throws MethodNotAllowedException, AuthException, ResourceNotFoundException, OrmException {
    GroupDescription.Internal parent =
        resource.asInternalGroup().orElseThrow(MethodNotAllowedException::new);

    GroupDescription.Basic member =
        groupsCollection.parse(TopLevelResource.INSTANCE, id).getGroup();
    if (resource.getControl().canSeeGroup() && isSubgroup(parent, member)) {
      return new SubgroupResource(resource, member);
    }
    throw new ResourceNotFoundException(id);
  }

  private boolean isSubgroup(GroupDescription.Internal parent, GroupDescription.Basic member)
      throws OrmException, ResourceNotFoundException {
    try {
      return groups.isSubgroup(dbProvider.get(), parent.getGroupUUID(), member.getGroupUUID());
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(
          String.format("Group %s not found", parent.getGroupUUID()));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public PutSubgroup create(GroupResource group, IdString id) {
    return new PutSubgroup(addSubgroups, id.get());
  }

  @Override
  public DynamicMap<RestView<SubgroupResource>> views() {
    return views;
  }
}
