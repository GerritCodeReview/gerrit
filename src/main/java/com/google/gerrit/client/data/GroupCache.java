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
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.reviewdb.TrustedExternalId;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cache of group information, including account memberships. */
public class GroupCache {
  private AccountGroup.Id adminGroupId;
  private AccountGroup.Id anonymousGroupId;
  private AccountGroup.Id registeredGroupId;
  private Set<AccountGroup.Id> anonOnly;
  private List<TrustedExternalId> trustedIds;

  private final LinkedHashMap<Account.Id, Set<AccountGroup.Id>> byAccount =
      new LinkedHashMap<Account.Id, Set<AccountGroup.Id>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            final Map.Entry<Account.Id, Set<AccountGroup.Id>> eldest) {
          return 4096 <= size();
        }
      };

  public GroupCache(final SystemConfig cfg) {
    adminGroupId = cfg.adminGroupId;
    anonymousGroupId = cfg.anonymousGroupId;
    registeredGroupId = cfg.registeredGroupId;

    anonOnly =
        Collections.unmodifiableSet(Collections.singleton(anonymousGroupId));
  }

  /**
   * Is this group membership managed automatically by Gerrit?
   * 
   * @param groupId the group to test.
   * @return true if Gerrit handles this group membership automatically; false
   *         if it can be manually managed.
   */
  public boolean isAutoGroup(final AccountGroup.Id groupId) {
    return isAnonymousUsers(groupId) || isRegisteredUsers(groupId);
  }

  /**
   * Does this group designate the magical 'Anonymous Users' group?
   * 
   * @param groupId the group to test.
   * @return true if this is the magical 'Anonymous' group; false otherwise.
   */
  public boolean isAnonymousUsers(final AccountGroup.Id groupId) {
    return anonymousGroupId.equals(groupId);
  }

  /**
   * Does this group designate the magical 'Registered Users' group?
   * 
   * @param groupId the group to test.
   * @return true if this is the magical 'Registered' group; false otherwise.
   */
  public boolean isRegisteredUsers(final AccountGroup.Id groupId) {
    return registeredGroupId.equals(groupId);
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
    if (isAnonymousUsers(groupId) || isRegisteredUsers(groupId)) {
      return true;
    }
    return getEffectiveGroups(accountId).contains(groupId);
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
    if (isAutoGroup(m.getAccountGroupId())) {
      return;
    }
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
    if (isAutoGroup(m.getAccountGroupId())) {
      return;
    }
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
   * <p>
   * A user is only a member of groups beyond {@link #anonymousGroupId} and
   * {@link #registeredGroupId} if their account is using only
   * {@link TrustedExternalId}s.
   * 
   * @param accountId the account to obtain the group list for.
   * @return unmodifiable set listing the groups the account is a member of.
   */
  public Set<AccountGroup.Id> getEffectiveGroups(final Account.Id accountId) {
    if (accountId == null) {
      return anonOnly;
    }

    Set<AccountGroup.Id> m;
    synchronized (byAccount) {
      m = byAccount.get(accountId);
    }
    if (m != null) {
      return m;
    }

    m = new HashSet<AccountGroup.Id>();
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        if (isIdentityTrustable(db, accountId)) {
          for (final AccountGroupMember g : db.accountGroupMembers().byAccount(
              accountId)) {
            m.add(g.getAccountGroupId());
          }
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      m.clear();
    }
    m.add(anonymousGroupId);
    m.add(registeredGroupId);
    synchronized (byAccount) {
      byAccount.put(accountId, Collections.unmodifiableSet(m));
    }
    return m;
  }

  private boolean isIdentityTrustable(final ReviewDb db,
      final Account.Id accountId) throws OrmException {
    switch (Common.getGerritConfig().getLoginType()) {
      case HTTP:
        // Its safe to assume yes for an HTTP authentication type, as the
        // only way in is through some external system that the admin trusts
        //
        return true;

      case OPENID:
      default:
        // Validate against the trusted provider list
        //
        return TrustedExternalId.isIdentityTrustable(getTrustedIds(db), db
            .accountExternalIds().byAccount(accountId));
    }
  }

  private synchronized List<TrustedExternalId> getTrustedIds(final ReviewDb db)
      throws OrmException {
    if (trustedIds == null) {
      trustedIds =
          Collections.unmodifiableList(db.trustedExternalIds().all().toList());
    }
    return trustedIds;
  }

  /** Force the entire group cache to flush from memory and recompute. */
  public void flush() {
    synchronized (byAccount) {
      byAccount.clear();
    }
    synchronized (this) {
      trustedIds = null;
    }
  }
}
