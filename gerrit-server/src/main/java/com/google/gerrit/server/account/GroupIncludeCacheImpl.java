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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final String BYINCLUDE_NAME = "groups_byinclude";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.UUID, Collection<AccountGroup.UUID>>> byInclude =
            new TypeLiteral<Cache<AccountGroup.UUID, Collection<AccountGroup.UUID>>>() {};
        core(byInclude, BYINCLUDE_NAME).populateWith(ByIncludeLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final Cache<AccountGroup.UUID, Collection<AccountGroup.UUID>> byInclude;

  @Inject
  GroupIncludeCacheImpl(
      @Named(BYINCLUDE_NAME) Cache<AccountGroup.UUID, Collection<AccountGroup.UUID>> byInclude) {
    this.byInclude = byInclude;
  }

  public Collection<AccountGroup.UUID> getByInclude(AccountGroup.UUID groupId) {
    return byInclude.get(groupId);
  }

  public void evictInclude(AccountGroup.UUID groupId) {
    byInclude.remove(groupId);
  }

  static class ByIncludeLoader extends
      EntryCreator<AccountGroup.UUID, Collection<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByIncludeLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Collection<AccountGroup.UUID> createEntry(final AccountGroup.UUID key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        List<AccountGroup> group = db.accountGroups().byUUID(key).toList();
        if (group.size() != 1) {
          return Collections.emptyList();
        }

        Set<AccountGroup.Id> ids = new HashSet<AccountGroup.Id>();
        for (AccountGroupInclude agi : db.accountGroupIncludes().byInclude(group.get(0).getId())) {
          ids.add(agi.getGroupId());
        }

        Set<AccountGroup.UUID> groupArray = new HashSet<AccountGroup.UUID> ();
        for (AccountGroup g : db.accountGroups().get(ids)) {
          groupArray.add(g.getGroupUUID());
        }
        return Collections.unmodifiableCollection(groupArray);
      } finally {
        db.close();
      }
    }

    @Override
    public Collection<AccountGroup.UUID> missing(final AccountGroup.UUID key) {
      return Collections.emptyList();
    }
  }
}
