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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Cache of group information, including account memberships. */
public class GroupCache {
  private final SchemaFactory<ReviewDb> schema;
  private AccountGroup.Id adminGroupId;

  private final LinkedHashMap<Account.Id, Set<AccountGroup.Id>> byAccount =
      new LinkedHashMap<Account.Id, Set<AccountGroup.Id>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            final Map.Entry<Account.Id, Set<AccountGroup.Id>> eldest) {
          return 4096 <= size();
        }
      };

  protected GroupCache(final SchemaFactory<ReviewDb> rdf, final SystemConfig cfg) {
    schema = rdf;
    adminGroupId = cfg.adminGroupId;
  }

  /**
   * Determine if the user is a member of the blessed administrator group.
   * 
   * @param accountId the account to test for membership.
   * @return true if the account is in the special blessed administration group;
   *         false otherwise.
   */
  public boolean isAdministrator(final Account.Id accountId) {
    return isInGroup(accountId, adminGroupId);
  }

  /**
   * Determine if the user is a member of a specific group.
   * 
   * @param accountId the account to test for membership.
   * @param groupId the group to test for membership within.
   * @return true if the account is in the group; false otherwise.
   */
  public boolean isInGroup(final Account.Id accountId,
      final AccountGroup.Id groupId) {
    final Set<AccountGroup.Id> m = getGroups(accountId);
    return m.contains(groupId);
  }

  /**
   * Invalidate all cached information about a single user account.
   * 
   * @param accountId the account to invalidate from the cache.
   */
  public void invalidate(final Account.Id accountId) {
    synchronized (byAccount) {
      byAccount.remove(accountId);
    }
  }

  /**
   * Notify the cache that an account has become a member of a group.
   * 
   * @param m the account-group pairing that was just inserted.
   */
  public void notifyGroupAdd(final AccountGroupMember m) {
    synchronized (byAccount) {
      final Set<AccountGroup.Id> e = byAccount.get(m.getAccountId());
      if (e != null) {
        final Set<AccountGroup.Id> n = new HashSet<AccountGroup.Id>(e);
        n.add(m.getAccountGroupId());
        byAccount.put(m.getAccountId(), Collections.unmodifiableSet(n));
      }
    }
  }

  /**
   * Notify the cache that an account has been removed from a group.
   * 
   * @param m the account-group pairing that was just deleted.
   */
  public void notifyGroupDelete(final AccountGroupMember m) {
    synchronized (byAccount) {
      final Set<AccountGroup.Id> e = byAccount.get(m.getAccountId());
      if (e != null) {
        final Set<AccountGroup.Id> n = new HashSet<AccountGroup.Id>(e);
        n.remove(m.getAccountGroupId());
        byAccount.put(m.getAccountId(), Collections.unmodifiableSet(n));
      }
    }
  }

  /**
   * Get the groups a specific account is a member of.
   * 
   * @param accountId the account to obtain the group list for.
   * @return unmodifiable set listing the groups the account is a member of.
   */
  public Set<AccountGroup.Id> getGroups(final Account.Id accountId) {
    Set<AccountGroup.Id> m;
    synchronized (byAccount) {
      m = byAccount.get(accountId);
    }
    if (m != null) {
      return m;
    }

    m = new HashSet<AccountGroup.Id>();
    try {
      final ReviewDb db = schema.open();
      try {
        for (final AccountGroupMember g : db.accountGroupMembers().byAccount(
            accountId)) {
          m.add(g.getAccountGroupId());
        }
        m = Collections.unmodifiableSet(m);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      m = Collections.emptySet();
    }
    synchronized (byAccount) {
      byAccount.put(accountId, m);
    }
    return m;
  }
}
