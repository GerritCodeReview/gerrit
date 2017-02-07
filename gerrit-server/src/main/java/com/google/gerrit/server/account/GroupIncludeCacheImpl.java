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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks group inclusions in memory for efficient access. */
@Singleton
public class GroupIncludeCacheImpl implements GroupIncludeCache {
  private static final Logger log = LoggerFactory.getLogger(GroupIncludeCacheImpl.class);
  private static final String PARENT_GROUPS_NAME = "groups_byinclude";
  private static final String SUBGROUPS_NAME = "groups_members";
  private static final String EXTERNAL_NAME = "groups_external";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(
                PARENT_GROUPS_NAME,
                AccountGroup.UUID.class,
                new TypeLiteral<Set<AccountGroup.UUID>>() {})
            .loader(ParentGroupsLoader.class);

        cache(SUBGROUPS_NAME, AccountGroup.UUID.class, new TypeLiteral<Set<AccountGroup.UUID>>() {})
            .loader(SubgroupsLoader.class);

        cache(EXTERNAL_NAME, String.class, new TypeLiteral<Set<AccountGroup.UUID>>() {})
            .loader(AllExternalLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.UUID, Set<AccountGroup.UUID>> subgroups;
  private final LoadingCache<AccountGroup.UUID, Set<AccountGroup.UUID>> parentGroups;
  private final LoadingCache<String, Set<AccountGroup.UUID>> external;

  @Inject
  GroupIncludeCacheImpl(
      @Named(SUBGROUPS_NAME) LoadingCache<AccountGroup.UUID, Set<AccountGroup.UUID>> subgroups,
      @Named(PARENT_GROUPS_NAME)
          LoadingCache<AccountGroup.UUID, Set<AccountGroup.UUID>> parentGroups,
      @Named(EXTERNAL_NAME) LoadingCache<String, Set<AccountGroup.UUID>> external) {
    this.subgroups = subgroups;
    this.parentGroups = parentGroups;
    this.external = external;
  }

  @Override
  public Set<AccountGroup.UUID> subgroupsOf(AccountGroup.UUID groupId) {
    try {
      return subgroups.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load members of group", e);
      return Collections.emptySet();
    }
  }

  @Override
  public Set<AccountGroup.UUID> parentGroupsOf(AccountGroup.UUID groupId) {
    try {
      return parentGroups.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load included groups", e);
      return Collections.emptySet();
    }
  }

  @Override
  public void evictSubgroupsOf(AccountGroup.UUID groupId) {
    if (groupId != null) {
      subgroups.invalidate(groupId);
    }
  }

  @Override
  public void evictParentGroupsOf(AccountGroup.UUID groupId) {
    if (groupId != null) {
      parentGroups.invalidate(groupId);

      if (!AccountGroup.isInternalGroup(groupId)) {
        external.invalidate(EXTERNAL_NAME);
      }
    }
  }

  @Override
  public Set<AccountGroup.UUID> allExternalMembers() {
    try {
      return external.get(EXTERNAL_NAME);
    } catch (ExecutionException e) {
      log.warn("Cannot load set of non-internal groups", e);
      return Collections.emptySet();
    }
  }

  static class SubgroupsLoader extends CacheLoader<AccountGroup.UUID, Set<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    SubgroupsLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Set<AccountGroup.UUID> load(AccountGroup.UUID key) throws Exception {
      try (ReviewDb db = schema.open()) {
        List<AccountGroup> group = db.accountGroups().byUUID(key).toList();
        if (group.size() != 1) {
          return Collections.emptySet();
        }

        Set<AccountGroup.UUID> ids = new HashSet<>();
        for (AccountGroupById agi : db.accountGroupById().byGroup(group.get(0).getId())) {
          ids.add(agi.getIncludeUUID());
        }
        return ImmutableSet.copyOf(ids);
      }
    }
  }

  static class ParentGroupsLoader extends CacheLoader<AccountGroup.UUID, Set<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ParentGroupsLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Set<AccountGroup.UUID> load(AccountGroup.UUID key) throws Exception {
      try (ReviewDb db = schema.open()) {
        Set<AccountGroup.Id> ids = new HashSet<>();
        for (AccountGroupById agi : db.accountGroupById().byIncludeUUID(key)) {
          ids.add(agi.getGroupId());
        }

        Set<AccountGroup.UUID> groupArray = new HashSet<>();
        for (AccountGroup g : db.accountGroups().get(ids)) {
          groupArray.add(g.getGroupUUID());
        }
        return ImmutableSet.copyOf(groupArray);
      }
    }
  }

  static class AllExternalLoader extends CacheLoader<String, Set<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    AllExternalLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public Set<AccountGroup.UUID> load(String key) throws Exception {
      try (ReviewDb db = schema.open()) {
        Set<AccountGroup.UUID> ids = new HashSet<>();
        for (AccountGroupById agi : db.accountGroupById().all()) {
          if (!AccountGroup.isInternalGroup(agi.getIncludeUUID())) {
            ids.add(agi.getIncludeUUID());
          }
        }
        return ImmutableSet.copyOf(ids);
      }
    }
  }
}
