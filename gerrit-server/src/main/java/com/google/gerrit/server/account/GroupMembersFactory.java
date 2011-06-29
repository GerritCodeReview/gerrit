// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class GroupMembersFactory implements Callable<Set<Account>> {
  public interface Factory {
    GroupMembersFactory create(AccountGroup.UUID groupUUID);
  }

  private GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountCache accountCache;

  private final AccountGroup.UUID groupUUID;


  @Inject
  GroupMembersFactory(final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountCache accountCache,
      @Assisted final AccountGroup.UUID groupUUID) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountCache = accountCache;

    this.groupUUID = groupUUID;
  }

  @Override
  public Set<Account> call() throws NoSuchGroupException, OrmException {
    return getAllGroupMembers(groupCache.get(groupUUID),
        new HashSet<AccountGroup.Id>());
  }

  private Set<Account> getAllGroupMembers(final AccountGroup group,
      final Set<AccountGroup.Id> seen) throws NoSuchGroupException,
      OrmException {
    seen.add(group.getId());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();

    final Set<Account> members = new HashSet<Account>();
    if (groupDetail.members != null) {
      for (final AccountGroupMember member : groupDetail.members) {
        members.add(accountCache.get(member.getAccountId()).getAccount());
      }
    }
    if (groupDetail.includes != null) {
      for (AccountGroupInclude groupInclude : groupDetail.includes) {
        if (!seen.contains(groupInclude.getIncludeId())) {
          members.addAll(getAllGroupMembers(
              groupCache.get(groupInclude.getIncludeId()), seen));
        }
      }
    }
    return members;
  }

}
