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
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.group.PutMembers.PutMember;
import com.google.gerrit.server.util.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class MembersCollection implements
    ChildCollection<GroupResource, MemberResource>,
    AcceptsCreate<GroupResource>{

  private final DynamicMap<RestView<MemberResource>> views;
  private final Provider<ListMembers> list;
  private final IdentifiedUser.GenericFactory userGenericFactory;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountResolver accountResolver;
  private final Provider<PutMembers> put;

  @Inject
  MembersCollection(final DynamicMap<RestView<MemberResource>> views,
      final Provider<ListMembers> list,
      final IdentifiedUser.GenericFactory userGenericFactory,
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountResolver accountResolver,
      final Provider<PutMembers> put) {
    this.views = views;
    this.list = list;
    this.userGenericFactory = userGenericFactory;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountResolver = accountResolver;
    this.put = put;
  }

  @Override
  public RestView<GroupResource> list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public MemberResource parse(final GroupResource parent, final String id)
      throws ResourceNotFoundException, Exception {
    final Account a = accountResolver.find(Url.decode(id));
    if (a == null) {
      throw new ResourceNotFoundException(id);
    }

    final AccountGroup group =
        groupCache.get(parent.getControl().getGroup().getGroupUUID());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();
    if (groupDetail.members != null) {
      for (final AccountGroupMember member : groupDetail.members) {
        if (member.getAccountId().equals(a.getId())) {
          return new MemberResource(parent,
              userGenericFactory.create(a.getId()));
        }
      }
    }
    throw new ResourceNotFoundException(id);
  }

  @SuppressWarnings("unchecked")
  @Override
  public PutMember create(final GroupResource group, final String id) {
    return new PutMember(put, Url.decode(id));
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
