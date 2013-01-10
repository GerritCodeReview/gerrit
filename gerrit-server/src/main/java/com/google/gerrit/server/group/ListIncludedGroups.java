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

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.group.GetGroup.GroupInfo;
import com.google.inject.Inject;

import java.util.List;

public class ListIncludedGroups implements RestReadView<GroupResource> {
  private final GroupControl.Factory groupControlFactory;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;

  @Inject
  ListIncludedGroups(final GroupControl.Factory groupControlFactory,
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory) {
    this.groupControlFactory = groupControlFactory;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
  }

  @Override
  public List<GroupInfo> apply(final GroupResource resource)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    final List<GroupInfo> includedGroups = Lists.newArrayList();

    final GroupControl groupControl =
        groupControlFactory.validateFor(resource.getGroupUUID());
    final AccountGroup group =
        groupCache.get(groupControl.getGroup().getGroupUUID());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();

    if (groupDetail.includes != null) {
      for (final AccountGroupIncludeByUuid groupInclude : groupDetail.includes) {
        final AccountGroup includedGroup =
            groupCache.get(groupInclude.getIncludeUUID());
        includedGroups.add(GetGroup.parse(GroupDescriptions
            .forAccountGroup(includedGroup)));
      }
    }

    return includedGroups;
  }
}
