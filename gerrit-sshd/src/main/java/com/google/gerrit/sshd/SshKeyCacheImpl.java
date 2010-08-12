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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Provides the {@link SshKeyCacheEntry}. */
@Singleton
public class SshKeyCacheImpl implements SshKeyCache {
  private static final Logger log =
      LoggerFactory.getLogger(SshKeyCacheImpl.class);
  private static final String CACHE_NAME = "sshkeys";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Username, EntryList>> type =
            new TypeLiteral<Cache<Username, EntryList>>() {};
        core(type, CACHE_NAME).populateWith(Loader.class);
        bind(SshKeyCacheImpl.class);
        bind(SshKeyCache.class).to(SshKeyCacheImpl.class);
      }
    };
  }

  private final Cache<Username, EntryList> cache;

  @Inject
  SshKeyCacheImpl(@Named(CACHE_NAME) final Cache<Username, EntryList> cache) {
    this.cache = cache;
  }

  EntryList get(String username) {
    return FutureUtil.get(cache.get(new Username(username)));
  }

  public ListenableFuture<Void> evictAsync(String username) {
    if (username != null) {
      return cache.removeAsync(new Username(username));
    } else {
      return Futures.immediateFuture(null);
    }
  }

  @Override
  public AccountSshKey create(AccountSshKey.Id id, String encoded)
      throws InvalidSshKeyException {
    try {
      encoded = SshUtil.toOpenSshPublicKey(encoded);
      AccountSshKey key = new AccountSshKey(id, encoded);
      SshUtil.parse(key);
      return key;
    } catch (NoSuchAlgorithmException e) {
      throw new InvalidSshKeyException();

    } catch (InvalidKeySpecException e) {
      throw new InvalidSshKeyException();

    } catch (NoSuchProviderException e) {
      log.error("Cannot parse SSH key", e);
      throw new InvalidSshKeyException();
    }
  }

  static class Username {
    @Column(id = 1)
    String name;

    Username() {
    }

    Username(String name) {
      this.name = name;
    }
  }

  static class EntryList {
    static enum Type {
      VALID_HAS_KEYS, INVALID_USER, NO_SUCH_USER, NO_KEYS
    }

    @Column(id = 1)
    Type type;

    @Column(id = 2)
    Collection<SshKeyCacheEntry> keys;

    EntryList() {
      type = Type.NO_KEYS;
      keys = Collections.emptyList();
    }

    EntryList(Type t, Collection<SshKeyCacheEntry> k) {
      this.type = t;
      this.keys = k;
    }

    Collection<SshKeyCacheEntry> getKeys() {
      return keys;
    }

    Type getType() {
      return type;
    }
  }

  static class Loader extends EntryCreator<Username, EntryList> {
    private final SchemaFactory<ReviewDb> schema;
    private final AccountCache accountCache;

    @Inject
    Loader(SchemaFactory<ReviewDb> schema, AccountCache accountCache) {
      this.schema = schema;
      this.accountCache = accountCache;
    }

    @Override
    public EntryList createEntry(Username username) throws Exception {
      AccountExternalId user = FutureUtil.get(accountCache.get( //
          AccountExternalId.forUsername(username.name)));
      if (user == null) {
        Collection<SshKeyCacheEntry> none = Collections.emptyList();
        return new EntryList(EntryList.Type.NO_SUCH_USER, none);
      }

      final Account.Id accountId = user.getAccountId();
      final ReviewDb db = schema.open();
      try {
        List<SshKeyCacheEntry> kl = Lists.newArrayListWithExpectedSize(4);
        for (AccountSshKey k : db.accountSshKeys().byAccount(accountId)) {
          if (k.isValid()) {
            add(db, kl, k);
          }
        }
        if (kl.isEmpty()) {
          Collection<SshKeyCacheEntry> none = Collections.emptyList();
          return new EntryList(EntryList.Type.NO_KEYS, none);
        } else {
          kl = Collections.unmodifiableList(kl);
          return new EntryList(EntryList.Type.VALID_HAS_KEYS, kl);
        }
      } finally {
        db.close();
      }
    }

    @Override
    public EntryList missing(Username username) {
      Collection<SshKeyCacheEntry> none = Collections.emptyList();
      return new EntryList(EntryList.Type.INVALID_USER, none);
    }

    private void add(ReviewDb db, List<SshKeyCacheEntry> kl, AccountSshKey k) {
      try {
        kl.add(new SshKeyCacheEntry(k.getKey(), k));
      } catch (OutOfMemoryError e) {
        // This is the only case where we assume the problem has nothing
        // to do with the key object, and instead we must abort this load.
        //
        throw e;
      } catch (Throwable e) {
        markInvalid(db, k);
      }
    }

    private void markInvalid(final ReviewDb db, final AccountSshKey k) {
      try {
        log.info("Flagging SSH key " + k.getKey() + " invalid");
        k.setInvalid();
        db.accountSshKeys().update(Collections.singleton(k));
      } catch (OrmException e) {
        log.error("Failed to mark SSH key" + k.getKey() + " invalid", e);
      }
    }
  }
}
