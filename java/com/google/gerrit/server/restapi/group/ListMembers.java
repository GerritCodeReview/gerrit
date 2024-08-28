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

package com.google.gerrit.server.restapi.group;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountInfoComparator;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.permissions.PermissionBackendException;
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
  private final AccountLoader.Factory accountLoaderFactory;

  @Option(name = "--recursive", usage = "to resolve included groups recursively")
  private boolean recursive;

  @Inject
  protected ListMembers(
      GroupCache groupCache,
      GroupControl.Factory groupControlFactory,
      AccountLoader.Factory accountLoaderFactory) {
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @CanIgnoreReturnValue
  public ListMembers setRecursive(boolean recursive) {
    this.recursive = recursive;
    return this;
  }

  @Override
  public Response<List<AccountInfo>> apply(GroupResource resource)
      throws NotInternalGroupException, PermissionBackendException {
    GroupDescription.Internal group =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    if (recursive) {
      return Response.ok(getTransitiveMembers(group, resource.getControl()));
    }
    return Response.ok(getDirectMembers(group, resource.getControl()));
  }

  protected boolean canSeeGroup(InternalGroup group) {
    InternalGroupDescription internalGroup = new InternalGroupDescription(group);
    GroupControl groupControl = groupControlFactory.controlFor(internalGroup);
    return groupControl.canSeeGroup();
  }

  public List<AccountInfo> getTransitiveMembers(AccountGroup.UUID groupUuid)
      throws PermissionBackendException {
    Optional<InternalGroup> group = groupCache.get(groupUuid);
    if (group.isPresent()) {
      return getTransitiveMembers(group.get());
    }
    return ImmutableList.of();
  }

  public List<AccountInfo> getTransitiveMembers(InternalGroup group)
      throws PermissionBackendException {
    InternalGroupDescription internalGroup = new InternalGroupDescription(group);
    GroupControl groupControl = groupControlFactory.controlFor(internalGroup);
    return getTransitiveMembers(internalGroup, groupControl);
  }

  private List<AccountInfo> getTransitiveMembers(
      GroupDescription.Internal group, GroupControl groupControl)
      throws PermissionBackendException {
    checkSameGroup(group, groupControl);
    Set<Account.Id> members =
        getTransitiveMemberIds(
            group, groupControl, new HashSet<>(ImmutableSet.of(group.getGroupUUID())));
    return toAccountInfos(members);
  }

  public List<AccountInfo> getDirectMembers(InternalGroup group) throws PermissionBackendException {
    InternalGroupDescription internalGroup = new InternalGroupDescription(group);
    return getDirectMembers(internalGroup, groupControlFactory.controlFor(internalGroup));
  }

  public List<AccountInfo> getDirectMembers(
      GroupDescription.Internal group, GroupControl groupControl)
      throws PermissionBackendException {
    checkSameGroup(group, groupControl);
    ImmutableSet<Account.Id> directMembers = getDirectMemberIds(group, groupControl);
    return toAccountInfos(directMembers);
  }

  protected List<AccountInfo> getMembers(InternalGroup group) throws PermissionBackendException {
    if (recursive) {
      return getTransitiveMembers(group);
    }
    return getDirectMembers(group);
  }

  private List<AccountInfo> toAccountInfos(Set<Account.Id> members)
      throws PermissionBackendException {
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    List<AccountInfo> memberInfos = new ArrayList<>(members.size());
    for (Account.Id member : members) {
      memberInfos.add(accountLoader.get(member));
    }
    accountLoader.fill();
    memberInfos.sort(AccountInfoComparator.ORDER_NULLS_FIRST);
    return memberInfos;
  }

  private Set<Account.Id> getTransitiveMemberIds(
      GroupDescription.Internal group,
      GroupControl groupControl,
      HashSet<AccountGroup.UUID> seenGroups) {
    ImmutableSet<Account.Id> directMembers = getDirectMemberIds(group, groupControl);

    if (!groupControl.canSeeGroup()) {
      return directMembers;
    }

    ImmutableSet<Account.Id> indirectMembers = getIndirectMemberIds(group, seenGroups);
    return Sets.union(directMembers, indirectMembers);
  }

  private static ImmutableSet<Account.Id> getDirectMemberIds(
      GroupDescription.Internal group, GroupControl groupControl) {
    return group.getMembers().stream().filter(groupControl::canSeeMember).collect(toImmutableSet());
  }

  private ImmutableSet<Account.Id> getIndirectMemberIds(
      GroupDescription.Internal group, HashSet<AccountGroup.UUID> seenGroups) {
    ImmutableSet.Builder<Account.Id> indirectMembers = ImmutableSet.builder();
    ImmutableSet.Builder<AccountGroup.UUID> subgroupMembersToLoad = ImmutableSet.builder();
    for (AccountGroup.UUID subgroupUuid : group.getSubgroups()) {
      if (!seenGroups.contains(subgroupUuid)) {
        seenGroups.add(subgroupUuid);
        subgroupMembersToLoad.add(subgroupUuid);
      }
    }
    groupCache.get(subgroupMembersToLoad.build()).values().stream()
        .map(InternalGroupDescription::new)
        .forEach(
            subgroup -> {
              GroupControl subgroupControl = groupControlFactory.controlFor(subgroup);
              indirectMembers.addAll(getTransitiveMemberIds(subgroup, subgroupControl, seenGroups));
            });

    return indirectMembers.build();
  }

  private static void checkSameGroup(GroupDescription.Internal group, GroupControl groupControl) {
    checkState(
        group.equals(groupControl.getGroup()), "Specified group and groupControl do not match");
  }
}
