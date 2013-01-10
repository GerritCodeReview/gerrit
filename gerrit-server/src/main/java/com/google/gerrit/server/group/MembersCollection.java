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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.util.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class MembersCollection implements
    ChildCollection<GroupResource, MemberResource> {

  private final DynamicMap<RestView<MemberResource>> views;
  private final Provider<ListMembers> list;
  private final IdentifiedUser.GenericFactory userGenericFactory;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;

  @Inject
  MembersCollection(final DynamicMap<RestView<MemberResource>> views,
      final Provider<ListMembers> list,
      final IdentifiedUser.GenericFactory userGenericFactory,
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory) {
    this.views = views;
    this.list = list;
    this.userGenericFactory = userGenericFactory;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
  }

  @Override
  public RestView<GroupResource> list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public MemberResource parse(final GroupResource parent, final String id)
      throws ResourceNotFoundException, Exception {
    final Account.Id accountId;
    try {
      accountId = new Account.Id(Integer.parseInt(Url.decode(id)));
    } catch (NumberFormatException e) {
      throw new ResourceNotFoundException(id);
    }

    final AccountGroup group =
        groupCache.get(parent.getControl().getGroup().getGroupUUID());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();
    if (groupDetail.members != null) {
      for (final AccountGroupMember member : groupDetail.members) {
        if (member.getAccountId().equals(accountId)) {
          return new MemberResource(
              userGenericFactory.create(accountId));
        }
      }
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<MemberResource>> views() {
    return views;
  }

  public static MemberInfo parse(final Account account) {
    final MemberInfo accountInfo = new MemberInfo();
    accountInfo.setId(account.getId());
    accountInfo.fullName = account.getFullName();
    accountInfo.preferredEmail = account.getPreferredEmail();
    accountInfo.userName = account.getUserName();
    return accountInfo;
  }

  static class MemberInfo {
    final String kind = "gerritcodereview#member";

    String fullName;
    String id;
    int accountId;
    String preferredEmail;
    String userName;

    void setId(Account.Id i) {
      accountId = i.get();
      id = Url.encode(Integer.toString(accountId));
    }
  }
}
