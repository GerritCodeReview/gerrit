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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class ListMembers implements RestReadView<GroupResource> {
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountLoader accountLoader;

  @Option(name = "--recursive", usage = "to resolve included groups recursively")
  private boolean recursive;

  @Inject
  protected ListMembers(
      GroupCache groupCache,
      GroupDetailFactory.Factory groupDetailFactory,
      AccountLoader.Factory accountLoaderFactory) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountLoader = accountLoaderFactory.create(true);
  }

  public ListMembers setRecursive(boolean recursive) {
    this.recursive = recursive;
    return this;
  }

  @Override
  public List<AccountInfo> apply(GroupResource resource)
      throws MethodNotAllowedException, OrmException {
    if (resource.toAccountGroup() == null) {
      throw new MethodNotAllowedException();
    }

    return apply(resource.getGroupUUID());
  }

  public List<AccountInfo> apply(AccountGroup group) throws OrmException {
    return apply(group.getGroupUUID());
  }

  public List<AccountInfo> apply(AccountGroup.UUID groupId) throws OrmException {
    final Map<Account.Id, AccountInfo> members =
        getMembers(groupId, new HashSet<AccountGroup.UUID>());
    final List<AccountInfo> memberInfos = Lists.newArrayList(members.values());
    Collections.sort(memberInfos, AccountInfoComparator.ORDER_NULLS_FIRST);
    return memberInfos;
  }

  private Map<Account.Id, AccountInfo> getMembers(
      final AccountGroup.UUID groupUUID, HashSet<AccountGroup.UUID> seenGroups)
      throws OrmException {
    seenGroups.add(groupUUID);

    final Map<Account.Id, AccountInfo> members = new HashMap<>();
    final AccountGroup group = groupCache.get(groupUUID);
    if (group == null) {
      // the included group is an external group and can't be resolved
      return Collections.emptyMap();
    }

    final GroupDetail groupDetail;
    try {
      groupDetail = groupDetailFactory.create(group.getId()).call();
    } catch (NoSuchGroupException e) {
      // the included group is not visible
      return Collections.emptyMap();
    }

    if (groupDetail.members != null) {
      for (AccountGroupMember m : groupDetail.members) {
        if (!members.containsKey(m.getAccountId())) {
          members.put(m.getAccountId(), accountLoader.get(m.getAccountId()));
        }
      }
    }

    if (recursive) {
      if (groupDetail.includes != null) {
        for (AccountGroupById includedGroup : groupDetail.includes) {
          if (!seenGroups.contains(includedGroup.getIncludeUUID())) {
            members.putAll(getMembers(includedGroup.getIncludeUUID(), seenGroups));
          }
        }
      }
    }
    accountLoader.fill();
    return members;
  }
}
