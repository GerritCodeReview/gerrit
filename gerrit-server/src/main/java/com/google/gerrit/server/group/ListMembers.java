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

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetail;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ListMembers implements RestReadView<GroupResource> {
  private final GroupCache groupCache;
  private final GroupDetail.Factory groupDetailFactory;
  private final AccountInfo.Loader accountLoader;

  @Option(name = "--recursive", usage = "to resolve included groups recursively")
  private boolean recursive;

  @Inject
  protected ListMembers(GroupCache groupCache,
      GroupDetail.Factory groupDetailFactory,
      AccountInfo.Loader.Factory accountLoaderFactory) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountLoader = accountLoaderFactory.create(true);
  }

  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
  }

  @Override
  public List<AccountInfo> apply(final GroupResource resource)
      throws MethodNotAllowedException, OrmException {
    if (resource.toAccountGroup() == null) {
      throw new MethodNotAllowedException();
    }

    return apply(resource.getGroupUUID());
  }

  public List<AccountInfo> apply(AccountGroup group)
      throws OrmException {
    return apply(group.getGroupUUID());
  }

  public List<AccountInfo> apply(AccountGroup.UUID groupId)
      throws OrmException {
    final Map<Account.Id, AccountInfo> members =
        getMembers(groupId, new HashSet<AccountGroup.UUID>());
    final List<AccountInfo> memberInfos = Lists.newArrayList(members.values());
    Collections.sort(memberInfos, new Comparator<AccountInfo>() {
      @Override
      public int compare(AccountInfo a, AccountInfo b) {
        return ComparisonChain.start()
            .compare(a.name, b.name, Ordering.natural().nullsFirst())
            .compare(a.email, b.email, Ordering.natural().nullsFirst())
            .compare(a._accountId, b._accountId, Ordering.natural().nullsFirst()).result();
      }
    });
    return memberInfos;
  }

  private Map<Account.Id, AccountInfo> getMembers(
      final AccountGroup.UUID groupUUID,
      final HashSet<AccountGroup.UUID> seenGroups) throws OrmException {
    seenGroups.add(groupUUID);

    final Map<Account.Id, AccountInfo> members = Maps.newHashMap();
    final AccountGroup group = groupCache.get(groupUUID);
    if (group == null) {
      // the included group is an external group and can't be resolved
      return Collections.emptyMap();
    }

    final GroupDetail groupDetail = groupDetailFactory.create(groupUUID);

    List<Account.Id> ms = groupDetail.getDirctMembers();
    if (ms != null && !ms.isEmpty()) {
      for (final Account.Id m : ms) {
        if (!members.containsKey(m)) {
          members.put(m, accountLoader.get(m));
        }
      }
    }

    List<AccountGroup.UUID> gs = groupDetail.getDirectIncludes();
    if (recursive) {
      if (gs != null && !gs.isEmpty()) {
        for (final AccountGroup.UUID includedGroupUUID : gs) {
          if (!seenGroups.contains(includedGroupUUID)) {
            members.putAll(getMembers(includedGroupUUID, seenGroups));
          }
        }
      }
    }
    accountLoader.fill();
    return members;
  }
}
