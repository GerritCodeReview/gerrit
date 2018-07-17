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

package com.google.gerrit.server.restapi.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.SubgroupResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class SubgroupsCollection implements ChildCollection<GroupResource, SubgroupResource> {
  private final DynamicMap<RestView<SubgroupResource>> views;
  private final ListSubgroups list;
  private final GroupsCollection groupsCollection;

  @Inject
  SubgroupsCollection(
      DynamicMap<RestView<SubgroupResource>> views,
      ListSubgroups list,
      GroupsCollection groupsCollection) {
    this.views = views;
    this.list = list;
    this.groupsCollection = groupsCollection;
  }

  @Override
  public RestView<GroupResource> list() {
    return list;
  }

  @Override
  public SubgroupResource parse(GroupResource resource, IdString id)
      throws NotInternalGroupException, AuthException, ResourceNotFoundException {
    GroupDescription.Internal parent =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);

    GroupDescription.Basic member =
        groupsCollection.parse(TopLevelResource.INSTANCE, id).getGroup();
    if (resource.getControl().canSeeGroup() && isSubgroup(parent, member)) {
      return new SubgroupResource(resource, member);
    }
    throw new ResourceNotFoundException(id);
  }

  private static boolean isSubgroup(
      GroupDescription.Internal parent, GroupDescription.Basic member) {
    return parent.getSubgroups().contains(member.getGroupUUID());
  }

  @Override
  public DynamicMap<RestView<SubgroupResource>> views() {
    return views;
  }
}
