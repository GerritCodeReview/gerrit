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

package com.google.gerrit.server.account.externalids;

import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Caches external IDs of all accounts. The external IDs are always loaded from NoteDb. */
@Singleton
class ExternalIdCacheImpl implements ExternalIdCache {
  private static final Logger log = LoggerFactory.getLogger(ExternalIdCacheImpl.class);

  private final LoadingCache<ObjectId, AllExternalIds> extIdsByAccount;
  private final ExternalIdReader externalIdReader;
  private final Lock lock;

  @Inject
  ExternalIdCacheImpl(ExternalIdReader externalIdReader) {
    this.extIdsByAccount =
        CacheBuilder.newBuilder()
            // The cached data is potentially pretty large and we are always only interested
            // in the latest value, hence the maximum cache size is set to 1.
            // This can lead to extra cache loads in case of the following race:
            // 1. thread 1 reads the notes ref at revision A
            // 2. thread 2 updates the notes ref to revision B and stores the derived value
            //    for B in the cache
            // 3. thread 1 attempts to read the data for revision A from the cache, and misses
            // 4. later threads attempt to read at B
            // In this race unneeded reloads are done in step 3 (reload from revision A) and
            // step 4 (reload from revision B, because the value for revision B was lost when the
            // reload from revision A was done, since the cache can hold only one entry).
            // These reloads could be avoided by increasing the cache size to 2. However the race
            // window between reading the ref and looking it up in the cache is small so that
            // it's rare that this race happens. Therefore it's not worth to double the memory
            // usage of this cache, just to avoid this.
            .maximumSize(1)
            .build(new Loader(externalIdReader));
    this.externalIdReader = externalIdReader;
    this.lock = new ReentrantLock(true /* fair */);
  }

  @Override
  public void onCreate(ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId> extIds)
      throws IOException {
    updateCache(
        oldNotesRev,
        newNotesRev,
        m -> {
          for (ExternalId extId : extIds) {
            extId.checkThatBlobIdIsSet();
            m.put(extId.accountId(), extId);
          }
        });
  }

  @Override
  public void onRemove(ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId> extIds)
      throws IOException {
    updateCache(
        oldNotesRev,
        newNotesRev,
        m -> {
          for (ExternalId extId : extIds) {
            m.remove(extId.accountId(), extId);
          }
        });
  }

  @Override
  public void onUpdate(
      ObjectId oldNotesRev, ObjectId newNotesRev, Collection<ExternalId> updatedExtIds)
      throws IOException {
    updateCache(
        oldNotesRev,
        newNotesRev,
        m -> {
          removeKeys(m.values(), updatedExtIds.stream().map(e -> e.key()).collect(toSet()));
          for (ExternalId updatedExtId : updatedExtIds) {
            updatedExtId.checkThatBlobIdIsSet();
            m.put(updatedExtId.accountId(), updatedExtId);
          }
        });
  }

  @Override
  public void onReplace(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Account.Id accountId,
      Collection<ExternalId> toRemove,
      Collection<ExternalId> toAdd)
      throws IOException {
    ExternalIdsUpdate.checkSameAccount(Iterables.concat(toRemove, toAdd), accountId);

    updateCache(
        oldNotesRev,
        newNotesRev,
        m -> {
          for (ExternalId extId : toRemove) {
            m.remove(extId.accountId(), extId);
          }
          for (ExternalId extId : toAdd) {
            extId.checkThatBlobIdIsSet();
            m.put(extId.accountId(), extId);
          }
        });
  }

  @Override
  public void onReplace(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Collection<ExternalId> toRemove,
      Collection<ExternalId> toAdd)
      throws IOException {
    updateCache(
        oldNotesRev,
        newNotesRev,
        m -> {
          for (ExternalId extId : toRemove) {
            m.remove(extId.accountId(), extId);
          }
          for (ExternalId extId : toAdd) {
            extId.checkThatBlobIdIsSet();
            m.put(extId.accountId(), extId);
          }
        });
  }

  @Override
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException {
    return get().byAccount().get(accountId);
  }

  @Override
  public ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException {
    return get().byAccount();
  }

  @Override
  public ImmutableSetMultimap<String, ExternalId> byEmails(String... emails) throws IOException {
    AllExternalIds allExternalIds = get();
    ImmutableSetMultimap.Builder<String, ExternalId> byEmails = ImmutableSetMultimap.builder();
    for (String email : emails) {
      byEmails.putAll(email, allExternalIds.byEmail().get(email));
    }
    return byEmails.build();
  }

  @Override
  public ImmutableSetMultimap<String, ExternalId> allByEmail() throws IOException {
    return get().byEmail();
  }

  private AllExternalIds get() throws IOException {
    try {
      return extIdsByAccount.get(externalIdReader.readRevision());
    } catch (ExecutionException e) {
      throw new IOException("Cannot load external ids", e);
    }
  }

  private void updateCache(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Consumer<Multimap<Account.Id, ExternalId>> update) {
    lock.lock();
    try {
      ListMultimap<Account.Id, ExternalId> m;
      if (!ObjectId.zeroId().equals(oldNotesRev)) {
        m =
            MultimapBuilder.hashKeys()
                .arrayListValues()
                .build(extIdsByAccount.get(oldNotesRev).byAccount());
      } else {
        m = MultimapBuilder.hashKeys().arrayListValues().build();
      }
      update.accept(m);
      extIdsByAccount.put(newNotesRev, AllExternalIds.create(m));
    } catch (ExecutionException e) {
      log.warn("Cannot update external IDs", e);
    } finally {
      lock.unlock();
    }
  }

  private static void removeKeys(Collection<ExternalId> ids, Collection<ExternalId.Key> toRemove) {
    Collections2.transform(ids, e -> e.key()).removeAll(toRemove);
  }

  private static class Loader extends CacheLoader<ObjectId, AllExternalIds> {
    private final ExternalIdReader externalIdReader;

    Loader(ExternalIdReader externalIdReader) {
      this.externalIdReader = externalIdReader;
    }

    @Override
    public AllExternalIds load(ObjectId notesRev) throws Exception {
      Multimap<Account.Id, ExternalId> extIdsByAccount =
          MultimapBuilder.hashKeys().arrayListValues().build();
      for (ExternalId extId : externalIdReader.all(notesRev)) {
        extId.checkThatBlobIdIsSet();
        extIdsByAccount.put(extId.accountId(), extId);
      }
      return AllExternalIds.create(extIdsByAccount);
    }
  }

  @AutoValue
  abstract static class AllExternalIds {
    static AllExternalIds create(Multimap<Account.Id, ExternalId> byAccount) {
      ImmutableSetMultimap<String, ExternalId> byEmail =
          byAccount.values().stream()
              .filter(e -> !Strings.isNullOrEmpty(e.email()))
              .collect(toImmutableSetMultimap(ExternalId::email, e -> e));
      return new AutoValue_ExternalIdCacheImpl_AllExternalIds(
          ImmutableSetMultimap.copyOf(byAccount), byEmail);
    }

    public abstract ImmutableSetMultimap<Account.Id, ExternalId> byAccount();

    public abstract ImmutableSetMultimap<String, ExternalId> byEmail();
  }
}
