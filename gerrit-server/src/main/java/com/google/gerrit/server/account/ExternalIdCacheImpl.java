// Copyright (C) 2016 The Android Open Source Project
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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class ExternalIdCacheImpl implements ExternalIdCache {
  private static final Logger log =
      LoggerFactory.getLogger(ExternalIdCacheImpl.class);

  private static final String CACHE_NAME = "external_ids_map";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, ListKey.class,
            new TypeLiteral<Multimap<Account.Id, AccountExternalId>>() {})
                .maximumWeight(1).loader(Loader.class);

        bind(ExternalIdCacheImpl.class);
        bind(ExternalIdCache.class).to(ExternalIdCacheImpl.class);
      }
    };
  }

  private final LoadingCache<ListKey,
      Multimap<Account.Id, AccountExternalId>> extIdsByAccount;
  private final Lock lock;

  @Inject
  ExternalIdCacheImpl(
      @Named(CACHE_NAME) LoadingCache<ListKey,
          Multimap<Account.Id, AccountExternalId>> extIdsByAccount) {
    this.extIdsByAccount = extIdsByAccount;
    this.lock = new ReentrantLock(true /* fair */);
  }

  @Override
  public void onCreate(AccountExternalId extId) {
    onCreate(Collections.singleton(extId));
  }

  @Override
  public void onCreate(Iterable<AccountExternalId> extIds) {
    lock.lock();
    try {
      Multimap<Account.Id, AccountExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (AccountExternalId extId : extIds) {
        n.put(extId.getAccountId(), extId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void remove(AccountExternalId extId) {
    remove(Collections.singleton(extId));
  }

  @Override
  public void remove(Iterable<AccountExternalId> extIds) {
    lock.lock();
    try {
      Multimap<Account.Id, AccountExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (AccountExternalId extId : extIds) {
        n.remove(extId.getAccountId(), extId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void remove(Account.Id accountId, AccountExternalId.Key extIdKey) {
    remove(accountId, Collections.singleton(extIdKey));
  }

  @Override
  public void remove(Account.Id accountId,
      Iterable<AccountExternalId.Key> extIdKeys) {
    lock.lock();
    try {
      Multimap<Account.Id, AccountExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (AccountExternalId extId : byAccount(accountId)) {
        for (AccountExternalId.Key extIdKey : extIdKeys) {
          if (extIdKey.equals(extId.getKey())) {
            n.remove(accountId, extId);
            break;
          }
        }
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void update(AccountExternalId updatedExtId) {
    lock.lock();
    try {
      Multimap<Account.Id, AccountExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (AccountExternalId extId : byAccount(updatedExtId.getAccountId())) {
        if (updatedExtId.getKey().equals(extId.getKey())) {
          n.remove(updatedExtId.getAccountId(), extId);
          break;
        }
      }
      n.put(updatedExtId.getAccountId(), updatedExtId);
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Collection<AccountExternalId> byAccount(Account.Id accountId) {
    try {
      return ImmutableSet
          .copyOf(extIdsByAccount.get(ListKey.ALL).get(accountId));
    } catch (ExecutionException e) {
      log.warn("Cannot list external ids", e);
      return Collections.emptySet();
    }
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  static class Loader
      extends CacheLoader<ListKey, Multimap<Account.Id, AccountExternalId>> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    Loader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public Multimap<Account.Id, AccountExternalId> load(ListKey key)
        throws Exception {
      try (ReviewDb db = schema.open()) {
        Multimap<Account.Id, AccountExternalId> extIdsByAccount =
            MultimapBuilder.hashKeys().arrayListValues().build();
        for (AccountExternalId extId : db.accountExternalIds().all()) {
          extIdsByAccount.put(extId.getAccountId(), extId);
        }
        return ImmutableMultimap.copyOf(extIdsByAccount);
      }
    }
  }
}
