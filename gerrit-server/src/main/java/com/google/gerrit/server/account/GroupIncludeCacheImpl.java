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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final String BYINCLUDE_NAME = "groups_byinclude";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.Id, Collection<AccountGroup.Id>>> byInclude =
            new TypeLiteral<Cache<AccountGroup.Id, Collection<AccountGroup.Id>>>() {};
        core(byInclude, BYINCLUDE_NAME).populateWith(ByIncludeLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final Cache<AccountGroup.Id, Collection<AccountGroup.Id>> byInclude;

  @Inject
  GroupIncludeCacheImpl(
      @Named(BYINCLUDE_NAME) Cache<AccountGroup.Id, Collection<AccountGroup.Id>> byInclude) {
    this.byInclude = byInclude;
  }

  public Collection<AccountGroup.Id> getByInclude(final AccountGroup.Id groupId) {
    return byInclude.get(groupId);
  }

  public void evictInclude(AccountGroup.Id groupId) {
    byInclude.remove(groupId);
  }

  static class ByIncludeLoader extends EntryCreator<AccountGroup.Id, Collection<AccountGroup.Id>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByIncludeLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Collection<AccountGroup.Id> createEntry(final AccountGroup.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        ArrayList<AccountGroup.Id> groupArray = new ArrayList<AccountGroup.Id> ();
        for (AccountGroupInclude agi : db.accountGroupIncludes().byInclude(key)) {
          groupArray.add(agi.getGroupId());
        }

        return Collections.unmodifiableCollection(groupArray);
      } finally {
        db.close();
      }
    }

    @Override
    public Collection<AccountGroup.Id> missing(final AccountGroup.Id key) {
      return Collections.emptyList();
    }
  }
}
