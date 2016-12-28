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

import static java.util.stream.Collectors.toSet;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.cache.CacheModule;
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
            new TypeLiteral<Multimap<Account.Id, ExternalId>>() {})
                .maximumWeight(1).loader(Loader.class);

        bind(ExternalIdCacheImpl.class);
        bind(ExternalIdCache.class).to(ExternalIdCacheImpl.class);
      }
    };
  }

  private final LoadingCache<ListKey, Multimap<Account.Id, ExternalId>> extIdsByAccount;
  private final Lock lock;

  @Inject
  ExternalIdCacheImpl(
      @Named(CACHE_NAME) LoadingCache<ListKey,
          Multimap<Account.Id, ExternalId>> extIdsByAccount) {
    this.extIdsByAccount = extIdsByAccount;
    this.lock = new ReentrantLock(true /* fair */);
  }

  @Override
  public void onCreate(ExternalId extId) {
    onCreate(Collections.singleton(extId));
  }

  @Override
  public void onCreate(Iterable<ExternalId> extIds) {
    lock.lock();
    try {
      Multimap<Account.Id, ExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (ExternalId extId : extIds) {
        n.put(extId.accountId(), extId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void remove(ExternalId extId) {
    remove(Collections.singleton(extId));
  }

  @Override
  public void remove(Iterable<ExternalId> extIds) {
    lock.lock();
    try {
      Multimap<Account.Id, ExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (ExternalId extId : extIds) {
        n.remove(extId.accountId(), extId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void remove(Account.Id accountId, ExternalId.Key extIdKey) {
    remove(accountId, Collections.singleton(extIdKey));
  }

  @Override
  public void remove(Account.Id accountId, Iterable<ExternalId.Key> extIdKeys) {
    lock.lock();
    try {
      Multimap<Account.Id, ExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (ExternalId extId : byAccount(accountId)) {
        for (ExternalId.Key extIdKey : extIdKeys) {
          if (extIdKey.equals(extId.key())) {
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
  public void update(ExternalId updatedExtId) {
    update(Collections.singleton(updatedExtId));
  }

  @Override
  public void update(Iterable<ExternalId> updatedExtIds) {
    lock.lock();
    try {
      Multimap<Account.Id, ExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (ExternalId updatedExtId : updatedExtIds) {
        for (ExternalId extId : byAccount(updatedExtId.accountId())) {
          if (updatedExtId.key().equals(extId.key())) {
            n.remove(updatedExtId.accountId(), extId);
            break;
          }
        }
        n.put(updatedExtId.accountId(), updatedExtId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void replace(Account.Id accountId, Iterable<ExternalId> toRemove,
      Iterable<ExternalId> toAdd) {
    ExternalIdsUpdate.checkSameAccount(Iterables.concat(toRemove, toAdd), accountId);
    lock.lock();
    try {
      Multimap<Account.Id, ExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (ExternalId extId : toRemove) {
        n.remove(extId.accountId(), extId);
      }
      for (ExternalId extId : toAdd) {
        n.put(extId.accountId(), extId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void replaceByKeys(Account.Id accountId,
      Iterable<ExternalId.Key> toRemove, Iterable<ExternalId> toAdd) {
    ExternalIdsUpdate.checkSameAccount(toAdd, accountId);
    lock.lock();
    try {
      Multimap<Account.Id, ExternalId> n = MultimapBuilder.hashKeys()
          .arrayListValues().build(extIdsByAccount.get(ListKey.ALL));
      for (ExternalId extId : byAccount(accountId)) {
        for (ExternalId.Key extIdKey : toRemove) {
          if (extIdKey.equals(extId.key())) {
            n.remove(accountId, extId);
          }
        }
      }
      for (ExternalId extId : toAdd) {
        n.put(extId.accountId(), extId);
      }
      extIdsByAccount.put(ListKey.ALL, ImmutableMultimap.copyOf(n));
    } catch (ExecutionException e) {
      log.warn("Cannot list avaliable projects", e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Collection<ExternalId> byAccount(Account.Id accountId) {
    try {
      return ImmutableSet
          .copyOf(extIdsByAccount.get(ListKey.ALL).get(accountId));
    } catch (ExecutionException e) {
      log.warn("Cannot list external ids", e);
      return Collections.emptySet();
    }
  }

  @Override
  public Collection<ExternalId> byAccount(Account.Id accountId, String scheme) {
    return byAccount(accountId).stream()
        .filter(e -> e.key().isScheme(scheme))
        .collect(toSet());
  }

  static class ListKey {
    static final ListKey ALL = new ListKey();

    private ListKey() {
    }
  }

  static class Loader
      extends CacheLoader<ListKey, Multimap<Account.Id, ExternalId>> {
    private final ExternalIds externalIds;

    @Inject
    Loader(ExternalIds externalIds) {
      this.externalIds = externalIds;
    }

    @Override
    public Multimap<Account.Id, ExternalId> load(ListKey key) throws Exception {
      Multimap<Account.Id, ExternalId> extIdsByAccount =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (ExternalId extId : externalIds.all()) {
        extIdsByAccount.put(extId.accountId(), extId);
      }
      return ImmutableMultimap.copyOf(extIdsByAccount);
    }
  }
}
