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

package com.google.gerrit.sshd;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.server.ssh.SshKeyCreator;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Provides the {@link SshKeyCacheEntry}. */
@Singleton
public class SshKeyCacheImpl implements SshKeyCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "sshkeys";

  static final Iterable<SshKeyCacheEntry> NO_SUCH_USER = none();
  static final Iterable<SshKeyCacheEntry> NO_KEYS = none();

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, String.class, new TypeLiteral<Iterable<SshKeyCacheEntry>>() {})
            .loader(Loader.class);
        bind(SshKeyCacheImpl.class);
        bind(SshKeyCache.class).to(SshKeyCacheImpl.class);
        bind(SshKeyCreator.class).to(SshKeyCreatorImpl.class);
      }
    };
  }

  private static Iterable<SshKeyCacheEntry> none() {
    return Collections.unmodifiableCollection(Arrays.asList(new SshKeyCacheEntry[0]));
  }

  private final LoadingCache<String, Iterable<SshKeyCacheEntry>> cache;

  @Inject
  SshKeyCacheImpl(@Named(CACHE_NAME) LoadingCache<String, Iterable<SshKeyCacheEntry>> cache) {
    this.cache = cache;
  }

  Iterable<SshKeyCacheEntry> get(String username) {
    try {
      return cache.get(username);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot load SSH keys for %s", username);
      return Collections.emptyList();
    }
  }

  @Override
  public void evict(String username) {
    if (username != null) {
      logger.atFine().log("Evict SSH key for username %s", username);
      cache.invalidate(username);
    }
  }

  static class Loader extends CacheLoader<String, Iterable<SshKeyCacheEntry>> {
    private final ExternalIds externalIds;
    private final VersionedAuthorizedKeys.Accessor authorizedKeys;

    @Inject
    Loader(ExternalIds externalIds, VersionedAuthorizedKeys.Accessor authorizedKeys) {
      this.externalIds = externalIds;
      this.authorizedKeys = authorizedKeys;
    }

    @Override
    public Iterable<SshKeyCacheEntry> load(String username) throws Exception {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Loading SSH keys for account with username",
              Metadata.builder().username(username).build())) {
        Optional<ExternalId> user =
            externalIds.get(ExternalId.Key.create(SCHEME_USERNAME, username));
        if (!user.isPresent()) {
          return NO_SUCH_USER;
        }

        List<SshKeyCacheEntry> kl = new ArrayList<>(4);
        for (AccountSshKey k : authorizedKeys.getKeys(user.get().accountId())) {
          if (k.valid()) {
            add(kl, k);
          }
        }

        if (kl.isEmpty()) {
          return NO_KEYS;
        }
        return Collections.unmodifiableList(kl);
      }
    }

    private void add(List<SshKeyCacheEntry> kl, AccountSshKey k) {
      try {
        kl.add(new SshKeyCacheEntry(k.accountId(), SshUtil.parse(k)));
      } catch (OutOfMemoryError e) {
        // This is the only case where we assume the problem has nothing
        // to do with the key object, and instead we must abort this load.
        //
        throw e;
      } catch (Throwable e) {
        markInvalid(k);
      }
    }

    private void markInvalid(AccountSshKey k) {
      try {
        logger.atInfo().log("Flagging SSH key %d of account %s invalid", k.seq(), k.accountId());
        authorizedKeys.markKeyInvalid(k.accountId(), k.seq());
      } catch (IOException | ConfigInvalidException e) {
        logger.atSevere().withCause(e).log(
            "Failed to mark SSH key %d of account %s invalid", k.seq(), k.accountId());
      }
    }
  }
}
