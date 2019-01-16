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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PARENT_GROUPS_NAME = "groups_bysubgroup";
  private static final String GROUPS_WITH_MEMBER_NAME = "groups_bymember";
  private static final String EXTERNAL_NAME = "groups_external";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(
                GROUPS_WITH_MEMBER_NAME,
                Account.Id.class,
                new TypeLiteral<ImmutableSet<AccountGroup.UUID>>() {})
            .loader(GroupsWithMemberLoader.class);

        cache(
                PARENT_GROUPS_NAME,
                AccountGroup.UUID.class,
                new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(ParentGroupsLoader.class);

        cache(EXTERNAL_NAME, String.class, new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(AllExternalLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, ImmutableSet<AccountGroup.UUID>> groupsWithMember;
  private final LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> parentGroups;
  private final LoadingCache<String, ImmutableList<AccountGroup.UUID>> external;

  @Inject
  GroupIncludeCacheImpl(
      @Named(GROUPS_WITH_MEMBER_NAME)
          LoadingCache<Account.Id, ImmutableSet<AccountGroup.UUID>> groupsWithMember,
      @Named(PARENT_GROUPS_NAME)
          LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> parentGroups,
      @Named(EXTERNAL_NAME) LoadingCache<String, ImmutableList<AccountGroup.UUID>> external) {
    this.groupsWithMember = groupsWithMember;
    this.parentGroups = parentGroups;
    this.external = external;
  }

  @Override
  public Collection<AccountGroup.UUID> getGroupsWithMember(Account.Id memberId) {
    try {
      return groupsWithMember.get(memberId);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load groups containing %s as member", memberId);
      return ImmutableSet.of();
    }
  }

  @Override
  public Collection<AccountGroup.UUID> parentGroupsOf(AccountGroup.UUID groupId) {
    try {
      return parentGroups.get(groupId);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load included groups");
      return Collections.emptySet();
    }
  }

  @Override
  public void evictGroupsWithMember(Account.Id memberId) {
    if (memberId != null) {
      logger.atFine().log("Evict groups with member %d", memberId.get());
      groupsWithMember.invalidate(memberId);
    }
  }

  @Override
  public void evictParentGroupsOf(AccountGroup.UUID groupId) {
    if (groupId != null) {
      logger.atFine().log("Evict parent groups of %s", groupId.get());
      parentGroups.invalidate(groupId);

      if (!AccountGroup.isInternalGroup(groupId)) {
        logger.atFine().log("Evict external group %s", groupId.get());
        external.invalidate(EXTERNAL_NAME);
      }
    }
  }

  @Override
  public Collection<AccountGroup.UUID> allExternalMembers() {
    try {
      return external.get(EXTERNAL_NAME);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load set of non-internal groups");
      return ImmutableList.of();
    }
  }

  static class GroupsWithMemberLoader
      extends CacheLoader<Account.Id, ImmutableSet<AccountGroup.UUID>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    GroupsWithMemberLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public ImmutableSet<AccountGroup.UUID> load(Account.Id memberId) throws StorageException {
      try (TraceTimer timer = TraceContext.newTimer("Loading groups with member %s", memberId)) {
        return groupQueryProvider.get().byMember(memberId).stream()
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableSet());
      }
    }
  }

  static class ParentGroupsLoader
      extends CacheLoader<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ParentGroupsLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(AccountGroup.UUID key) throws StorageException {
      try (TraceTimer timer = TraceContext.newTimer("Loading parent groups of %s", key)) {
        return groupQueryProvider.get().bySubgroup(key).stream()
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableList());
      }
    }
  }

  static class AllExternalLoader extends CacheLoader<String, ImmutableList<AccountGroup.UUID>> {
    private final Groups groups;

    @Inject
    AllExternalLoader(Groups groups) {
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(String key) throws Exception {
      try (TraceTimer timer = TraceContext.newTimer("Loading all external groups")) {
        return groups.getExternalGroups().collect(toImmutableList());
      }
    }
  }
}
