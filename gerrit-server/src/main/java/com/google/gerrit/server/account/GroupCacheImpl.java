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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.NameKey;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Tracks group objects in memory for efficient access. */
@Singleton
public class GroupCacheImpl implements GroupCache {
  private static final String BYID_NAME = "groups";
  private static final String BYNAME_NAME = "groups_byname";
  private static final String BYUUID_NAME = "groups_byuuid";
  private static final String BYNAME_LIST = "groups_byname_list";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<AccountGroup.Id, AccountGroup>> byId =
            new TypeLiteral<Cache<AccountGroup.Id, AccountGroup>>() {};
        core(byId, BYID_NAME).populateWith(ByIdLoader.class);

        final TypeLiteral<Cache<AccountGroup.NameKey, AccountGroup.Id>> byName =
            new TypeLiteral<Cache<AccountGroup.NameKey, AccountGroup.Id>>() {};
        core(byName, BYNAME_NAME).populateWith(ByNameLoader.class);

        final TypeLiteral<Cache<AccountGroup.UUID, AccountGroup.Id>> byUUID =
            new TypeLiteral<Cache<AccountGroup.UUID, AccountGroup.Id>>() {};
        core(byUUID, BYUUID_NAME).populateWith(ByUUIDLoader.class);

        final TypeLiteral<Cache<ListKey, SortedSet<AccountGroup.NameKey>>> listType =
          new TypeLiteral<Cache<ListKey, SortedSet<AccountGroup.NameKey>>>() {};
        core(listType, BYNAME_LIST).populateWith(Lister.class);

