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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.group.Groups;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks group objects in memory for efficient access. */
@Singleton
public class GroupCacheImpl implements GroupCache {
  private static final Logger log = LoggerFactory.getLogger(GroupCacheImpl.class);

  private static final String BYID_NAME = "groups";
  private static final String BYNAME_NAME = "groups_byname";
  private static final String BYUUID_NAME = "groups_byuuid";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYID_NAME, AccountGroup.Id.class, new TypeLiteral<Optional<AccountGroup>>() {})
            .loader(ByIdLoader.class);

        cache(BYNAME_NAME, String.class, new TypeLiteral<Optional<AccountGroup>>() {})
            .loader(ByNameLoader.class);

        cache(BYUUID_NAME, String.class, new TypeLiteral<Optional<AccountGroup>>() {})
            .loader(ByUUIDLoader.class);

        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.Id, Optional<AccountGroup>> byId;
  private final LoadingCache<String, Optional<AccountGroup>> byName;
  private final LoadingCache<String, Optional<AccountGroup>> byUUID;
  private final SchemaFactory<ReviewDb> schema;
  private final Provider<GroupIndexer> indexer;
  private final Groups groups;

  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) LoadingCache<AccountGroup.Id, Optional<AccountGroup>> byId,
      @Named(BYNAME_NAME) LoadingCache<String, Optional<AccountGroup>> byName,
      @Named(BYUUID_NAME) LoadingCache<String, Optional<AccountGroup>> byUUID,
      SchemaFactory<ReviewDb> schema,
      Provider<GroupIndexer> indexer,
      Groups groups) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.schema = schema;
    this.indexer = indexer;
    this.groups = groups;
  }

  @Override
  public AccountGroup get(AccountGroup.Id groupId) {
    try {
      Optional<AccountGroup> g = byId.get(groupId);
      return g.isPresent() ? g.get() : missing(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load group " + groupId, e);
      return missing(groupId);
    }
  }

  @Override
  public void evict(AccountGroup group) throws IOException {
    if (group.getId() != null) {
      byId.invalidate(group.getId());
    }
    if (group.getNameKey() != null) {
      byName.invalidate(group.getNameKey().get());
    }
    if (group.getGroupUUID() != null) {
      byUUID.invalidate(group.getGroupUUID().get());
    }
    indexer.get().index(group.getGroupUUID());
  }

  @Override
  public void evictAfterRename(final AccountGroup.NameKey oldName, AccountGroup.NameKey newName)
      throws IOException {
    if (oldName != null) {
      byName.invalidate(oldName.get());
    }
    if (newName != null) {
      byName.invalidate(newName.get());
    }
    indexer.get().index(get(newName).getGroupUUID());
  }

  @Override
  public AccountGroup get(AccountGroup.NameKey name) {
    if (name == null) {
      return null;
    }
    try {
      return byName.get(name.get()).orElse(null);
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup group %s by name", name.get()), e);
      return null;
    }
  }

  @Override
  public AccountGroup get(AccountGroup.UUID uuid) {
    if (uuid == null) {
      return null;
    }
    try {
      return byUUID.get(uuid.get()).orElse(null);
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup group %s by uuid", uuid.get()), e);
      return null;
    }
  }

  @Override
  public Optional<InternalGroup> getInternalGroup(AccountGroup.UUID groupUuid) {
    if (groupUuid == null) {
      return Optional.empty();
    }

    Optional<AccountGroup> accountGroup = Optional.empty();
    try {
      accountGroup = byUUID.get(groupUuid.get());
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot lookup group %s by uuid", groupUuid.get()), e);
    }

    if (!accountGroup.isPresent()) {
      return Optional.empty();
    }

    try (ReviewDb db = schema.open()) {
      ImmutableSet<Account.Id> members = groups.getMembers(db, groupUuid).collect(toImmutableSet());
      ImmutableSet<AccountGroup.UUID> includes =
          groups.getIncludes(db, groupUuid).collect(toImmutableSet());
      return accountGroup.map(group -> InternalGroup.create(group, members, includes));
    } catch (OrmException | NoSuchGroupException e) {
      log.warn(
          String.format("Cannot lookup members or sub-groups of group %s", groupUuid.get()), e);
    }
    return Optional.empty();
  }

  @Override
  public ImmutableList<AccountGroup> all() {
    try (ReviewDb db = schema.open()) {
      return groups.getAll(db).collect(toImmutableList());
    } catch (OrmException e) {
      log.warn("Cannot list internal groups", e);
      return ImmutableList.of();
    }
  }

  @Override
  public void onCreateGroup(AccountGroup.NameKey newGroupName) throws IOException {
    byName.invalidate(newGroupName.get());
    indexer.get().index(get(newGroupName).getGroupUUID());
  }

  private static AccountGroup missing(AccountGroup.Id key) {
    AccountGroup.NameKey name = new AccountGroup.NameKey("Deleted Group" + key);
    return new AccountGroup(name, key, null, TimeUtil.nowTs());
  }

  static class ByIdLoader extends CacheLoader<AccountGroup.Id, Optional<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    ByIdLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public Optional<AccountGroup> load(AccountGroup.Id key) throws Exception {
      try (ReviewDb db = schema.open()) {
        return groups.getGroup(db, key);
      }
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    ByNameLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public Optional<AccountGroup> load(String name) throws Exception {
      try (ReviewDb db = schema.open()) {
        return groups.getGroup(db, new AccountGroup.NameKey(name));
      }
    }
  }

  static class ByUUIDLoader extends CacheLoader<String, Optional<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    ByUUIDLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public Optional<AccountGroup> load(String uuid) throws Exception {
      try (ReviewDb db = schema.open()) {
        return groups.getGroup(db, new AccountGroup.UUID(uuid));
      }
    }
  }
}
