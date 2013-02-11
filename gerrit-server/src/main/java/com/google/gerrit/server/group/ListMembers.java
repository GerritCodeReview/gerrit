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
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.group.MembersCollection.MemberInfo;
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
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountCache accountCache;

  @Option(name = "--recursive", usage = "to resolve included groups recursively")
  private boolean recursive;

  @Inject
  ListMembers(final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountCache accountCache) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountCache = accountCache;
  }

  @Override
  public List<MemberInfo> apply(final GroupResource resource)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    if (resource.toAccountGroup() == null) {
      throw new ResourceNotFoundException(resource.getGroupUUID().get());
    }
    final Map<Account.Id, MemberInfo> members =
        getMembers(resource.getGroupUUID(), new HashSet<AccountGroup.UUID>());
    final List<MemberInfo> memberInfos = Lists.newArrayList(members.values());
    Collections.sort(memberInfos, new Comparator<MemberInfo>() {
      @Override
      public int compare(MemberInfo a, MemberInfo b) {
        return ComparisonChain.start()
            .compare(a.fullName, b.fullName, Ordering.natural().nullsFirst())
            .compare(a.preferredEmail, b.preferredEmail, Ordering.natural().nullsFirst())
            .compare(a.id, b.id, Ordering.natural().nullsFirst()).result();
      }
    });
    return memberInfos;
  }

  private Map<Account.Id, MemberInfo> getMembers(
      final AccountGroup.UUID groupUUID,
      final HashSet<AccountGroup.UUID> seenGroups) throws OrmException,
      NoSuchGroupException {
    seenGroups.add(groupUUID);

    final Map<Account.Id, MemberInfo> members = Maps.newHashMap();
    final AccountGroup group = groupCache.get(groupUUID);
    if (group == null) {
      // the included group is an external group and can't be resolved
      return Collections.emptyMap();
    }

    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();

    if (groupDetail.members != null) {
      for (final AccountGroupMember m : groupDetail.members) {
        if (!members.containsKey(m.getAccountId())) {
          final Account account =
              accountCache.get(m.getAccountId()).getAccount();
          members.put(account.getId(), MembersCollection.parse(account));
        }
      }
    }

    if (recursive) {
      if (groupDetail.includes != null) {
        for (final AccountGroupIncludeByUuid includedGroup : groupDetail.includes) {
          if (!seenGroups.contains(includedGroup.getIncludeUUID())) {
            members.putAll(getMembers(includedGroup.getIncludeUUID(), seenGroups));
          }
        }
      }
    }
    return members;
  }
}
