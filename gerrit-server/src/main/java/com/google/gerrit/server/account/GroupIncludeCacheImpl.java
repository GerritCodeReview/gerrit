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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.group.Groups;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
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
                new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(ParentGroupsLoader.class);

        cache(
                SUBGROUPS_NAME,
                AccountGroup.UUID.class,
                new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(SubgroupsLoader.class);

        cache(EXTERNAL_NAME, String.class, new TypeLiteral<ImmutableList<AccountGroup.UUID>>() {})
            .loader(AllExternalLoader.class);

        bind(GroupIncludeCacheImpl.class);
        bind(GroupIncludeCache.class).to(GroupIncludeCacheImpl.class);
      }
    };
  }

  private final LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> subgroups;
  private final LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> parentGroups;
  private final LoadingCache<String, ImmutableList<AccountGroup.UUID>> external;

  @Inject
  GroupIncludeCacheImpl(
      @Named(SUBGROUPS_NAME)
          LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> subgroups,
      @Named(PARENT_GROUPS_NAME)
          LoadingCache<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> parentGroups,
      @Named(EXTERNAL_NAME) LoadingCache<String, ImmutableList<AccountGroup.UUID>> external) {
    this.subgroups = subgroups;
    this.parentGroups = parentGroups;
    this.external = external;
  }

  @Override
  public Collection<AccountGroup.UUID> subgroupsOf(AccountGroup.UUID groupId) {
    try {
      return subgroups.get(groupId);
    } catch (ExecutionException e) {
      log.warn("Cannot load members of group", e);
      return Collections.emptySet();
    }
  }

  @Override
  public Collection<AccountGroup.UUID> parentGroupsOf(AccountGroup.UUID groupId) {
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
  public Collection<AccountGroup.UUID> allExternalMembers() {
    try {
      return external.get(EXTERNAL_NAME);
    } catch (ExecutionException e) {
      log.warn("Cannot load set of non-internal groups", e);
      return ImmutableList.of();
    }
  }

  static class SubgroupsLoader
      extends CacheLoader<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    SubgroupsLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(AccountGroup.UUID key)
        throws OrmException, NoSuchGroupException {
      try (ReviewDb db = schema.open()) {
        return groups.getIncludes(db, key).collect(toImmutableList());
      }
    }
  }

  static class ParentGroupsLoader
      extends CacheLoader<AccountGroup.UUID, ImmutableList<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final GroupCache groupCache;
    private final Groups groups;

    @Inject
    ParentGroupsLoader(SchemaFactory<ReviewDb> sf, GroupCache groupCache, Groups groups) {
      schema = sf;
      this.groupCache = groupCache;
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(AccountGroup.UUID key) throws OrmException {
      try (ReviewDb db = schema.open()) {
        return groups
            .getParentGroups(db, key)
            .map(groupCache::get)
            .map(AccountGroup::getGroupUUID)
            .filter(Objects::nonNull)
            .collect(toImmutableList());
      }
    }
  }

  static class AllExternalLoader extends CacheLoader<String, ImmutableList<AccountGroup.UUID>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Groups groups;

    @Inject
    AllExternalLoader(SchemaFactory<ReviewDb> sf, Groups groups) {
      schema = sf;
      this.groups = groups;
    }

    @Override
    public ImmutableList<AccountGroup.UUID> load(String key) throws Exception {
      try (ReviewDb db = schema.open()) {
        return groups.getExternalGroups(db).collect(toImmutableList());
      }
    }
  }
}
