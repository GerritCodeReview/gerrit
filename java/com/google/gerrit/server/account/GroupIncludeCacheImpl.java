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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsProto;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsProto.ExternalGroupProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.logging.Metadata;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PARENT_GROUPS_NAME = "groups_bysubgroup";
  private static final String GROUPS_WITH_MEMBER_NAME = "groups_bymember";
  private static final String EXTERNAL_NAME = "groups_external";
  private static final String PERSISTED_EXTERNAL_NAME = "groups_external_persisted";

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
                new TypeLiteral<ImmutableSet<AccountGroup.UUID>>() {})
            .loader(ParentGroupsLoader.class);

        /**
         * Splitting the groups external cache into 2 caches: The first one is in memory, used to
         * serve the callers and has a single constant key "EXTERNAL_NAME". The second one is
         * persisted, its key represents the groups' state in NoteDb. The in-memory cache is used on
         * top of the persisted cache to enhance performance because the cache's value is used on
         * every request to Gerrit, potentially many times per request and the key computation can
         * become expensive.
         */
        cache(EXTERNAL_NAME, String.class, new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(AllExternalInMemoryLoader.class);

        persist(
                PERSISTED_EXTERNAL_NAME,
                String.class,
                new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .diskLimit(-1)
            .version(1)
            .maximumWeight(0)
            .keySerializer(StringCacheSerializer.INSTANCE)
            .valueSerializer(ExternalGroupsSerializer.INSTANCE);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final LoadingCache<Account.Id, ImmutableSet<AccountGroup.UUID>> groupsWithMember;
  private final LoadingCache<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> parentGroups;
  private final LoadingCache<String, ImmutableList<AccountGroup.UUID>> external;

  @Inject
  GroupIncludeCacheImpl(
      @Named(GROUPS_WITH_MEMBER_NAME)
          LoadingCache<Account.Id, ImmutableSet<AccountGroup.UUID>> groupsWithMember,
      @Named(PARENT_GROUPS_NAME)
          LoadingCache<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> parentGroups,
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
  public Collection<AccountGroup.UUID> parentGroupsOf(Set<AccountGroup.UUID> groupIds) {
    try {
      Set<AccountGroup.UUID> parents = new HashSet<>();
      parentGroups.getAll(groupIds).values().forEach(p -> parents.addAll(p));
      return parents;
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

      if (!groupId.isInternalGroup()) {
        logger.atFine().log("Evict external group %s", groupId.get());
        /**
         * No need to invalidate the persistent cache, because this eviction will change the state
         * of NoteDb causing the persistent cache's loader to use a new key that doesn't exist in
         * its cache.n
         */
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
    public ImmutableSet<AccountGroup.UUID> load(Account.Id memberId) {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading groups with member", Metadata.builder().accountId(memberId.get()).build())) {
        return groupQueryProvider.get().byMember(memberId).stream()
            .map(InternalGroup::getGroupUUID)
            .collect(toImmutableSet());
      }
    }
  }

  static class ParentGroupsLoader
      extends CacheLoader<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> {
    // Be conservative with batching: We don't want to exhaust the number of
    // results per page and maximum terms per query. Both are usually 1000+.
    private static final int MAX_BATCH_SIZE = 100;
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ParentGroupsLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public ImmutableSet<AccountGroup.UUID> load(AccountGroup.UUID key) {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading parent groups", Metadata.builder().groupUuid(key.get()).build())) {
        return loadAll(ImmutableList.of(key)).get(key);
      }
    }

    @Override
    public Map<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> loadAll(
        Iterable<? extends AccountGroup.UUID> keys) {
      int numKeys = Iterables.size(keys);
      Map<AccountGroup.UUID, ImmutableSet<AccountGroup.UUID>> result =
          Maps.newHashMapWithExpectedSize(numKeys);
      try (TraceTimer timer = TraceContext.newTimer("Loading " + numKeys + " parent groups")) {
        Iterables.partition(keys, MAX_BATCH_SIZE)
            .forEach(
                keyPartition ->
                    result.putAll(groupQueryProvider.get().bySubgroups(ImmutableSet.copyOf(keys))));
        return result;
      }
    }
  }

  static class AllExternalInMemoryLoader
      extends CacheLoader<String, ImmutableList<AccountGroup.UUID>> {
    private final Cache<String, ImmutableList<AccountGroup.UUID>> persisted;
    private final GroupsSnapshotReader snapshotReader;
    private final Groups groups;

    @Inject
    AllExternalInMemoryLoader(
        @Named(PERSISTED_EXTERNAL_NAME) Cache<String, ImmutableList<AccountGroup.UUID>> persisted,
        GroupsSnapshotReader snapshotReader,
        Groups groups) {
      this.persisted = persisted;
      this.snapshotReader = snapshotReader;
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(String key) throws Exception {
      GroupsSnapshotReader.Snapshot snapshot = snapshotReader.getSnapshot();
      return persisted.get(
          snapshot.hash(),
          () -> {
            try (TraceTimer timer = TraceContext.newTimer("Loading all external groups")) {
              return groups.getExternalGroups(snapshot.groupsRefs()).collect(toImmutableList());
            }
          });
    }
  }

  public enum ExternalGroupsSerializer
      implements CacheSerializer<ImmutableList<AccountGroup.UUID>> {
    INSTANCE;

    @Override
    public byte[] serialize(ImmutableList<AccountGroup.UUID> object) {
      AllExternalGroupsProto.Builder allBuilder = AllExternalGroupsProto.newBuilder();
      object.stream()
          .map(group -> ExternalGroupProto.newBuilder().setGroupUuid(group.get()).build())
          .forEach(allBuilder::addExternalGroup);
      return Protos.toByteArray(allBuilder.build());
    }

    @Override
    public ImmutableList<AccountGroup.UUID> deserialize(byte[] in) {
      return Protos.parseUnchecked(AllExternalGroupsProto.parser(), in).getExternalGroupList()
          .stream()
          .map(groupProto -> AccountGroup.UUID.parse(groupProto.getGroupUuid()))
          .collect(toImmutableList());
    }
  }
}
