// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cache of account information. */
@SuppressWarnings("serial")
public class AccountCache {
  private final LinkedHashMap<Account.Id, Account> byId =
      new LinkedHashMap<Account.Id, Account>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            final Map.Entry<Account.Id, Account> eldest) {
          return 4096 <= size();
        }
      };

  /**
   * Invalidate all cached information about a single user account.
   * 
   * @param accountId the account to invalidate from the cache.
   */
  public void invalidate(final Account.Id accountId) {
    synchronized (byId) {
      byId.remove(accountId);
    }
  }

  /**
   * Get a single account.
   * 
   * @param accountId the account to obtain.
   * @return the cached account entity; null if the account is not in the
   *         database anymore.
   */
  public Account get(final Account.Id accountId) {
    return get(accountId, null);
  }

  /**
   * Get a single account.
   * 
   * @param accountId the account to obtain.
   * @param qd optional connection to reuse (if not null) when doing a lookup.
   * @return the cached account entity; null if the account is not in the
   *         database anymore.
   */
  public Account get(final Account.Id accountId, final ReviewDb qd) {
    if (accountId == null) {
      return null;
    }

    Account m;
    synchronized (byId) {
      m = byId.get(accountId);
    }
    if (m != null) {
      return m;
    }

    try {
      final ReviewDb db = qd != null ? qd : Common.getSchemaFactory().open();
      try {
        m = db.accounts().get(accountId);
      } finally {
        if (qd == null) {
          db.close();
        }
      }
    } catch (OrmException e) {
      m = null;
    }
    if (m != null) {
      synchronized (byId) {
        byId.put(m.getId(), m);
      }
    }
    return m;
  }

  /**
   * Lookup multiple account records.
   * 
   * @param fetch set of all accounts to obtain.
   * @param qd optional query handle to use if the account data is not in cache.
   * @return records which match; if an account listed in <code>fetch</code> is
   *         not found it will not be returned.
   */
  public Collection<Account> get(final Set<Account.Id> fetch, final ReviewDb qd) {
    final Set<Account.Id> toget = new HashSet<Account.Id>(fetch);
    final Collection<Account> r = new ArrayList<Account>(toget.size());

    synchronized (byId) {
      for (final Iterator<Account.Id> i = toget.iterator(); i.hasNext();) {
        final Account m = byId.get(i.next());
        if (m != null) {
          r.add(m);
          i.remove();
        }
      }
    }

    if (!toget.isEmpty()) {
      List<Account> found;
      try {
        final ReviewDb db = qd != null ? qd : Common.getSchemaFactory().open();
        try {
          found = qd.accounts().get(toget).toList();
        } finally {
          if (qd == null) {
            db.close();
          }
        }
      } catch (OrmException e) {
        found = Collections.emptyList();
      }
      if (!found.isEmpty()) {
        synchronized (byId) {
          for (final Account a : found) {
            byId.put(a.getId(), a);
          }
        }
        r.addAll(found);
      }
    }
    return r;
  }

  /** Force the entire cache to flush from memory and recompute. */
  public void flush() {
    synchronized (byId) {
      byId.clear();
    }
  }
}
