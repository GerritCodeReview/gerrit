// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.cache.serialize.ProtobufSerializer;
import com.google.gerrit.server.cache.serialize.entities.InternalGroupSerializer;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.bouncycastle.util.Strings;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Tracks group objects in memory for efficient access. */
@Singleton
public class GroupCacheImpl implements GroupCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String BYID_NAME = "groups";
  private static final String BYNAME_NAME = "groups_byname";
  private static final String BYUUID_NAME = "groups_byuuid";
  private static final String BYUUID_NAME_PERSISTED = "groups_byuuid_persisted";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, AccountGroup.Id.class, new TypeLiteral<Optional<InternalGroup>>() {})
            .maximumWeight(Long.MAX_VALUE)
            .loader(ByIdLoader.class);

        cache(BYNAME_NAME, String.class, new TypeLiteral<Optional<InternalGroup>>() {})
            .maximumWeight(Long.MAX_VALUE)
            .loader(ByNameLoader.class);

        // We split the group cache into two parts for performance reasons:
        // 1) An in-memory part that has only the group ref uuid as key.
        // 2) A persisted part that has the group ref uuid and sha1 of the ref as key.
        //
        // When loading dashboards or returning change query results we potentially
        // need to access many groups.
        // We want the persisted cache to be immutable and we want it to be impossible that a
        // value for a given key is out of date. We therefore require the sha-1 in the key. That
        // is in line with the rest of the caches in Gerrit.
        //
        // Splitting the cache into two chunks internally in this class allows us to retain
        // the existing performance guarantees of not requiring reads for the repo for values
        // cached in-memory but also to persist the cache which leads to a much improved
        // cold-start behavior and in-memory miss latency.

        cache(BYUUID_NAME, String.class, new TypeLiteral<Optional<InternalGroup>>() {})
            .maximumWeight(Long.MAX_VALUE)
            .loader(ByUUIDInMemoryLoader.class);

        persist(
                BYUUID_NAME_PERSISTED,
                Cache.GroupKeyProto.class,
                new TypeLiteral<InternalGroup>() {})
            .loader(PersistedByUUIDLoader.class)
            .keySerializer(new ProtobufSerializer<>(Cache.GroupKeyProto.parser()))
            .valueSerializer(PersistedInternalGroupSerializer.INSTANCE)
            .diskLimit(1 << 30) // 1 GiB
            .version(1)
            .maximumWeight(0);

        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.Id, Optional<InternalGroup>> byId;
  private final LoadingCache<String, Optional<InternalGroup>> byName;
  private final LoadingCache<String, Optional<InternalGroup>> byUUID;
  private final LoadingCache<Cache.GroupKeyProto, InternalGroup> persistedByUuidCache;

  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) LoadingCache<AccountGroup.Id, Optional<InternalGroup>> byId,
      @Named(BYNAME_NAME) LoadingCache<String, Optional<InternalGroup>> byName,
      @Named(BYUUID_NAME) LoadingCache<String, Optional<InternalGroup>> byUUID,
      @Named(BYUUID_NAME_PERSISTED)
          LoadingCache<Cache.GroupKeyProto, InternalGroup> persistedByUuidCache) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.persistedByUuidCache = persistedByUuidCache;
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.Id groupId) {
    try {
      return byId.get(groupId);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load group %s", groupId);
      return Optional.empty();
    }
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.NameKey name) {
    if (name == null) {
      return Optional.empty();
    }
    try {
      return byName.get(name.get());
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up group %s by name", name.get());
      return Optional.empty();
    }
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.UUID groupUuid) {
    if (groupUuid == null) {
      return Optional.empty();
    }

    try {
      return byUUID.get(groupUuid.get());
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up group %s by uuid", groupUuid.get());
      return Optional.empty();
    }
  }

  @Override
  public Map<AccountGroup.UUID, InternalGroup> get(Collection<AccountGroup.UUID> groupUuids) {
    try {
      Set<String> groupUuidsStringSet =
          groupUuids.stream().map(u -> u.get()).collect(toImmutableSet());
      return byUUID.getAll(groupUuidsStringSet).entrySet().stream()
          .filter(g -> g.getValue().isPresent())
          .collect(toImmutableMap(g -> AccountGroup.uuid(g.getKey()), g -> g.getValue().get()));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot look up groups %s by uuids", groupUuids);
      return ImmutableMap.of();
    }
  }

  @Override
  public InternalGroup getFromMetaId(AccountGroup.UUID groupUuid, ObjectId metaId)
      throws StorageException {
    try {
      Cache.GroupKeyProto key =
          Cache.GroupKeyProto.newBuilder()
              .setUuid(groupUuid.get())
              .setRevision(ObjectIdConverter.create().toByteString(metaId))
              .build();
      return persistedByUuidCache.get(key);
    } catch (ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void evict(AccountGroup.Id groupId) {
    if (groupId != null) {
      logger.atFine().log("Evict group %s by ID", groupId.get());
      byId.invalidate(groupId);
    }
  }

  @Override
  public void evict(AccountGroup.NameKey groupName) {
    if (groupName != null) {
      logger.atFine().log("Evict group '%s' by name", groupName.get());
      byName.invalidate(groupName.get());
    }
  }

  @Override
  public void evict(AccountGroup.UUID groupUuid) {
    if (groupUuid != null) {
      logger.atFine().log("Evict group %s by UUID", groupUuid.get());
      byUUID.invalidate(groupUuid.get());
    }
  }

  @Override
  public void evict(Collection<AccountGroup.UUID> groupUuids) {
    if (groupUuids != null && !groupUuids.isEmpty()) {
      logger.atFine().log("Evict groups %s by UUID", groupUuids);
      byUUID.invalidateAll(groupUuids);
    }
  }

  static class ByIdLoader extends CacheLoader<AccountGroup.Id, Optional<InternalGroup>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ByIdLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public Optional<InternalGroup> load(AccountGroup.Id key) throws Exception {
      try (TraceTimer ignored =
          TraceContext.newTimer(
              "Loading group by ID", Metadata.builder().groupId(key.get()).build())) {
        return groupQueryProvider.get().byId(key);
      }
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<InternalGroup>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ByNameLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public Optional<InternalGroup> load(String name) throws Exception {
      try (TraceTimer ignored =
          TraceContext.newTimer(
              "Loading group by name", Metadata.builder().groupName(name).build())) {
        return groupQueryProvider.get().byName(AccountGroup.nameKey(name));
      }
    }
  }

  static class ByUUIDInMemoryLoader extends CacheLoader<String, Optional<InternalGroup>> {
    private final LoadingCache<Cache.GroupKeyProto, InternalGroup> persistedCache;
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;

    @Inject
    ByUUIDInMemoryLoader(
        @Named(BYUUID_NAME_PERSISTED)
            LoadingCache<Cache.GroupKeyProto, InternalGroup> persistedCache,
        GitRepositoryManager repoManager,
        AllUsersName allUsersName) {
      this.persistedCache = persistedCache;
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
    }

    @Override
    public Optional<InternalGroup> load(String uuid) throws Exception {
      return loadAll(ImmutableSet.of(uuid)).get(uuid);
    }

    @Override
    public Map<String, Optional<InternalGroup>> loadAll(Iterable<? extends String> uuids)
        throws Exception {
      Map<String, Optional<InternalGroup>> toReturn = new HashMap<>();
      if (Iterables.isEmpty(uuids)) {
        return toReturn;
      }
      Iterator<? extends String> uuidIterator = uuids.iterator();
      List<Cache.GroupKeyProto> keyList = new ArrayList<>();
      try (TraceTimer ignored =
              TraceContext.newTimer(
                  "Loading group from serialized cache",
                  Metadata.builder().cacheName(BYUUID_NAME_PERSISTED).build());
          Repository allUsers = repoManager.openRepository(allUsersName)) {
        while (uuidIterator.hasNext()) {
          String currentUuid = uuidIterator.next();
          String ref = RefNames.refsGroups(AccountGroup.uuid(currentUuid));
          Ref sha1 = allUsers.exactRef(ref);
          if (sha1 == null) {
            toReturn.put(currentUuid, Optional.empty());
            continue;
          }
          Cache.GroupKeyProto key =
              Cache.GroupKeyProto.newBuilder()
                  .setUuid(currentUuid)
                  .setRevision(ObjectIdConverter.create().toByteString(sha1.getObjectId()))
                  .build();
          keyList.add(key);
        }
      }
      persistedCache.getAll(keyList).entrySet().stream()
          .forEach(g -> toReturn.put(g.getKey().getUuid(), Optional.of(g.getValue())));
      return toReturn;
    }
  }

  static class PersistedByUUIDLoader extends CacheLoader<Cache.GroupKeyProto, InternalGroup> {
    private final Groups groups;

    @Inject
    PersistedByUUIDLoader(Groups groups) {
      this.groups = groups;
    }

    @Override
    public InternalGroup load(Cache.GroupKeyProto key) throws Exception {
      try (TraceTimer ignored =
          TraceContext.newTimer(
              "Loading group by UUID", Metadata.builder().groupUuid(key.getUuid()).build())) {
        ObjectId sha1 = ObjectIdConverter.create().fromByteString(key.getRevision());
        Optional<InternalGroup> loadedGroup =
            groups.getGroup(AccountGroup.uuid(key.getUuid()), sha1);
        if (!loadedGroup.isPresent()) {
          throw new IllegalStateException(
              String.format(
                  "group %s should have the sha-1 %s, but " + "it was not found",
                  key.getUuid(), sha1.getName()));
        }
        return loadedGroup.get();
      }
    }
  }

  private enum PersistedInternalGroupSerializer implements CacheSerializer<InternalGroup> {
    INSTANCE;

    @Override
    public byte[] serialize(InternalGroup value) {
      if (value == null) {
        return new byte[0];
      }
      return Protos.toByteArray(InternalGroupSerializer.serialize(value));
    }

    @Nullable
    @Override
    public InternalGroup deserialize(byte[] in) {
      if (Strings.fromByteArray(in).isEmpty()) {
        return null;
      }
      return InternalGroupSerializer.deserialize(
          Protos.parseUnchecked(Cache.InternalGroupProto.parser(), in));
    }
  }
}
