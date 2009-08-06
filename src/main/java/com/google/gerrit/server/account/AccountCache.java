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

package com.google.gerrit.server.account;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Caches important (but small) account state to avoid database hits. */
@Singleton
public class AccountCache {
  private static final Logger log =
      LoggerFactory.getLogger(AccountCache.class);

  private final SchemaFactory<ReviewDb> schema;
  private final AuthConfig authConfig;
  private final SelfPopulatingCache self;

  private final Set<AccountGroup.Id> registered;
  private final Set<AccountGroup.Id> anonymous;

  @Inject
  AccountCache(final SchemaFactory<ReviewDb> sf, final SystemConfig cfg,
      final AuthConfig ac, final CacheManager mgr) {
    schema = sf;
    authConfig = ac;

    final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>(2);
    r.add(cfg.anonymousGroupId);
    r.add(cfg.registeredGroupId);
    registered = Collections.unmodifiableSet(r);
    anonymous = Collections.singleton(cfg.anonymousGroupId);

    final Cache dc = mgr.getCache("accounts");
    self = new SelfPopulatingCache(dc, new CacheEntryFactory() {
      @Override
      public Object createEntry(final Object key) throws Exception {
        return lookup((Account.Id) key);
      }
    });
    mgr.replaceCacheWithDecoratedCache(dc, self);
  }

  private AccountState lookup(final Account.Id who) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final Account account = db.accounts().get(who);
      if (account == null) {
        // Account no longer exists? They are anonymous.
        //
        return missing(who);
      }

      final List<AccountExternalId> ids =
          db.accountExternalIds().byAccount(who).toList();
      Set<String> emails = new HashSet<String>();
      for (AccountExternalId id : ids) {
        if (id.getEmailAddress() != null && !id.getEmailAddress().isEmpty()) {
          emails.add(id.getEmailAddress());
        }
      }

      Set<AccountGroup.Id> actual = new HashSet<AccountGroup.Id>();
      for (AccountGroupMember g : db.accountGroupMembers().byAccount(who)) {
        actual.add(g.getAccountGroupId());
      }

      if (actual.isEmpty()) {
        actual = registered;
      } else {
        actual.addAll(registered);
        actual = Collections.unmodifiableSet(actual);
      }

      final Set<AccountGroup.Id> effective;
      if (authConfig.isIdentityTrustable(ids)) {
        effective = actual;
      } else {
        effective = registered;
      }

      return new AccountState(account, actual, effective, emails);
    } finally {
      db.close();
    }
  }

  @SuppressWarnings("unchecked")
  public AccountState get(final Account.Id accountId) {
    if (accountId == null) {
      return null;
    }

    final Element m;
    try {
      m = self.get(accountId);
    } catch (IllegalStateException e) {
      log.error("Cannot lookup account for " + accountId, e);
      return missing(accountId);
    } catch (CacheException e) {
      log.error("Cannot lookup account for " + accountId, e);
      return missing(accountId);
    }

    if (m == null || m.getObjectValue() == null) {
      return missing(accountId);
    }
    return (AccountState) m.getObjectValue();
  }

  private AccountState missing(final Account.Id accountId) {
    final Account account = new Account(accountId);
    final Set<String> emails = Collections.emptySet();
    return new AccountState(account, anonymous, anonymous, emails);
  }

  public void evict(final Account.Id accountId) {
    if (accountId != null) {
      self.remove(accountId);
    }
  }
}
