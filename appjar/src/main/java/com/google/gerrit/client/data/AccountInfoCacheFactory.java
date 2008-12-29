// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Efficiently builds a {@link AccountInfoCache}. */
public class AccountInfoCacheFactory {
  private final ReviewDb db;
  private final HashMap<Account.Id, Account> cache;
  private final HashSet<Account.Id> toFetch;

  public AccountInfoCacheFactory(final ReviewDb schema) {
    db = schema;
    cache = new HashMap<Account.Id, Account>();
    toFetch = new HashSet<Account.Id>();
  }

  /**
   * Indicate an account will be needed later on.
   * <p>
   * This method permits batch fetching from the data store by building a list
   * of Account.Ids which need to be obtained during the next {@link #fetch}.
   * 
   * @param id identity that will be needed in the future; may be null.
   */
  public void want(final Account.Id id) {
    if (id != null && !cache.containsKey(id)) {
      toFetch.add(id);
    }
  }

  /** Indicate one or more accounts will be needed later on. */
  public void want(final Collection<Account.Id> ids) {
    for (final Account.Id id : ids) {
      want(id);
    }
  }

  /** Fetch all accounts previously queued by {@link #want(Account.Id)} */
  public void fetch() throws OrmException {
    if (!toFetch.isEmpty()) {
      for (final Account a : db.accounts().get(toFetch)) {
        cache.put(a.getId(), a);
      }
      toFetch.clear();
    }
  }

  /** Load one account entity, reusing a cached instance if already loaded. */
  public Account get(final Account.Id id) throws OrmException {
    if (id == null) {
      return null;
    }

    Account a = cache.get(id);
    if (a == null) {
      if (toFetch.isEmpty()) {
        a = db.accounts().get(id);
        if (a != null) {
          cache.put(id, a);
        }
      } else {
        toFetch.add(id);
        fetch();
        a = cache.get(id);
      }
    }
    return a;
  }

  /**
   * Create an AccountInfoCache with the currently loaded Account entities.
   * <p>
   * Implicitly invokes {@link #fetch()} prior to creating the cache, ensuring
   * any previously enqueued entities will be included in the result.
   * */
  public AccountInfoCache create() throws OrmException {
    fetch();
    final List<AccountInfo> r = new ArrayList<AccountInfo>(cache.size());
    for (final Account a : cache.values()) {
      r.add(new AccountInfo(a));
    }
    return new AccountInfoCache(r);
  }
}
