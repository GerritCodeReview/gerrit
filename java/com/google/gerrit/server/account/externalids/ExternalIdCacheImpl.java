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

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;

/** Caches external IDs of all accounts. The external IDs are always loaded from NoteDb. */
@Singleton
class ExternalIdCacheImpl implements ExternalIdCache {
  public static final String CACHE_NAME = "external_ids_map";

  private final LoadingCache<ObjectId, AllExternalIds> extIdsByAccount;
  private final ExternalIdReader externalIdReader;

  @Inject
  ExternalIdCacheImpl(
      @Named(CACHE_NAME) LoadingCache<ObjectId, AllExternalIds> extIdsByAccount,
      ExternalIdReader externalIdReader) {
    this.extIdsByAccount = extIdsByAccount;
    this.externalIdReader = externalIdReader;
  }

  @Override
  public Optional<ExternalId> byKey(ExternalId.Key key) throws IOException {
    return Optional.ofNullable(get().byKey().get(key));
  }

  @Override
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException {
    return get().byAccount().get(accountId);
  }

  @Override
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId, ObjectId rev) throws IOException {
    return get(rev).byAccount().get(accountId);
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
    return get(externalIdReader.readRevision());
  }

  /**
   * Returns the cached value or a freshly loaded value that will be cached after this call in case
   * the value was absent from the cache.
   *
   * <p>This method will load the value using {@link ExternalIdCacheLoader} in case it is not
   * already cached. {@link ExternalIdCacheLoader} requires loading older versions of the cached
   * value and Caffeine does not support recursive calls to the cache from loaders. Hence, we use a
   * Cache instead of a LoadingCache and perform the loading ourselves here similar to what a
   * loading cache would do.
   */
  private AllExternalIds get(ObjectId rev) throws IOException {
    try {
      return extIdsByAccount.get(rev);
    } catch (ExecutionException e) {
      throw new IOException("unable to load external ids", e);
    }
  }
}
