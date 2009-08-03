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
import com.google.gerrit.client.reviewdb.ReviewDb;
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
import java.util.Set;

/** Translates an email address to a set of matching accounts. */
@Singleton
public class AccountByEmailCache {
  private static final Logger log =
      LoggerFactory.getLogger(AccountByEmailCache.class);

  private final SchemaFactory<ReviewDb> schema;
  private final SelfPopulatingCache self;

  @Inject
  AccountByEmailCache(final SchemaFactory<ReviewDb> sf, final CacheManager mgr) {
    schema = sf;

    final Cache dc = mgr.getCache("accounts_byemail");
    self = new SelfPopulatingCache(dc, new CacheEntryFactory() {
      @Override
      public Object createEntry(final Object key) throws Exception {
        return lookup((String) key);
      }
    });
    mgr.replaceCacheWithDecoratedCache(dc, self);
  }

  private Set<Account.Id> lookup(final String email) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final HashSet<Account.Id> r = new HashSet<Account.Id>();
      for (Account a : db.accounts().byPreferredEmail(email)) {
        r.add(a.getId());
      }
      for (AccountExternalId a : db.accountExternalIds().byEmailAddress(email)) {
        r.add(a.getAccountId());
      }
      return pack(r);
    } finally {
      db.close();
    }
  }

  @SuppressWarnings("unchecked")
  public Set<Account.Id> get(final String email) {
    if (email == null || email.isEmpty()) {
      return Collections.emptySet();
    }

    final Element m;
    try {
      m = self.get(email);
    } catch (IllegalStateException e) {
      log.error("Cannot lookup email " + email, e);
      return Collections.emptySet();
    } catch (CacheException e) {
      log.error("Cannot lookup email " + email, e);
      return Collections.emptySet();
    }

    if (m == null || m.getObjectValue() == null) {
      return Collections.emptySet();
    }
    return (Set<Account.Id>) m.getObjectValue();
  }

  public void evict(final String email) {
    if (email != null && !email.isEmpty()) {
      self.remove(email);
    }
  }

  private static Set<Account.Id> pack(final Set<Account.Id> c) {
    switch (c.size()) {
      case 0:
        return Collections.emptySet();
      case 1:
        return one(c);
      default:
        return Collections.unmodifiableSet(new HashSet<Account.Id>(c));
    }
  }

  private static <T> Set<T> one(final Set<T> c) {
    return Collections.singleton(c.iterator().next());
  }
}
