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
import com.google.gerrit.reviewdb.AccountGroupIncludedGroup;
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
public class IncludedGroupCacheImpl implements IncludedGroupCache {
  private static final String BYINCLUDEDGROUP_NAME = "groups_includes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.Id, Collection<AccountGroup.Id>>> byIncludedGroup =
            new TypeLiteral<Cache<AccountGroup.Id, Collection<AccountGroup.Id>>>() {};
        core(byIncludedGroup, BYINCLUDEDGROUP_NAME).populateWith(ByIncludedGroupLoader.class);

        bind(IncludedGroupCacheImpl.class);
        bind(IncludedGroupCache.class).to(IncludedGroupCacheImpl.class);
      }
    };
  }

  private final Cache<AccountGroup.Id, Collection<AccountGroup.Id>> byIncludedGroup;

  @Inject
  IncludedGroupCacheImpl(
      @Named(BYINCLUDEDGROUP_NAME) Cache<AccountGroup.Id, Collection<AccountGroup.Id>> byIncludedGroup) {
    this.byIncludedGroup = byIncludedGroup;
  }

  public Collection<AccountGroup.Id> getByIncludedGroup(final AccountGroup.Id groupId) {
    return byIncludedGroup.get(groupId);
  }

  public void evictIncludedGroup(AccountGroup.Id groupId) {
    byIncludedGroup.remove(groupId);
  }

  static class ByIncludedGroupLoader extends EntryCreator<AccountGroup.Id, Collection<AccountGroup.Id>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByIncludedGroupLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Collection<AccountGroup.Id> createEntry(final AccountGroup.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        ArrayList<AccountGroup.Id> groupArray = new ArrayList<AccountGroup.Id> ();
        for (AccountGroupIncludedGroup agig : db.accountGroupIncludedGroups().byIncludedGroup(key)) {
          groupArray.add(agig.getGroupId());
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