        bind(GroupCacheImpl.class);
        bind(GroupCache.class).to(GroupCacheImpl.class);
      }
    };
  }

  private final Cache<AccountGroup.Id, AccountGroup> byId;
  private final Cache<AccountGroup.NameKey, AccountGroup.Id> byName;
  private final Cache<AccountGroup.UUID, AccountGroup.Id> byUUID;
  private final Cache<ListKey,SortedSet<AccountGroup.NameKey>> list;
  private final Lock listLock;

  @Inject
  GroupCacheImpl(
      @Named(BYID_NAME) Cache<AccountGroup.Id, AccountGroup> byId,
      @Named(BYNAME_NAME) Cache<AccountGroup.NameKey, AccountGroup.Id> byName,
      @Named(BYUUID_NAME) Cache<AccountGroup.UUID, AccountGroup.Id> byUUID,
      @Named(BYNAME_LIST) final Cache<ListKey, SortedSet<AccountGroup.NameKey>> list) {
    this.byId = byId;
    this.byName = byName;
    this.byUUID = byUUID;
    this.list = list;
    this.listLock = new ReentrantLock(true /* fair */);
  }

  @Override
  public AccountGroup get(final AccountGroup.Id groupId) {
    return byId.get(groupId);
  }

  @Override
  public void evict(final AccountGroup group) {
    byId.remove(group.getId());
  }

  @Override
  public void evictAfterRename(final AccountGroup.NameKey oldName,
      final AccountGroup.NameKey newName) {
    byName.remove(oldName);
    updateGroupList(oldName, newName);
  }

  @Override
  public AccountGroup get(final AccountGroup.NameKey name) {
    AccountGroup.Id groupId = byName.get(name);
    if (groupId == null) {
      return null;
    }
    return get(groupId);
  }

  @Override
  public AccountGroup get(final AccountGroup.UUID uuid) {
    AccountGroup.Id groupId = byUUID.get(uuid);
    if (groupId == null) {
      return null;
    }
    return get(groupId);
  }

  @Override
  public Iterable<AccountGroup> all() {
    SortedSet<NameKey> names = list.get(ListKey.ALL);
    List<AccountGroup> groups = Lists.newArrayListWithCapacity(names.size());
    for (AccountGroup.NameKey groupName : names) {
      AccountGroup group = get(groupName);
      if (group != null) {
        groups.add(group);
      }
    }
    return Collections.unmodifiableList(groups);
  }

  @Override
  public void onCreateGroup(final AccountGroup.NameKey newGroupName) {
    updateGroupList(null, newGroupName);
  }

  private void updateGroupList(final AccountGroup.NameKey nameToRemove,
      final AccountGroup.NameKey nameToAdd) {
    listLock.lock();
    try {
      SortedSet<AccountGroup.NameKey> n = list.get(ListKey.ALL);
      n = new TreeSet<AccountGroup.NameKey>(n);
      if (nameToRemove != null) {
        n.remove(nameToRemove);
      }
      if (nameToAdd != null) {
        n.add(nameToAdd);
      }
      list.put(ListKey.ALL, Collections.unmodifiableSortedSet(n));
    } finally {
      listLock.unlock();
    }
  }

  static class ByIdLoader extends EntryCreator<AccountGroup.Id, AccountGroup> {
    private final SchemaFactory<ReviewDb> schema;
    private final Cache<AccountGroup.NameKey, AccountGroup.Id> byName;
    private final Cache<AccountGroup.UUID, AccountGroup.Id> byUUID;

    @Inject
    ByIdLoader(
        final SchemaFactory<ReviewDb> sf,
        @Named(BYNAME_NAME) Cache<AccountGroup.NameKey, AccountGroup.Id> byName,
        @Named(BYUUID_NAME) Cache<AccountGroup.UUID, AccountGroup.Id> byUUID) {
      schema = sf;
      this.byName = byName;
      this.byUUID = byUUID;
    }

    @Override
    public AccountGroup createEntry(final AccountGroup.Id key) throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountGroup group = db.accountGroups().get(key);
        if (group != null) {
          byName.put(group.getNameKey(), group.getId());
          byUUID.put(group.getGroupUUID(), group.getId());
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
      EntryCreator<AccountGroup.NameKey, AccountGroup.Id> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    ByNameLoader(final SchemaFactory<ReviewDb> sf) {
      schema = sf;
    }

    @Override
    public AccountGroup.Id createEntry(final AccountGroup.NameKey key)
        throws Exception {
      final AccountGroupName r;
      final ReviewDb db = schema.open();
      try {
        r = db.accountGroupNames().get(key);
        if (r != null) {
          return r.getId();
        } else {
          return null;
        }
      } finally {
        db.close();
      }
    }
  }

  static class ByUUIDLoader extends
      EntryCreator<AccountGroup.UUID, AccountGroup.Id> {
    private final SchemaFactory<ReviewDb> schema;
    private final Cache<AccountGroup.Id, AccountGroup> byId;
    private final Cache<AccountGroup.NameKey, AccountGroup.Id> byName;

    @Inject
    ByUUIDLoader(
        final SchemaFactory<ReviewDb> sf,
        @Named(BYID_NAME) Cache<AccountGroup.Id, AccountGroup> byId,
        @Named(BYNAME_NAME) Cache<AccountGroup.NameKey, AccountGroup.Id> byName) {
      schema = sf;
      this.byId = byId;
      this.byName = byName;
    }

    @Override
    public AccountGroup.Id createEntry(final AccountGroup.UUID uuid)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        List<AccountGroup> r = db.accountGroups().byUUID(uuid).toList();
        if (r.size() == 1) {
          AccountGroup group = r.get(0);
          byId.put(group.getId(), group);
          byName.put(group.getNameKey(), group.getId());
          return group.getId();
        } else {
          return null;
        }
      } finally {
        db.close();
      }
    }
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  static class Lister extends EntryCreator<ListKey, SortedSet<AccountGroup.NameKey>> {
    private final SchemaFactory<ReviewDb> schema;
    private final Cache<AccountGroup.Id, AccountGroup> byId;
    private final Cache<AccountGroup.NameKey, AccountGroup.Id> byName;
    private final Cache<AccountGroup.UUID, AccountGroup.Id> byUUID;

    @Inject
    Lister(
        final SchemaFactory<ReviewDb> sf,
        @Named(BYID_NAME) Cache<AccountGroup.Id, AccountGroup> byId,
        @Named(BYNAME_NAME) Cache<AccountGroup.NameKey, AccountGroup.Id> byName,
        @Named(BYUUID_NAME) Cache<AccountGroup.UUID, AccountGroup.Id> byUUID) {
      schema = sf;
      this.byId = byId;
      this.byName = byName;
      this.byUUID = byUUID;
    }

    @Override
    public SortedSet<AccountGroup.NameKey> createEntry(ListKey key)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        SortedSet<AccountGroup.NameKey> names = Sets.newTreeSet();
        for (AccountGroup group : db.accountGroups().all()) {
          byId.put(group.getId(), group);
          byName.put(group.getNameKey(), group.getId());
          byUUID.put(group.getGroupUUID(), group.getId());
          names.add(group.getNameKey());
        }
        return Collections.unmodifiableSortedSet(names);
      } finally {
        db.close();
      }
    }
  }
}
