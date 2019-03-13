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
import com.google.common.collect.Streams;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.group.Groups;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final Logger log = LoggerFactory.getLogger(GroupIncludeCacheImpl.class);
  private static final String PARENT_GROUPS_NAME = "groups_bysubgroup";
  private static final String SUBGROUPS_NAME = "groups_subgroups";
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

        cache(
                SUBGROUPS_NAME,
                AccountGroup.UUID.class,
                new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(SubgroupsLoader.class);

        cache(EXTERNAL_NAME, String.class, new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(AllExternalLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, ImmutableSet<AccountGroup.UUID>> groupsWithMember;
  private final LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> subgroups;
  private final LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> parentGroups;
  private final LoadingCache<String, ImmutableList<AccountGroup.UUID>> external;

  @Inject
  GroupIncludeCacheImpl(
      @Named(GROUPS_WITH_MEMBER_NAME)
          LoadingCache<Account.Id, ImmutableSet<AccountGroup.UUID>> groupsWithMember,
      @Named(SUBGROUPS_NAME)
          LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> subgroups,
      @Named(PARENT_GROUPS_NAME)
          LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> parentGroups,
      @Named(EXTERNAL_NAME) LoadingCache<String, ImmutableList<AccountGroup.UUID>> external) {
    this.groupsWithMember = groupsWithMember;
    this.subgroups = subgroups;
    this.parentGroups = parentGroups;
    this.external = external;
  }

  @Override
  public Collection<AccountGroup.UUID> getGroupsWithMember(Account.Id memberId) {
    try {
      return groupsWithMember.get(memberId);
    } catch (ExecutionException e) {
      log.warn("Cannot load groups containing {} as member", memberId.get());
      return ImmutableSet.of();
    }
  }

  @Override
  public Collection<AccountGroup.UUID> subgroupsOf(AccountGroup.UUID groupId) {
    try {
      return subgroups.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load members of group", e);
      return Collections.emptySet();
    }
  }

  @Override
  public Collection<AccountGroup.UUID> parentGroupsOf(AccountGroup.UUID groupId) {
    try {
      return parentGroups.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load included groups", e);
      return Collections.emptySet();
    }
  }

  @Override
  public void evictGroupsWithMember(Account.Id memberId) {
    if (memberId != null) {
      groupsWithMember.invalidate(memberId);
    }
  }

  @Override
  public void evictSubgroupsOf(AccountGroup.UUID groupId) {
    if (groupId != null) {
      subgroups.invalidate(groupId);
    }
  }

  @Override
  public void evictParentGroupsOf(AccountGroup.UUID groupId) {
    if (groupId != null) {
      parentGroups.invalidate(groupId);

      if (!AccountGroup.isInternalGroup(groupId)) {
        external.invalidate(EXTERNAL_NAME);
      }
    }
  }

  @Override
  public Collection<AccountGroup.UUID> allExternalMembers() {
    try {
      return external.get(EXTERNAL_NAME);
    } catch (ExecutionException e) {
      log.warn("Cannot load set of non-internal groups", e);
      return ImmutableList.of();
    }
  }

  static class GroupsWithMemberLoader
      extends CacheLoader<Account.Id, ImmutableSet<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Provider<GroupIndex> groupIndexProvider;
    private final Provider<InternalGroupQuery> groupQueryProvider;
    private final GroupCache groupCache;

    @Inject
    GroupsWithMemberLoader(
        SchemaFactory<ReviewDb> schema,
        GroupIndexCollection groupIndexCollection,
        Provider<InternalGroupQuery> groupQueryProvider,
        GroupCache groupCache) {
      this.schema = schema;
      groupIndexProvider = groupIndexCollection::getSearchIndex;
      this.groupQueryProvider = groupQueryProvider;
      this.groupCache = groupCache;
    }

    @Override
    public ImmutableSet<AccountGroup.UUID> load(Account.Id memberId)
        throws OrmException, NoSuchGroupException {
      GroupIndex groupIndex = groupIndexProvider.get();
      if (groupIndex != null && groupIndex.getSchema().hasField(GroupField.MEMBER)) {
        return groupQueryProvider.get().byMember(memberId).stream()
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableSet());
      }
      try (ReviewDb db = schema.open()) {
        return Groups.getGroupsWithMemberFromReviewDb(db, memberId)
            .map(groupCache::get)
            .flatMap(Streams::stream)
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableSet());
      }
    }
  }

  static class SubgroupsLoader
      extends CacheLoader<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    SubgroupsLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(AccountGroup.UUID key)
        throws OrmException, NoSuchGroupException {
      try (ReviewDb db = schema.open()) {
        return groups.getSubgroups(db, key).collect(toImmutableList());
      }
    }
  }

  static class ParentGroupsLoader
      extends CacheLoader<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Provider<GroupIndex> groupIndexProvider;
    private final Provider<InternalGroupQuery> groupQueryProvider;
    private final GroupCache groupCache;

    @Inject
    ParentGroupsLoader(
        SchemaFactory<ReviewDb> sf,
        GroupIndexCollection groupIndexCollection,
        Provider<InternalGroupQuery> groupQueryProvider,
        GroupCache groupCache) {
      schema = sf;
      this.groupIndexProvider = groupIndexCollection::getSearchIndex;
      this.groupQueryProvider = groupQueryProvider;
      this.groupCache = groupCache;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(AccountGroup.UUID key) throws OrmException {
      GroupIndex groupIndex = groupIndexProvider.get();
      if (groupIndex != null && groupIndex.getSchema().hasField(GroupField.SUBGROUP)) {
        return groupQueryProvider.get().bySubgroup(key).stream()
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableList());
      }
      try (ReviewDb db = schema.open()) {
        return Groups.getParentGroupsFromReviewDb(db, key)
            .map(groupCache::get)
            .flatMap(Streams::stream)
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableList());
      }
    }
  }

  static class AllExternalLoader extends CacheLoader<String, ImmutableList<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    AllExternalLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(String key) throws Exception {
      try (ReviewDb db = schema.open()) {
        return groups.getExternalGroups(db).collect(toImmutableList());
      }
    }
  }
}
