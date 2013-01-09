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
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.group.MembersCollection.MemberInfo;
import com.google.inject.Inject;

import java.util.List;

public class ListMembers implements RestReadView<GroupResource> {
  private final GroupControl.Factory groupControlFactory;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountCache accountCache;

  @Inject
  ListMembers(final GroupControl.Factory groupControlFactory,
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountCache accountCache) {
    this.groupControlFactory = groupControlFactory;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountCache = accountCache;
  }

  @Override
  public List<MemberInfo> apply(final GroupResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    final List<MemberInfo> members = Lists.newArrayList();

    final GroupControl groupControl =
        groupControlFactory.validateFor(resource.getGroupUUID());
    final AccountGroup group =
        groupCache.get(groupControl.getGroup().getGroupUUID());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();

    if (groupDetail.members != null) {
      for (final AccountGroupMember member : groupDetail.members) {
        final Account account = accountCache.get(member.getAccountId()).getAccount();
        members.add(MembersCollection.parse(account));
      }
    }

    return members;
  }
}
