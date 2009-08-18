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
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

/** Tracks group objects in memory for efficient access. */
@Singleton
public class GroupCache {
  private static final String CACHE_NAME = "groups";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.Id, AccountGroup>> type =
            new TypeLiteral<Cache<AccountGroup.Id, AccountGroup>>() {};
        core(type, CACHE_NAME);
        bind(GroupCache.class);
      }
    };
  }

  private final SchemaFactory<ReviewDb> schema;
  private final SelfPopulatingCache<AccountGroup.Id, AccountGroup> byId;

  private final AccountGroup.Id administrators;

  @Inject
  GroupCache(final SchemaFactory<ReviewDb> sf, final SystemConfig cfg,
      @Named(CACHE_NAME) final Cache<AccountGroup.Id, AccountGroup> rawCache) {
    schema = sf;
    administrators = cfg.adminGroupId;

    byId = new SelfPopulatingCache<AccountGroup.Id, AccountGroup>(rawCache) {
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
  }

  public final AccountGroup.Id getAdministrators() {
    return administrators;
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

  @SuppressWarnings("unchecked")
  public AccountGroup get(final AccountGroup.Id groupId) {
    return byId.get(groupId);
  }

  public void evict(final AccountGroup.Id groupId) {
    byId.remove(groupId);
  }

  public AccountGroup lookup(final String groupName) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);

      final AccountGroup group = db.accountGroups().get(nameKey);
      if (group != null) {
        return group;
      } else {
        return null;
      }
    } finally {
      db.close();
    }
  }
}
