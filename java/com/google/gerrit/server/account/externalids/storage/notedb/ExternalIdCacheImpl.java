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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.ObjectIdCacheSerializer;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Caches external IDs of all accounts. The external IDs are always loaded from NoteDb. *
 *
 * <p>This class should be bounded as a Singleton. However, due to internal limitations in Google,
 * it cannot be marked as a singleton. The common installation pattern should therefore be:
 *
 * <pre>{@code
 * * install(new ExternalIdCacheModule());
 * * install(new ExternalIdCacheBindingModule());
 * *
 * }</pre>
 */
public class ExternalIdCacheImpl implements ExternalIdCache {
  public static final String CACHE_NAME = "external_ids_map";

  public static class ExternalIdCacheModule extends CacheModule {
    @Override
    protected void configure() {
      persist(CACHE_NAME, ObjectId.class, new TypeLiteral<AllExternalIds>() {})
          // The cached data is potentially pretty large and we are always only interested
          // in the latest value. However, due to a race condition, it is possible for different
          // threads to observe different values of the meta ref, and hence request different keys
          // from the cache. Extend the cache size by 1 to cover this case, but expire the extra
          // object after a short period of time, since it may be a potentially large amount of
          // memory.
          // When loading a new value because the primary data advanced, we want to leverage the old
          // cache state to recompute only what changed. This doesn't affect cache size though as
          // Guava calls the loader first and evicts later on.
          .maximumWeight(2)
          .expireFromMemoryAfterAccess(Duration.ofMinutes(1))
          .diskLimit(-1)
          .version(1)
          .keySerializer(ObjectIdCacheSerializer.INSTANCE)
          .valueSerializer(AllExternalIds.Serializer.INSTANCE);
    }
  }

  public static class ExternalIdCacheBindingModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(ExternalIdCache.class).to(ExternalIdCacheImpl.class).in(SINGLETON);
    }

    /**
     * Used by {@link ExternalIdsNoteDbImpl}. Modules which bind {@link ExternalIdCache} by using
     * modules other than {@link ExternalIdCacheBindingModule}, should also provide an {@code
     * Optional<ExternalIdCacheImpl>} binding.
     */
    @Provides
    @Singleton
    Optional<ExternalIdCacheImpl> provideNoteDbExternalIdCacheImpl(
        ExternalIdCacheImpl externalIdCache) {
      return Optional.of(externalIdCache);
    }
  }

  private final Cache<ObjectId, AllExternalIds> extIdsByAccount;
  private final ExternalIdReader externalIdReader;
  private final ExternalIdCacheLoader externalIdCacheLoader;
  private final Lock lock;

  @Inject
  ExternalIdCacheImpl(
      @Named(CACHE_NAME) Cache<ObjectId, AllExternalIds> extIdsByAccount,
      ExternalIdReader externalIdReader,
      ExternalIdCacheLoader externalIdCacheLoader) {
    this.extIdsByAccount = extIdsByAccount;
    this.externalIdReader = externalIdReader;
    this.externalIdCacheLoader = externalIdCacheLoader;
    this.lock = new ReentrantLock(true /* fair */);
  }

  @Override
  public Optional<ExternalId> byKey(ExternalId.Key key) throws IOException {
    return Optional.ofNullable(get().byKey().get(key));
  }

  @Override
  public ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException {
    return get().byAccount().get(accountId);
  }

  ImmutableSet<ExternalId> byAccount(Account.Id accountId, ObjectId rev) throws IOException {
    return get(rev).byAccount().get(accountId);
  }

  @Override
  public ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException {
    return get().byAccount();
  }

  /**
   * Each access to the external ID cache requires reading the SHA1 of the refs/meta/external-ids
   * branch. If external IDs for multiple emails are needed it is more efficient to use {@link
   * #byEmails(String...)} as this method reads the SHA1 of the refs/meta/external-ids branch only
   * once (and not once per email).
   *
   * @see #byEmails(String...)
   */
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
   * Returns the cached value or a freshly loaded value that will be cached with this call in case
   * the value was absent from the cache.
   *
   * <p>This method will load the value using {@link ExternalIdCacheLoader} in case it is not
   * already cached. {@link ExternalIdCacheLoader} requires loading older versions of the cached
   * value and Caffeine does not support recursive calls to the cache from loaders. Hence, we use a
   * Cache instead of a LoadingCache and perform the loading ourselves here similar to what a
   * loading cache would do.
   */
  private AllExternalIds get(ObjectId rev) throws IOException {
    AllExternalIds cachedValue = extIdsByAccount.getIfPresent(rev);
    if (cachedValue != null) {
      return cachedValue;
    }

    // Load the value and put it in the cache.
    lock.lock();
    try {
      // Check if value was already loaded while waiting for the lock.
      cachedValue = extIdsByAccount.getIfPresent(rev);
      if (cachedValue != null) {
        return cachedValue;
      }

      AllExternalIds newlyLoadedValue;
      try {
        newlyLoadedValue = externalIdCacheLoader.load(rev);
      } catch (ConfigInvalidException e) {
        throw new IOException("Cannot load external ids", e);
      }
      extIdsByAccount.put(rev, newlyLoadedValue);
      return newlyLoadedValue;
    } finally {
      lock.unlock();
    }
  }
}
