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

import com.google.gerrit.client.auth.openid.OpenIdUtil;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tracks authentication related details for user accounts. */
@Singleton
public class AccountManager {
  private final SchemaFactory<ReviewDb> schema;
  private final AccountCache byIdCache;
  private final AccountByEmailCache byEmailCache;
  private final AuthConfig authConfig;
  private final Realm realm;

  @Inject
  AccountManager(final SchemaFactory<ReviewDb> schema,
      final AccountCache byIdCache, final AccountByEmailCache byEmailCache,
      final AuthConfig authConfig, final Realm accountMapper) {
    this.schema = schema;
    this.byIdCache = byIdCache;
    this.byEmailCache = byEmailCache;
    this.authConfig = authConfig;
    this.realm = accountMapper;
  }

  /**
   * Authenticate the user, potentially creating a new account if they are new.
   *
   * @param who identity of the user, with any details we received about them.
   * @return the result of authenticating the user.
   * @throws AccountException the account does not exist, and cannot be created,
   *         or exists, but cannot be located.
   */
  public AuthResult authenticate(AuthRequest who) throws AccountException {
    who = realm.authenticate(who);
    try {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId id =
            db.accountExternalIds().get(
                new AccountExternalId.Key(who.getExternalId()));
        if (id == null) {
          // New account, automatically create and return.
          //
          return create(db, who);

        } else {
          // Account exists, return the identity to the caller.
          //
          update(db, who, id);
          return new AuthResult(id.getAccountId(), false);
        }

      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new AccountException("Authentication error", e);
    }
  }

  private void update(final ReviewDb db, final AuthRequest who,
      final AccountExternalId extId) throws OrmException, AccountException {
    final Transaction txn = db.beginTransaction();
    final Account account = db.accounts().get(extId.getAccountId());
    boolean updateAccount = false;
    if (account == null) {
      throw new AccountException("Account has been deleted");
    }

    // If the email address was modified by the authentication provider,
    // update our records to match the changed email.
    //
    final String newEmail = who.getEmailAddress();
    final String oldEmail = extId.getEmailAddress();
    if (newEmail != null && !newEmail.equals(oldEmail)) {
      if (oldEmail != null && oldEmail.equals(account.getPreferredEmail())) {
        updateAccount = true;
        account.setPreferredEmail(newEmail);
      }

      extId.setEmailAddress(newEmail);
    }

    if (!realm.allowsEdit(Account.FieldName.FULL_NAME)
        && !eq(account.getFullName(), who.getDisplayName())) {
      updateAccount = true;
      account.setFullName(who.getDisplayName());
    }
    if (!realm.allowsEdit(Account.FieldName.SSH_USER_NAME)
        && !eq(account.getSshUserName(), who.getSshUserName())) {
      updateAccount = true;
      account.setSshUserName(who.getSshUserName());
    }

    extId.setLastUsedOn();
    db.accountExternalIds().update(Collections.singleton(extId), txn);
    if (updateAccount) {
      db.accounts().update(Collections.singleton(account), txn);
    }
    txn.commit();

    if (newEmail != null && !newEmail.equals(oldEmail)) {
      byEmailCache.evict(oldEmail);
      byEmailCache.evict(newEmail);
    }
    if (updateAccount) {
      byIdCache.evict(account.getId());
    }
  }

