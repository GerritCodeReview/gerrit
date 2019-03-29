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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.ObjectId;

/** Caches external IDs of all accounts. The external IDs are always loaded from NoteDb. */
@Singleton
class ExternalIdCacheImpl implements ExternalIdCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String CACHE_NAME = "external_ids_map";

  private final LoadingCache<ObjectId, AllExternalIds> extIdsByAccount;
  private final ExternalIdReader externalIdReader;
  private final Lock lock;

  @Inject
  ExternalIdCacheImpl(
      @Named(CACHE_NAME) LoadingCache<ObjectId, AllExternalIds> extIdsByAccount,
      ExternalIdReader externalIdReader) {
    this.extIdsByAccount = extIdsByAccount;
    this.externalIdReader = externalIdReader;
    this.lock = new ReentrantLock(true /* fair */);
  }

  @Override
  public void onReplace(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Collection<ExternalId> toRemove,
      Collection<ExternalId> toAdd) {
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
  public Set<ExternalId> byAccount(Account.Id accountId) throws IOException {
    return get().byAccount().get(accountId);
  }

  @Override
  public Set<ExternalId> byAccount(Account.Id accountId, ObjectId rev) throws IOException {
    return get(rev).byAccount().get(accountId);
  }

  @Override
  public SetMultimap<String, ExternalId> byEmails(String... emails) throws IOException {
    AllExternalIds allExternalIds = get();
    ImmutableSetMultimap.Builder<String, ExternalId> byEmails = ImmutableSetMultimap.builder();
    for (String email : emails) {
      byEmails.putAll(email, allExternalIds.byEmail().get(email));
    }
    return byEmails.build();
  }

  private AllExternalIds get() throws IOException {
    return get(externalIdReader.readRevision());
  }

  private AllExternalIds get(ObjectId rev) throws IOException {
    try {
      return extIdsByAccount.get(rev);
    } catch (ExecutionException e) {
      throw new IOException("Cannot load external ids", e);
    }
  }

  private void updateCache(
      ObjectId oldNotesRev,
      ObjectId newNotesRev,
      Consumer<SetMultimap<Account.Id, ExternalId>> update) {
    if (oldNotesRev.equals(newNotesRev)) {
      // No need to update external id cache since there is no update to those external ids.
      return;
    }

    lock.lock();
    try {
      SetMultimap<Account.Id, ExternalId> m;
      if (!ObjectId.zeroId().equals(oldNotesRev)) {
        m =
            MultimapBuilder.hashKeys()
                .hashSetValues()
                .build(extIdsByAccount.get(oldNotesRev).byAccount());
      } else {
        m = MultimapBuilder.hashKeys().hashSetValues().build();
      }
      update.accept(m);
      extIdsByAccount.put(newNotesRev, AllExternalIds.create(m));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot update external IDs");
    } finally {
      lock.unlock();
    }
  }

  static class Loader extends CacheLoader<ObjectId, AllExternalIds> {
    private final ExternalIdReader externalIdReader;

    @Inject
    Loader(ExternalIdReader externalIdReader) {
      this.externalIdReader = externalIdReader;
    }

    @Override
    public AllExternalIds load(ObjectId notesRev) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer("Loading external IDs (revision=%s)", notesRev)) {
        ImmutableSet<ExternalId> externalIds = externalIdReader.all(notesRev);
        externalIds.forEach(ExternalId::checkThatBlobIdIsSet);
        return AllExternalIds.create(externalIds);
      }
    }
  }
}
