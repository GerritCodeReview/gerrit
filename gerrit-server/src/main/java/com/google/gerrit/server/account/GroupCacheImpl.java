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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Collection;
import java.util.List;

/** Tracks group objects in memory for efficient access. */
@Singleton
public class GroupCacheImpl implements GroupCache {
  private static final String BYID_NAME = "groups";
  private static final String BYNAME_NAME = "groups_byname";
  private static final String BYUUID_NAME = "groups_byuuid";
  private static final String BYEXT_NAME = "groups_byext";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.Id, AccountGroup>> byId =
            new TypeLiteral<Cache<AccountGroup.Id, AccountGroup>>() {};
        core(byId, BYID_NAME).populateWith(ByIdLoader.class);

        final TypeLiteral<Cache<AccountGroup.NameKey, AccountGroup>> byName =
            new TypeLiteral<Cache<AccountGroup.NameKey, AccountGroup>>() {};
        core(byName, BYNAME_NAME).populateWith(ByNameLoader.class);

        final TypeLiteral<Cache<AccountGroup.UUID, AccountGroup>> byUUID =
            new TypeLiteral<Cache<AccountGroup.UUID, AccountGroup>>() {};
        core(byUUID, BYUUID_NAME).populateWith(ByUUIDLoader.class);

        final TypeLiteral<Cache<AccountGroup.ExternalNameKey, Collection<AccountGroup>>> byExternalName =
            new TypeLiteral<Cache<AccountGroup.ExternalNameKey, Collection<AccountGroup>>>() {};
        core(byExternalName, BYEXT_NAME) //
            .populateWith(ByExternalNameLoader.class);

        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final Cache<AccountGroup.Id, AccountGroup> byId;
  private final Cache<AccountGroup.NameKey, AccountGroup> byName;
  private final Cache<AccountGroup.UUID, AccountGroup> byUUID;
  private final Cache<AccountGroup.ExternalNameKey, Collection<AccountGroup>> byExternalName;

  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) Cache<AccountGroup.Id, AccountGroup> byId,
      @Named(BYNAME_NAME) Cache<AccountGroup.NameKey, AccountGroup> byName,
      @Named(BYUUID_NAME) Cache<AccountGroup.UUID, AccountGroup> byUUID,
      @Named(BYEXT_NAME) Cache<AccountGroup.ExternalNameKey, Collection<AccountGroup>> byExternalName) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.byExternalName = byExternalName;
  }

  public AccountGroup get(final AccountGroup.Id groupId) {
    return byId.get(groupId);
  }

  public void evict(final AccountGroup group) {
    byId.remove(group.getId());
    byName.remove(group.getNameKey());
    byUUID.remove(group.getGroupUUID());
    byExternalName.remove(group.getExternalNameKey());
  }

  public void evictAfterRename(final AccountGroup.NameKey oldName) {
    byName.remove(oldName);
  }

  public AccountGroup get(final AccountGroup.NameKey name) {
    return byName.get(name);
  }

  public AccountGroup get(final AccountGroup.UUID uuid) {
    return byUUID.get(uuid);
  }

  public Collection<AccountGroup> get(
      final AccountGroup.ExternalNameKey externalName) {
    return byExternalName.get(externalName);
  }

  static class ByIdLoader extends EntryCreator<AccountGroup.Id, AccountGroup> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByIdLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public AccountGroup createEntry(final AccountGroup.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountGroup group = db.accountGroups().get(key);
        if (group != null) {
          return group;
        } else {
          return missing(key);
        }
      } finally {
        db.close();
      }
    }

    @Override
    public AccountGroup missing(final AccountGroup.Id key) {
      final AccountGroup.NameKey name =
          new AccountGroup.NameKey("Deleted Group" + key.toString());
      final AccountGroup g = new AccountGroup(name, key, null);
      g.setType(AccountGroup.Type.SYSTEM);
      return g;
    }
  }

  static class ByNameLoader extends
      EntryCreator<AccountGroup.NameKey, AccountGroup> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByNameLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public AccountGroup createEntry(final AccountGroup.NameKey key)
        throws Exception {
      final AccountGroupName r;
      final ReviewDb db = schema.open();
      try {
        r = db.accountGroupNames().get(key);
        if (r != null) {
          return db.accountGroups().get(r.getId());
        } else {
          return null;
        }
      } finally {
        db.close();
      }
    }
  }

  static class ByUUIDLoader extends
      EntryCreator<AccountGroup.UUID, AccountGroup> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByUUIDLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public AccountGroup createEntry(final AccountGroup.UUID uuid)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        List<AccountGroup> r = db.accountGroups().byUUID(uuid).toList();
        if (r.size() == 1) {
          return r.get(0);
        } else {
          return null;
        }
      } finally {
        db.close();
      }
    }
  }

  static class ByExternalNameLoader extends
      EntryCreator<AccountGroup.ExternalNameKey, Collection<AccountGroup>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByExternalNameLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Collection<AccountGroup> createEntry(
        final AccountGroup.ExternalNameKey key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        return db.accountGroups().byExternalName(key).toList();
      } finally {
        db.close();
      }
    }
  }
}
