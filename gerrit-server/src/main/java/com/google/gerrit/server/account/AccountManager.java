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

import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.errors.InvalidUserNameException;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks authentication related details for user accounts. */
@Singleton
public class AccountManager {
  private static final Logger log =
      LoggerFactory.getLogger(AccountManager.class);

  private final SchemaFactory<ReviewDb> schema;
  private final AccountCache byIdCache;
  private final AccountByEmailCache byEmailCache;
  private final Realm realm;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeUserName.Factory changeUserNameFactory;
  private final ProjectCache projectCache;
  private final AtomicBoolean awaitsFirstAccountCheck;
  private final AuditService auditService;

  @Inject
  AccountManager(final SchemaFactory<ReviewDb> schema,
      final AccountCache byIdCache, final AccountByEmailCache byEmailCache,
      final Realm accountMapper,
      final IdentifiedUser.GenericFactory userFactory,
      final ChangeUserName.Factory changeUserNameFactory,
      final ProjectCache projectCache,
      final AuditService auditService) throws OrmException {
    this.schema = schema;
    this.byIdCache = byIdCache;
    this.byEmailCache = byEmailCache;
    this.realm = accountMapper;
    this.userFactory = userFactory;
    this.changeUserNameFactory = changeUserNameFactory;
    this.projectCache = projectCache;
    this.awaitsFirstAccountCheck = new AtomicBoolean(true);
    this.auditService = auditService;
  }

  /**
   * @return user identified by this external identity string, or null.
   */
  public Account.Id lookup(final String externalId) throws AccountException {
    try {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId ext =
            db.accountExternalIds().get(new AccountExternalId.Key(externalId));
        return ext != null ? ext.getAccountId() : null;
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new AccountException("Cannot lookup account " + externalId, e);
    }
  }

  /**
   * Authenticate the user, potentially creating a new account if they are new.
   *
   * @param who identity of the user, with any details we received about them.
   * @return the result of authenticating the user.
   * @throws AccountException the account does not exist, and cannot be created,
   *         or exists, but cannot be located, or is inactive.
   */
  public AuthResult authenticate(AuthRequest who) throws AccountException {
    who = realm.authenticate(who);
    try {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId.Key key = id(who);
        final AccountExternalId id = db.accountExternalIds().get(key);
        if (id == null) {
          // New account, automatically create and return.
          //
          return create(db, who);

        } else { // Account exists

          Account act = db.accounts().get(id.getAccountId());
          if (act == null || !act.isActive()) {
            throw new AccountException("Authentication error, account inactive");
          }

          // return the identity to the caller.
          update(db, who, id);
          return new AuthResult(id.getAccountId(), key, false);
        }

      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new AccountException("Authentication error", e);
    }
  }

  private void update(final ReviewDb db, final AuthRequest who,
      final AccountExternalId extId) throws OrmException {
    final IdentifiedUser user = userFactory.create(extId.getAccountId());
    Account toUpdate = null;

    // If the email address was modified by the authentication provider,
    // update our records to match the changed email.
    //
    final String newEmail = who.getEmailAddress();
    final String oldEmail = extId.getEmailAddress();
    if (newEmail != null && !newEmail.equals(oldEmail)) {
      if (oldEmail != null
          && oldEmail.equals(user.getAccount().getPreferredEmail())) {
        toUpdate = load(toUpdate, user.getAccountId(), db);
        toUpdate.setPreferredEmail(newEmail);
      }

      extId.setEmailAddress(newEmail);
      db.accountExternalIds().update(Collections.singleton(extId));
    }

    if (!realm.allowsEdit(Account.FieldName.FULL_NAME)
        && !eq(user.getAccount().getFullName(), who.getDisplayName())) {
      toUpdate = load(toUpdate, user.getAccountId(), db);
      toUpdate.setFullName(who.getDisplayName());
    }

    if (!realm.allowsEdit(Account.FieldName.USER_NAME)
        && !eq(user.getUserName(), who.getUserName())) {
      changeUserNameFactory.create(db, user, who.getUserName());
    }

    if (toUpdate != null) {
      db.accounts().update(Collections.singleton(toUpdate));
    }

    if (newEmail != null && !newEmail.equals(oldEmail)) {
      byEmailCache.evict(oldEmail);
      byEmailCache.evict(newEmail);
    }
    if (toUpdate != null) {
      byIdCache.evict(toUpdate.getId());
    }
  }

