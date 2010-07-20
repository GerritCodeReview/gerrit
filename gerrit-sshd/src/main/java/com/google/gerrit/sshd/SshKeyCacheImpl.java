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

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gerrit.server.ssh.SshKeyCache;
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
import java.util.ArrayList;
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
        final TypeLiteral<Cache<Account.Username, SshKeyCacheEntryCollection>> type =
            new TypeLiteral<Cache<Account.Username, SshKeyCacheEntryCollection>>() {};
        core(type, CACHE_NAME).populateWith(Loader.class);
        bind(SshKeyCacheImpl.class);
        bind(SshKeyCache.class).to(SshKeyCacheImpl.class);
      }
    };
  }

  private final Cache<Account.Username, SshKeyCacheEntryCollection> cache;

  @Inject
  SshKeyCacheImpl(
      @Named(CACHE_NAME) final Cache<Account.Username, SshKeyCacheEntryCollection> cache) {
    this.cache = cache;
  }

  public SshKeyCacheEntryCollection get(String username) {
    return cache.get(new Account.Username(username));
  }

  public void evict(String username) {
    cache.remove(new Account.Username(username));
  }

  @Override
  public AccountSshKey create(AccountSshKey.Id id, String encoded)
      throws InvalidSshKeyException {
    try {
      final AccountSshKey key =
          new AccountSshKey(id, SshUtil.toOpenSshPublicKey(encoded));
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

  static class Loader extends EntryCreator<Account.Username, SshKeyCacheEntryCollection> {
    private final SchemaFactory<ReviewDb> schema;

    @Inject
    Loader(SchemaFactory<ReviewDb> schema) {
      this.schema = schema;
    }

    @Override
    public SshKeyCacheEntryCollection createEntry(Account.Username username)
        throws Exception {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId.Key key =
            new AccountExternalId.Key(SCHEME_USERNAME, username.get());
        final AccountExternalId user = db.accountExternalIds().get(key);
        if (user == null) {
          return new SshKeyCacheEntryCollection(
              SshKeyCacheEntryCollection.Type.NO_SUCH_USER);
        }

        final List<SshKeyCacheEntry> kl = new ArrayList<SshKeyCacheEntry>(4);
        for (AccountSshKey k : db.accountSshKeys().byAccount(
            user.getAccountId())) {
          if (k.isValid()) {
            add(db, kl, k);
          }
        }
        if (kl.isEmpty()) {
          return new SshKeyCacheEntryCollection(
              SshKeyCacheEntryCollection.Type.NO_KEYS);
        }
        return new SshKeyCacheEntryCollection(Collections.unmodifiableList(kl));
      } finally {
        db.close();
      }
    }

    @Override
    public SshKeyCacheEntryCollection missing(Account.Username username) {
      return new SshKeyCacheEntryCollection(
          SshKeyCacheEntryCollection.Type.INVALID_USER);
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
