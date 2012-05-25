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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final Logger log = LoggerFactory
      .getLogger(GroupIncludeCacheImpl.class);
  private static final String BYINCLUDE_NAME = "groups_byinclude";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(BYINCLUDE_NAME,
            AccountGroup.UUID.class,
            new TypeLiteral<Set<AccountGroup.UUID>>() {})
          .loader(ByIncludeLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.UUID, Set<AccountGroup.UUID>> byInclude;

  @Inject
  GroupIncludeCacheImpl(
      @Named(BYINCLUDE_NAME) LoadingCache<AccountGroup.UUID, Set<AccountGroup.UUID>> byInclude) {
    this.byInclude = byInclude;
  }

  public Collection<AccountGroup.UUID> getByInclude(AccountGroup.UUID groupId) {
    try {
      return byInclude.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load included groups", e);
      return Collections.emptySet();
    }
  }

  public void evictInclude(AccountGroup.UUID groupId) {
    if (groupId != null) {
      byInclude.invalidate(groupId);
    }
  }

  static class ByIncludeLoader extends
      CacheLoader<AccountGroup.UUID, Set<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByIncludeLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Set<AccountGroup.UUID> load(AccountGroup.UUID key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        List<AccountGroup> group = db.accountGroups().byUUID(key).toList();
        if (group.size() != 1) {
          return Collections.emptySet();
        }

        Set<AccountGroup.Id> ids = Sets.newHashSet();
        for (AccountGroupInclude agi : db.accountGroupIncludes()
            .byInclude(group.get(0).getId())) {
          ids.add(agi.getGroupId());
        }

        Set<AccountGroup.UUID> groupArray = Sets.newHashSet();
        for (AccountGroup g : db.accountGroups().get(ids)) {
          groupArray.add(g.getGroupUUID());
        }
        return ImmutableSet.copyOf(groupArray);
      } finally {
        db.close();
      }
    }
  }
}