  private Account load(Account toUpdate, Account.Id accountId, ReviewDb db)
      throws OrmException {
    if (toUpdate == null) {
      toUpdate = db.accounts().get(accountId);
      if (toUpdate == null) {
        throw new OrmException("Account " + accountId + " has been deleted");
      }
    }
    return toUpdate;
  }

  private static boolean eq(final String a, final String b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  private AuthResult create(final ReviewDb db, final AuthRequest who)
      throws OrmException, AccountException {
    final Account.Id newId = new Account.Id(db.nextAccountId());
    final Account account = new Account(newId, TimeUtil.nowTs());
    final AccountExternalId extId = createId(newId, who);

    extId.setEmailAddress(who.getEmailAddress());
    account.setFullName(who.getDisplayName());
    account.setPreferredEmail(extId.getEmailAddress());

    final boolean isFirstAccount = awaitsFirstAccountCheck.getAndSet(false)
      && db.accounts().anyAccounts().toList().isEmpty();

    try {
      db.accounts().upsert(Collections.singleton(account));
      db.accountExternalIds().upsert(Collections.singleton(extId));
    } finally {
      // If adding the account failed, it may be that it actually was the
      // first account. So we reset the 'check for first account'-guard, as
      // otherwise the first account would not get administration permissions.
      awaitsFirstAccountCheck.set(isFirstAccount);
    }

    if (isFirstAccount) {
      // This is the first user account on our site. Assume this user
      // is going to be the site's administrator and just make them that
      // to bootstrap the authentication database.
      //
      Permission admin = projectCache.getAllProjects()
          .getConfig()
          .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
          .getPermission(GlobalCapability.ADMINISTRATE_SERVER);

      final AccountGroup.UUID uuid = admin.getRules().get(0).getGroup().getUUID();
      final AccountGroup g = db.accountGroups().byUUID(uuid).iterator().next();
      final AccountGroup.Id adminId = g.getId();
      final AccountGroupMember m =
          new AccountGroupMember(new AccountGroupMember.Key(newId, adminId));
      auditService.dispatchAddAccountsToGroup(newId, Collections.singleton(m));
      db.accountGroupMembers().insert(Collections.singleton(m));
    }

    if (who.getUserName() != null) {
      // Only set if the name hasn't been used yet, but was given to us.
      //
      IdentifiedUser user = userFactory.create(newId);
      try {
        changeUserNameFactory.create(db, user, who.getUserName()).call();
      } catch (NameAlreadyUsedException e) {
        final String message =
            "Cannot assign user name \"" + who.getUserName() + "\" to account "
                + newId + "; name already in use.";
        handleSettingUserNameFailure(db, account, extId, message, e, false);
      } catch (InvalidUserNameException e) {
        final String message =
            "Cannot assign user name \"" + who.getUserName() + "\" to account "
                + newId + "; name does not conform.";
        handleSettingUserNameFailure(db, account, extId, message, e, false);
      } catch (OrmException e) {
        final String message = "Cannot assign user name";
        handleSettingUserNameFailure(db, account, extId, message, e, true);
      }
    }

    byEmailCache.evict(account.getPreferredEmail());
    realm.onCreateAccount(who, account);
    return new AuthResult(newId, extId.getKey(), true);
  }

  /**
   * This method handles an exception that occurred during the setting of the
   * user name for a newly created account. If the realm does not allow the user
   * to set a user name manually this method deletes the newly created account
   * and throws an {@link AccountUserNameException}. In any case the error
   * message is logged.
   *
   * @param db the database
   * @param account the newly created account
   * @param extId the newly created external id
   * @param errorMessage the error message
   * @param e the exception that occurred during the setting of the user name
   *        for the new account
   * @param logException flag that decides whether the exception should be
   *        included into the log
   * @throws AccountUserNameException thrown if the realm does not allow the
   *         user to manually set the user name
   * @throws OrmException thrown if cleaning the database failed
   */
  private void handleSettingUserNameFailure(final ReviewDb db,
      final Account account, final AccountExternalId extId,
      final String errorMessage, final Exception e, final boolean logException)
      throws AccountUserNameException, OrmException {
    if (logException) {
      log.error(errorMessage, e);
    } else {
      log.error(errorMessage);
    }
    if (!realm.allowsEdit(Account.FieldName.USER_NAME)) {
      // setting the given user name has failed, but the realm does not
      // allow the user to manually set a user name,
      // this means we would end with an account without user name
      // (without 'username:<USERNAME>' entry in
      // account_external_ids table),
      // such an account cannot be used for uploading changes,
      // this is why the best we can do here is to fail early and cleanup
      // the database
      db.accounts().delete(Collections.singleton(account));
      db.accountExternalIds().delete(Collections.singleton(extId));
      throw new AccountUserNameException(errorMessage, e);
    }
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
   * @return the result of linking the identity to the user.
   * @throws AccountException the identity belongs to a different account, or it
   *         cannot be linked at this time.
   */
  public AuthResult link(final Account.Id to, AuthRequest who)
      throws AccountException, OrmException {
    final ReviewDb db = schema.open();
    try {
      who = realm.link(db, to, who);

      final AccountExternalId.Key key = id(who);
      AccountExternalId extId = db.accountExternalIds().get(key);
      if (extId != null) {
        if (!extId.getAccountId().equals(to)) {
          throw new AccountException("Identity in use by another account");
        }
        update(db, who, extId);

      } else {
        extId = createId(to, who);
        extId.setEmailAddress(who.getEmailAddress());
        db.accountExternalIds().insert(Collections.singleton(extId));

        if (who.getEmailAddress() != null) {
          final Account a = db.accounts().get(to);
          if (a.getPreferredEmail() == null) {
            a.setPreferredEmail(who.getEmailAddress());
            db.accounts().update(Collections.singleton(a));
          }
        }

        if (who.getEmailAddress() != null) {
          byEmailCache.evict(who.getEmailAddress());
          byIdCache.evict(to);
        }
      }

      return new AuthResult(to, key, false);

    } finally {
      db.close();
    }
  }

  /**
   * Unlink an authentication identity from an existing account.
   *
   * @param from account to unlink the identity from.
   * @param who the identity to delete
   * @return the result of unlinking the identity from the user.
   * @throws AccountException the identity belongs to a different account, or it
   *         cannot be unlinked at this time.
   */
  public AuthResult unlink(final Account.Id from, AuthRequest who)
      throws AccountException, OrmException {
    final ReviewDb db = schema.open();
    try {
      who = realm.unlink(db, from, who);

      final AccountExternalId.Key key = id(who);
      AccountExternalId extId = db.accountExternalIds().get(key);
      if (extId != null) {
        if (!extId.getAccountId().equals(from)) {
          throw new AccountException("Identity in use by another account");
        }
        db.accountExternalIds().delete(Collections.singleton(extId));

        if (who.getEmailAddress() != null) {
          final Account a = db.accounts().get(from);
          if (a.getPreferredEmail() != null
              && a.getPreferredEmail().equals(who.getEmailAddress())) {
            a.setPreferredEmail(null);
            db.accounts().update(Collections.singleton(a));
          }
          byEmailCache.evict(who.getEmailAddress());
          byIdCache.evict(from);
        }

      } else {
        throw new AccountException("Identity not found");
      }

      return new AuthResult(from, key, false);

    } finally {
      db.close();
    }
  }


  private static AccountExternalId.Key id(final AuthRequest who) {
    return new AccountExternalId.Key(who.getExternalId());
  }
}
