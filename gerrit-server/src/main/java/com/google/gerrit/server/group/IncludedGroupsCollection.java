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

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class IncludedGroupsCollection implements
    ChildCollection<GroupResource, IncludedGroupResource> {

  private final DynamicMap<RestView<IncludedGroupResource>> views;
  private final Provider<ListIncludedGroups> list;
  private final GroupControl.Factory groupControlFactory;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;

  @Inject
  IncludedGroupsCollection(final DynamicMap<RestView<IncludedGroupResource>> views,
      final Provider<ListIncludedGroups> list,
      final GroupControl.Factory groupControlFactory,
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory) {
    this.views = views;
    this.list = list;
    this.groupControlFactory = groupControlFactory;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
  }

  @Override
  public RestView<GroupResource> list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public IncludedGroupResource parse(final GroupResource parent, final String id)
      throws ResourceNotFoundException, Exception {
    final GroupResource groupResource =
        GroupsCollection.parse(id, groupControlFactory);
    final AccountGroup group =
        groupCache.get(parent.getControl().getGroup().getGroupUUID());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();
    if (groupDetail.includes != null) {
      for (final AccountGroupInclude groupInclude : groupDetail.includes) {
        final AccountGroup includedGroup =
            groupCache.get(groupInclude.getIncludeId());
        if (includedGroup.getGroupUUID().equals(groupResource.getGroupUUID())) {
          return new IncludedGroupResource(groupResource.getControl());
        }
      }
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<IncludedGroupResource>> views() {
    return views;
  }
}
