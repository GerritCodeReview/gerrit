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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.group.Groups;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.query.group.InternalGroupQuery;
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
        cache(BYID_NAME, AccountGroup.Id.class, new TypeLiteral<Optional<InternalGroup>>() {})
            .loader(ByIdLoader.class);

        cache(BYNAME_NAME, String.class, new TypeLiteral<Optional<InternalGroup>>() {})
            .loader(ByNameLoader.class);

        cache(BYUUID_NAME, String.class, new TypeLiteral<Optional<InternalGroup>>() {})
            .loader(ByUUIDLoader.class);

        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.Id, Optional<InternalGroup>> byId;
  private final LoadingCache<String, Optional<InternalGroup>> byName;
  private final LoadingCache<String, Optional<InternalGroup>> byUUID;
  private final Provider<GroupIndexer> indexer;

  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) LoadingCache<AccountGroup.Id, Optional<InternalGroup>> byId,
      @Named(BYNAME_NAME) LoadingCache<String, Optional<InternalGroup>> byName,
      @Named(BYUUID_NAME) LoadingCache<String, Optional<InternalGroup>> byUUID,
      Provider<GroupIndexer> indexer) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.indexer = indexer;
  }

  @Override
  public Optional<InternalGroup> get(AccountGroup.Id groupId) {
    try {
      return byId.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load group " + groupId, e);
      return Optional.empty();
    }
  }

  @Override
  public void evict(
      AccountGroup.UUID groupUuid, AccountGroup.Id groupId, AccountGroup.NameKey groupName)
      throws IOException {
    if (groupId != null) {
      byId.invalidate(groupId);
    }
    if (groupName != null) {
      byName.invalidate(groupName.get());
    }
    if (groupUuid != null) {
      byUUID.invalidate(groupUuid.get());
    }
    indexer.get().index(groupUuid);
  }

  @Override
  public void evictAfterRename(AccountGroup.NameKey oldName) throws IOException {
    if (oldName != null) {
      byName.invalidate(oldName.get());
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
      log.warn(String.format("Cannot look up group %s by name", name.get()), e);
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
      log.warn(String.format("Cannot look up group %s by uuid", groupUuid.get()), e);
      return Optional.empty();
    }
  }

  @Override
  public void onCreateGroup(AccountGroup group) throws IOException {
    indexer.get().index(group.getGroupUUID());
  }

  static class ByIdLoader extends CacheLoader<AccountGroup.Id, Optional<InternalGroup>> {
    private final Provider<InternalGroupQuery> groupQueryProvider;

    @Inject
    ByIdLoader(Provider<InternalGroupQuery> groupQueryProvider) {
      this.groupQueryProvider = groupQueryProvider;
    }

    @Override
    public Optional<InternalGroup> load(AccountGroup.Id key) throws Exception {
      return groupQueryProvider.get().byId(key);
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
      return groupQueryProvider.get().byName(new AccountGroup.NameKey(name));
    }
  }

  static class ByUUIDLoader extends CacheLoader<String, Optional<InternalGroup>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    ByUUIDLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public Optional<InternalGroup> load(String uuid) throws Exception {
      try (ReviewDb db = schema.open()) {
        return groups.getGroup(db, new AccountGroup.UUID(uuid));
      }
    }
  }
}
