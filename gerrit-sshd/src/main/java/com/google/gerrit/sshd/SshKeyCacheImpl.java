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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Provides the {@link SshKeyCacheEntry}. */
@Singleton
public class SshKeyCacheImpl implements SshKeyCache {
  private static final Logger log =
      LoggerFactory.getLogger(SshKeyCacheImpl.class);
  private static final String CACHE_NAME = "sshkeys";

  static final Iterable<SshKeyCacheEntry> NO_SUCH_USER = none();
  static final Iterable<SshKeyCacheEntry> NO_KEYS = none();

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME,
            String.class,
            new TypeLiteral<Iterable<SshKeyCacheEntry>>(){})
          .loader(Loader.class);
        bind(SshKeyCacheImpl.class);
        bind(SshKeyCache.class).to(SshKeyCacheImpl.class);
      }
    };
  }

  private static Iterable<SshKeyCacheEntry> none() {
    return Collections.unmodifiableCollection(Arrays
        .asList(new SshKeyCacheEntry[0]));
  }

  private final LoadingCache<String, Iterable<SshKeyCacheEntry>> cache;

  @Inject
  SshKeyCacheImpl(
      @Named(CACHE_NAME) LoadingCache<String, Iterable<SshKeyCacheEntry>> cache) {
    this.cache = cache;
  }

  Iterable<SshKeyCacheEntry> get(String username) {
    try {
      return cache.get(username);
    } catch (ExecutionException e) {
      log.warn("Cannot load SSH keys for " + username, e);
      return Collections.emptyList();
    }
  }

  @Override
  public void evict(String username) {
    if (username != null) {
      cache.invalidate(username);
    }
  }

  @Override
  public AccountSshKey create(AccountSshKey.Id id, String encoded)
      throws InvalidSshKeyException {
    try {
      final AccountSshKey key =
          new AccountSshKey(id, SshUtil.toOpenSshPublicKey(encoded));
      SshUtil.parse(key);
      return key;
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new InvalidSshKeyException();

    } catch (NoSuchProviderException e) {
      log.error("Cannot parse SSH key", e);
      throw new InvalidSshKeyException();
    }
  }

  static class Loader extends CacheLoader<String, Iterable<SshKeyCacheEntry>> {
    private final SchemaFactory<ReviewDb> schema;
    private final GitRepositoryManager repoManager;
    private final Provider<AllUsersName> allUsersName;
    private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Loader(SchemaFactory<ReviewDb> schema,
        GitRepositoryManager repoManager,
        Provider<AllUsersName> allUsersName,
        Provider<MetaDataUpdate.User> metaDataUpdateFactory,
        IdentifiedUser.GenericFactory userFactory) {
      this.schema = schema;
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.metaDataUpdateFactory = metaDataUpdateFactory;
      this.userFactory = userFactory;
    }

    @Override
    public Iterable<SshKeyCacheEntry> load(String username) throws Exception {
      try (ReviewDb db = schema.open()) {
        AccountExternalId.Key key =
            new AccountExternalId.Key(SCHEME_USERNAME, username);
        AccountExternalId user = db.accountExternalIds().get(key);
        if (user == null) {
          return NO_SUCH_USER;
        }

        List<SshKeyCacheEntry> kl = new ArrayList<>(4);
        try (Repository git = repoManager.openRepository(allUsersName.get())) {
          VersionedAuthorizedKeys authorizedKeys =
              new VersionedAuthorizedKeys(user.getAccountId());
          authorizedKeys.load(git);
          for (AccountSshKey k : authorizedKeys.getKeys()) {
            if (k.isValid()) {
              add(kl, k);
            }
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
        kl.add(new SshKeyCacheEntry(k.getKey(), SshUtil.parse(k)));
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
        log.info("Flagging SSH key " + k.getKey() + " invalid");
        k.setInvalid();

        try (MetaDataUpdate md = metaDataUpdateFactory.get()
                .create(allUsersName.get(), userFactory.create(k.getAccount()));
            Repository git = repoManager.openRepository(allUsersName.get())) {
          VersionedAuthorizedKeys authorizedKeys =
              new VersionedAuthorizedKeys(k.getAccount());
          authorizedKeys.load(md);
          authorizedKeys.markKeyInvalid(k.getKey().get());
          authorizedKeys.commit(md);
        }
      } catch (IOException | ConfigInvalidException e) {
        log.error("Failed to mark SSH key" + k.getKey() + " invalid", e);
      }
    }
  }
}