  private static boolean eq(final String a, final String b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  private AuthResult create(final ReviewDb db, final AuthRequest who)
      throws OrmException, AccountException {
    if (authConfig.isAllowGoogleAccountUpgrade()
        && who.isScheme(OpenIdUtil.URL_GOOGLE + "?")
        && who.getEmailAddress() != null) {
      final List<AccountExternalId> openId = new ArrayList<AccountExternalId>();
      final List<AccountExternalId> v1 = new ArrayList<AccountExternalId>();

      for (final AccountExternalId extId : db.accountExternalIds()
          .byEmailAddress(who.getEmailAddress())) {
        if (extId.isScheme(OpenIdUtil.URL_GOOGLE + "?")) {
          openId.add(extId);
        } else if (extId.isScheme(AccountExternalId.LEGACY_GAE)) {
          v1.add(extId);
        }
      }

      if (!openId.isEmpty()) {
        // The user has already registered with an OpenID from Google, but
        // Google may have changed the user's OpenID identity if this server
        // name has changed. Insert a new identity for the user.
        //
        final Account.Id accountId = openId.get(0).getAccountId();

        if (openId.size() > 1) {
          // Validate all matching identities are actually the same user.
          //
          for (final AccountExternalId extId : openId) {
            if (!accountId.equals(extId.getAccountId())) {
              throw new AccountException("Multiple user accounts for "
                  + who.getEmailAddress() + " using Google Accounts provider");
            }
          }
        }

        final AccountExternalId newId = createId(accountId, who);
        newId.setEmailAddress(who.getEmailAddress());
        newId.setLastUsedOn();

        if (openId.size() == 1) {
          final AccountExternalId oldId = openId.get(0);
          final Transaction txn = db.beginTransaction();
          db.accountExternalIds().delete(Collections.singleton(oldId), txn);
          db.accountExternalIds().insert(Collections.singleton(newId), txn);
          txn.commit();
        } else {
          db.accountExternalIds().insert(Collections.singleton(newId));
        }
        return new AuthResult(accountId, false);

      } else if (v1.size() == 1) {
        // Exactly one user was imported from Gerrit 1.x with this email
        // address. Upgrade their account by deleting the legacy import
        // identity and creating a new identity matching the token we have.
        //
        final AccountExternalId oldId = v1.get(0);
        final AccountExternalId newId = createId(oldId.getAccountId(), who);
        newId.setEmailAddress(who.getEmailAddress());
        newId.setLastUsedOn();
        final Transaction txn = db.beginTransaction();
        db.accountExternalIds().delete(Collections.singleton(oldId), txn);
        db.accountExternalIds().insert(Collections.singleton(newId), txn);
        txn.commit();
        return new AuthResult(newId.getAccountId(), false);

      } else if (v1.size() > 1) {
        throw new AccountException("Multiple Gerrit 1.x accounts found");
      }
    }

    final Account.Id newId = new Account.Id(db.nextAccountId());
    final Account account = new Account(newId);
    final AccountExternalId extId = createId(newId, who);

    extId.setLastUsedOn();
    extId.setEmailAddress(who.getEmailAddress());
    account.setFullName(who.getDisplayName());
    account.setPreferredEmail(extId.getEmailAddress());

    if (who.getSshUserName() != null
        && db.accounts().bySshUserName(who.getSshUserName()) == null) {
      // Only set if the name hasn't been used yet, but was given to us.
      //
      account.setSshUserName(who.getSshUserName());
    }

    final Transaction txn = db.beginTransaction();
    db.accounts().insert(Collections.singleton(account), txn);
    db.accountExternalIds().insert(Collections.singleton(extId), txn);
    txn.commit();

    byEmailCache.evict(account.getPreferredEmail());
    realm.onCreateAccount(who, account);
    return new AuthResult(newId, true);
  }

  private static AccountExternalId createId(final Account.Id newId,
      final AuthRequest who) {
    final String ext = who.getExternalId();
    return new AccountExternalId(newId, new AccountExternalId.Key(ext));
  }

  /**
   * Link another authentication identity to an existing account.
   *
   * @param to account to link the identity onto.
   * @param who the additional identity.
   * @throws AccountException the identity belongs to a different account, or it
   *         cannot be linked at this time.
   */
  public void link(final Account.Id to, final AuthRequest who)
      throws AccountException {
    try {
      final ReviewDb db = schema.open();
      try {
        AccountExternalId extId =
            db.accountExternalIds().get(
                new AccountExternalId.Key(who.getExternalId()));
        if (extId != null) {
          if (!extId.getAccountId().equals(to)) {
            throw new AccountException("Identity in use by another account");
          }
          update(db, who, extId);
        } else {
          extId = createId(to, who);
          extId.setEmailAddress(who.getEmailAddress());
          extId.setLastUsedOn();
          db.accountExternalIds().insert(Collections.singleton(extId));
          if (who.getEmailAddress() != null) {
            byEmailCache.evict(who.getEmailAddress());
            byIdCache.evict(to);
          }
        }

      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new AccountException("Cannot link identity", e);
    }
  }
}
