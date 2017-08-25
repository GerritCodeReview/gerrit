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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.api.accounts.AccountInfoComparator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kohsuke.args4j.Option;

public class ListMembers implements RestReadView<GroupResource> {
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final AccountLoader accountLoader;

  @Option(name = "--recursive", usage = "to resolve included groups recursively")
  private boolean recursive;

  @Inject
  protected ListMembers(
      GroupCache groupCache,
      GroupControl.Factory groupControlFactory,
      AccountLoader.Factory accountLoaderFactory) {
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.accountLoader = accountLoaderFactory.create(true);
  }

  public ListMembers setRecursive(boolean recursive) {
    this.recursive = recursive;
    return this;
  }

  @Override
  public List<AccountInfo> apply(GroupResource resource)
      throws MethodNotAllowedException, OrmException {
    GroupDescription.Internal group =
        resource.asInternalGroup().orElseThrow(MethodNotAllowedException::new);
    return apply(group.getGroupUUID());
  }

  public List<AccountInfo> apply(AccountGroup.UUID groupId) throws OrmException {
    Set<Account.Id> members = getMembers(groupId, new HashSet<>());
    List<AccountInfo> memberInfos = new ArrayList<>(members.size());
    for (Account.Id member : members) {
      memberInfos.add(accountLoader.get(member));
    }
    accountLoader.fill();
    memberInfos.sort(AccountInfoComparator.ORDER_NULLS_FIRST);
    return memberInfos;
  }

  private Set<Account.Id> getMembers(
      AccountGroup.UUID groupUUID, HashSet<AccountGroup.UUID> seenGroups) {
    seenGroups.add(groupUUID);

    Optional<InternalGroup> internalGroup = groupCache.get(groupUUID);
    if (!internalGroup.isPresent()) {
      return ImmutableSet.of();
    }
    InternalGroup group = internalGroup.get();

    GroupControl groupControl = groupControlFactory.controlFor(new InternalGroupDescription(group));

    Set<Account.Id> directMembers =
        group.getMembers().stream().filter(groupControl::canSeeMember).collect(toImmutableSet());

    Set<Account.Id> indirectMembers = new HashSet<>();
    if (recursive && groupControl.canSeeGroup()) {
      for (AccountGroup.UUID subgroupUuid : group.getIncludes()) {
        if (!seenGroups.contains(subgroupUuid)) {
          indirectMembers.addAll(getMembers(subgroupUuid, seenGroups));
        }
      }
    }
    return Sets.union(directMembers, indirectMembers);
  }
}
