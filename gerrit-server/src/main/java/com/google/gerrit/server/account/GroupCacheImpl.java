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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.List;
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

  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) LoadingCache<AccountGroup.Id, Optional<AccountGroup>> byId,
      @Named(BYNAME_NAME) LoadingCache<String, Optional<AccountGroup>> byName,
      @Named(BYUUID_NAME) LoadingCache<String, Optional<AccountGroup>> byUUID,
      SchemaFactory<ReviewDb> schema) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.schema = schema;
  }

  @Override
  public AccountGroup get(final AccountGroup.Id groupId) {
    try {
      Optional<AccountGroup> g = byId.get(groupId);
      return g.isPresent() ? g.get() : missing(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load group " + groupId, e);
      return missing(groupId);
    }
  }

  @Override
  public void evict(final AccountGroup group) {
    if (group.getId() != null) {
      byId.invalidate(group.getId());
    }
    if (group.getNameKey() != null) {
      byName.invalidate(group.getNameKey().get());
    }
    if (group.getGroupUUID() != null) {
      byUUID.invalidate(group.getGroupUUID().get());
    }
  }

  @Override
  public void evictAfterRename(
      final AccountGroup.NameKey oldName, final AccountGroup.NameKey newName) {
    if (oldName != null) {
      byName.invalidate(oldName.get());
    }
    if (newName != null) {
      byName.invalidate(newName.get());
    }
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
      log.warn(String.format("Cannot lookup group %s by name", uuid.get()), e);
      return null;
    }
  }

  @Override
  public ImmutableList<AccountGroup> all() {
    try (ReviewDb db = schema.open()) {
      return ImmutableList.copyOf(db.accountGroups().all());
    } catch (OrmException e) {
      log.warn("Cannot list internal groups", e);
      return ImmutableList.of();
    }
  }

  @Override
  public void onCreateGroup(AccountGroup.NameKey newGroupName) {
    byName.invalidate(newGroupName.get());
  }

  private static AccountGroup missing(AccountGroup.Id key) {
    AccountGroup.NameKey name = new AccountGroup.NameKey("Deleted Group" + key);
    return new AccountGroup(name, key, null);
  }

  static class ByIdLoader extends CacheLoader<AccountGroup.Id, Optional<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByIdLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Optional<AccountGroup> load(final AccountGroup.Id key) throws Exception {
      try (ReviewDb db = schema.open()) {
        return Optional.ofNullable(db.accountGroups().get(key));
      }
    }
  }

  static class ByNameLoader extends CacheLoader<String, Optional<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByNameLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Optional<AccountGroup> load(String name) throws Exception {
      try (ReviewDb db = schema.open()) {
        AccountGroup.NameKey key = new AccountGroup.NameKey(name);
        AccountGroupName r = db.accountGroupNames().get(key);
        if (r != null) {
          return Optional.ofNullable(db.accountGroups().get(r.getId()));
        }
        return Optional.empty();
      }
    }
  }

  static class ByUUIDLoader extends CacheLoader<String, Optional<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByUUIDLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Optional<AccountGroup> load(String uuid) throws Exception {
      try (ReviewDb db = schema.open()) {
        List<AccountGroup> r;

        r = db.accountGroups().byUUID(new AccountGroup.UUID(uuid)).toList();
        if (r.size() == 1) {
          return Optional.of(r.get(0));
        } else if (r.size() == 0) {
          return Optional.empty();
        } else {
          throw new OrmDuplicateKeyException("Duplicate group UUID " + uuid);
        }
      }
    }
  }
}
