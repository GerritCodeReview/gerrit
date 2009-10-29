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

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

/** Tracks group objects in memory for efficient access. */
@Singleton
public class GroupCacheImpl implements GroupCache {
  private static final String CACHE_NAME = "groups";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<com.google.gwtorm.client.Key<?>, AccountGroup>> byId =
            new TypeLiteral<Cache<com.google.gwtorm.client.Key<?>, AccountGroup>>() {};
        core(byId, CACHE_NAME);
        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final SchemaFactory<ReviewDb> schema;
  private final AccountGroup.Id administrators;
  private final SelfPopulatingCache<AccountGroup.Id, AccountGroup> byId;
  private final SelfPopulatingCache<AccountGroup.NameKey, AccountGroup> byName;
  private final SelfPopulatingCache<AccountGroup.ExternalNameKey, AccountGroup> byExternalName;

  @Inject
  GroupCacheImpl(
      final SchemaFactory<ReviewDb> sf,
      final AuthConfig authConfig,
      @Named(CACHE_NAME) final Cache<com.google.gwtorm.client.Key<?>, AccountGroup> rawAny) {
    schema = sf;
    administrators = authConfig.getAdministratorsGroup();

    byId =
        new SelfPopulatingCache<AccountGroup.Id, AccountGroup>((Cache) rawAny) {
          @Override
          public AccountGroup createEntry(final AccountGroup.Id key)
              throws Exception {
            return lookup(key);
          }

          @Override
          protected AccountGroup missing(final AccountGroup.Id key) {
            return missingGroup(key);
          }
        };

    byName =
        new SelfPopulatingCache<AccountGroup.NameKey, AccountGroup>(
            (Cache) rawAny) {
          @Override
          public AccountGroup createEntry(final AccountGroup.NameKey key)
              throws Exception {
            return lookup(key);
          }
        };

    byExternalName =
        new SelfPopulatingCache<AccountGroup.ExternalNameKey, AccountGroup>(
            (Cache) rawAny) {
          @Override
          public AccountGroup createEntry(final AccountGroup.ExternalNameKey key)
              throws Exception {
            return lookup(key);
          }
        };
  }

  private AccountGroup lookup(final AccountGroup.Id groupId)
      throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final AccountGroup group = db.accountGroups().get(groupId);
      if (group != null) {
        return group;
      } else {
        return missingGroup(groupId);
      }
    } finally {
      db.close();
    }
  }

  private AccountGroup missingGroup(final AccountGroup.Id groupId) {
    final AccountGroup.NameKey name =
        new AccountGroup.NameKey("Deleted Group" + groupId.toString());
    final AccountGroup g = new AccountGroup(name, groupId);
    g.setAutomaticMembership(true);
    g.setOwnerGroupId(administrators);
    return g;
  }

  private AccountGroup lookup(final AccountGroup.NameKey groupName)
      throws OrmException {
    final ReviewDb db = schema.open();
    try {
      return db.accountGroups().get(groupName);
    } finally {
      db.close();
    }
  }

  private AccountGroup lookup(final AccountGroup.ExternalNameKey externalName)
      throws OrmException {
    final ReviewDb db = schema.open();
    try {
      return db.accountGroups().get(externalName);
    } finally {
      db.close();
    }
  }

  public AccountGroup get(final AccountGroup.Id groupId) {
    return byId.get(groupId);
  }

  public void evict(final AccountGroup group) {
    byId.remove(group.getId());
    byName.remove(group.getNameKey());
    byExternalName.remove(group.getExternalNameKey());
  }

  public void evictAfterRename(final AccountGroup.NameKey oldName) {
    byName.remove(oldName);
  }

  public AccountGroup lookup(final String groupName) {
    return byName.get(new AccountGroup.NameKey(groupName));
  }

  public AccountGroup get(final AccountGroup.ExternalNameKey externalName) {
    return byExternalName.get(externalName);
  }
}
