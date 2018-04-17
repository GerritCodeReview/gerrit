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

import static com.google.gerrit.server.account.ExternalId.SCHEME_USERNAME;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Strings;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks authentication related details for user accounts. */
@Singleton
public class AccountManager {
  private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

  private final SchemaFactory<ReviewDb> schema;
  private final AccountCache byIdCache;
  private final AccountByEmailCache byEmailCache;
  private final Realm realm;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeUserName.Factory changeUserNameFactory;
  private final ProjectCache projectCache;
  private final AtomicBoolean awaitsFirstAccountCheck;
  private final AuditService auditService;
  private final ExternalIdsUpdate.Server externalIdsUpdateFactory;

  @Inject
  AccountManager(
      SchemaFactory<ReviewDb> schema,
      AccountCache byIdCache,
      AccountByEmailCache byEmailCache,
      Realm accountMapper,
      IdentifiedUser.GenericFactory userFactory,
      ChangeUserName.Factory changeUserNameFactory,
      ProjectCache projectCache,
      AuditService auditService,
      ExternalIdsUpdate.Server externalIdsUpdateFactory) {
    this.schema = schema;
    this.byIdCache = byIdCache;
    this.byEmailCache = byEmailCache;
    this.realm = accountMapper;
    this.userFactory = userFactory;
    this.changeUserNameFactory = changeUserNameFactory;
    this.projectCache = projectCache;
    this.awaitsFirstAccountCheck = new AtomicBoolean(true);
    this.auditService = auditService;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
  }

  /** @return user identified by this external identity string */
  public Optional<Account.Id> lookup(String externalId) throws AccountException {
    try (ReviewDb db = schema.open()) {
      ExternalId extId = findExternalId(db, ExternalId.Key.parse(externalId));
      return extId != null ? Optional.of(extId.accountId()) : Optional.empty();
    } catch (OrmException e) {
      throw new AccountException("Cannot lookup account " + externalId, e);
    }
  }

  /**
   * Authenticate the user, potentially creating a new account if they are new.
   *
   * @param who identity of the user, with any details we received about them.
   * @return the result of authenticating the user.
   * @throws AccountException the account does not exist, and cannot be created, or exists, but
   *     cannot be located, or is inactive.
   */
  public AuthResult authenticate(AuthRequest who) throws AccountException, IOException {
    who = realm.authenticate(who);
    try {
      try (ReviewDb db = schema.open()) {
        ExternalId id = findExternalId(db, who.getExternalIdKey());
        if (id == null) {
          if (who.getUserName() != null) {
            ExternalId.Key key = ExternalId.Key.create(SCHEME_USERNAME, who.getUserName());
            ExternalId existingId = findExternalId(db, key);
            if (existingId != null) {
              // An inconsistency is detected in the database, having a record for scheme
              // "username:"
              // but no record for scheme "gerrit:". Try to recover by linking
              // "gerrit:" identity to the existing account.
              log.warn(
                  "User {} already has an account; link new identity to the existing account.",
                  who.getUserName());
              return link(existingId.accountId(), who);
            }
          }
          // New account, automatically create and return.
          //
          log.info("External ID not found. Attempting to create new account.");
          return create(db, who);
        }

        // Account exists
        Account act = byIdCache.get(id.accountId()).getAccount();
        if (!act.isActive()) {
          throw new AccountException("Authentication error, account inactive");
        }

        // return the identity to the caller.
        update(db, who, id);
        return new AuthResult(id.accountId(), who.getExternalIdKey(), false);
      }
    } catch (OrmException | ConfigInvalidException e) {
      throw new AccountException("Authentication error", e);
    }
  }

  private ExternalId findExternalId(ReviewDb db, ExternalId.Key key) throws OrmException {
    return ExternalId.from(db.accountExternalIds().get(key.asAccountExternalIdKey()));
  }

  private void update(ReviewDb db, AuthRequest who, ExternalId extId)
      throws OrmException, IOException {
    IdentifiedUser user = userFactory.create(extId.accountId());
    Account toUpdate = null;

    // If the email address was modified by the authentication provider,
    // update our records to match the changed email.
    //
    String newEmail = who.getEmailAddress();
    String oldEmail = extId.email();
    if (newEmail != null && !newEmail.equals(oldEmail)) {
      if (oldEmail != null && oldEmail.equals(user.getAccount().getPreferredEmail())) {
        toUpdate = load(toUpdate, user.getAccountId(), db);
        toUpdate.setPreferredEmail(newEmail);
      }

      externalIdsUpdateFactory
          .create()
          .replace(
              db,
              extId,
              ExternalId.create(extId.key(), extId.accountId(), newEmail, extId.password()));
    }

    if (!realm.allowsEdit(AccountFieldName.FULL_NAME)
        && !Strings.isNullOrEmpty(who.getDisplayName())
        && !eq(user.getAccount().getFullName(), who.getDisplayName())) {
      toUpdate = load(toUpdate, user.getAccountId(), db);
      toUpdate.setFullName(who.getDisplayName());
    }

    if (!realm.allowsEdit(AccountFieldName.USER_NAME)
        && who.getUserName() != null
        && !eq(user.getUserName(), who.getUserName())) {
      log.warn(
          String.format(
              "Not changing already set username %s to %s", user.getUserName(), who.getUserName()));
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

  private Account load(Account toUpdate, Account.Id accountId, ReviewDb db) throws OrmException {
    if (toUpdate == null) {
      toUpdate = db.accounts().get(accountId);
      if (toUpdate == null) {
        throw new OrmException("Account " + accountId + " has been deleted");
      }
    }
    return toUpdate;
  }

  private static boolean eq(String a, String b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  private AuthResult create(ReviewDb db, AuthRequest who)
      throws OrmException, AccountException, IOException, ConfigInvalidException {
    Account.Id newId = new Account.Id(db.nextAccountId());
    Account account = new Account(newId, TimeUtil.nowTs());

    ExternalId extId =
        ExternalId.createWithEmail(who.getExternalIdKey(), newId, who.getEmailAddress());
    account.setFullName(who.getDisplayName());
    account.setPreferredEmail(extId.email());

    boolean isFirstAccount =
        awaitsFirstAccountCheck.getAndSet(false) && db.accounts().anyAccounts().toList().isEmpty();

    try {
      db.accounts().upsert(Collections.singleton(account));

      ExternalId existingExtId =
          ExternalId.from(db.accountExternalIds().get(extId.key().asAccountExternalIdKey()));
      if (existingExtId != null && !existingExtId.accountId().equals(extId.accountId())) {
        // external ID is assigned to another account, do not overwrite
        db.accounts().delete(Collections.singleton(account));
        throw new AccountException(
            "Cannot assign external ID \""
                + extId.key().get()
                + "\" to account "
                + newId
                + "; external ID already in use.");
      }
      externalIdsUpdateFactory.create().upsert(db, extId);
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
      Permission admin =
          projectCache
              .getAllProjects()
              .getConfig()
              .getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
              .getPermission(GlobalCapability.ADMINISTRATE_SERVER);

      AccountGroup.UUID uuid = admin.getRules().get(0).getGroup().getUUID();
      Iterator<AccountGroup> adminGroupIt = db.accountGroups().byUUID(uuid).iterator();
      if (!adminGroupIt.hasNext()) {
        throw new OrmException(
            "Administrator group's UUID is misaligned in backend and All-Projects repository");
      }
      AccountGroup g = adminGroupIt.next();
      AccountGroup.Id adminId = g.getId();
      AccountGroupMember m = new AccountGroupMember(new AccountGroupMember.Key(newId, adminId));
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
        String message =
            "Cannot assign user name \""
                + who.getUserName()
                + "\" to account "
                + newId
                + "; name already in use.";
        handleSettingUserNameFailure(db, account, extId, message, e, false);
      } catch (InvalidUserNameException e) {
        String message =
            "Cannot assign user name \""
                + who.getUserName()
                + "\" to account "
                + newId
                + "; name does not conform.";
        handleSettingUserNameFailure(db, account, extId, message, e, false);
      } catch (OrmException e) {
        String message = "Cannot assign user name";
        handleSettingUserNameFailure(db, account, extId, message, e, true);
      }
    }

    byEmailCache.evict(account.getPreferredEmail());
    byIdCache.evict(account.getId());
    realm.onCreateAccount(who, account);
    return new AuthResult(newId, extId.key(), true);
  }

  /**
   * This method handles an exception that occurred during the setting of the user name for a newly
   * created account. If the realm does not allow the user to set a user name manually this method
   * deletes the newly created account and throws an {@link AccountUserNameException}. In any case
   * the error message is logged.
   *
   * @param db the database
   * @param account the newly created account
   * @param extId the newly created external id
   * @param errorMessage the error message
   * @param e the exception that occurred during the setting of the user name for the new account
   * @param logException flag that decides whether the exception should be included into the log
   * @throws AccountUserNameException thrown if the realm does not allow the user to manually set
   *     the user name
   * @throws OrmException thrown if cleaning the database failed
   */
  private void handleSettingUserNameFailure(
      ReviewDb db,
      Account account,
      ExternalId extId,
      String errorMessage,
      Exception e,
      boolean logException)
      throws AccountUserNameException, OrmException, IOException {
    if (logException) {
      log.error(errorMessage, e);
    } else {
      log.error(errorMessage);
    }
    if (!realm.allowsEdit(AccountFieldName.USER_NAME)) {
      // setting the given user name has failed, but the realm does not
      // allow the user to manually set a user name,
      // this means we would end with an account without user name
      // (without 'username:<USERNAME>' entry in
      // account_external_ids table),
      // such an account cannot be used for uploading changes,
      // this is why the best we can do here is to fail early and cleanup
      // the database
      db.accounts().delete(Collections.singleton(account));
      externalIdsUpdateFactory.create().delete(db, extId);
      throw new AccountUserNameException(errorMessage, e);
    }
  }

  /**
   * Link another authentication identity to an existing account.
   *
   * @param to account to link the identity onto.
   * @param who the additional identity.
   * @return the result of linking the identity to the user.
   * @throws AccountException the identity belongs to a different account, or it cannot be linked at
   *     this time.
   */
  public AuthResult link(Account.Id to, AuthRequest who)
      throws AccountException, OrmException, IOException {
    try (ReviewDb db = schema.open()) {
      log.info("Link another authentication identity to an existing account");
      ExternalId extId = findExternalId(db, who.getExternalIdKey());
      if (extId != null) {
        if (!extId.accountId().equals(to)) {
          throw new AccountException("Identity in use by another account");
        }
        log.info("Updating existing external ID data");
        update(db, who, extId);
      } else {
        log.info("Linking new external ID to the existing account");
        externalIdsUpdateFactory
            .create()
            .insert(
                db, ExternalId.createWithEmail(who.getExternalIdKey(), to, who.getEmailAddress()));

        if (who.getEmailAddress() != null) {
          Account a = db.accounts().get(to);
          if (a.getPreferredEmail() == null) {
            a.setPreferredEmail(who.getEmailAddress());
            db.accounts().update(Collections.singleton(a));
            byIdCache.evict(to);
          }
          byEmailCache.evict(who.getEmailAddress());
        }
      }

      return new AuthResult(to, who.getExternalIdKey(), false);
    }
  }

  /**
   * Update the link to another unique authentication identity to an existing account.
   *
   * <p>Existing external identities with the same scheme will be removed and replaced with the new
   * one.
   *
   * @param to account to link the identity onto.
   * @param who the additional identity.
   * @return the result of linking the identity to the user.
   * @throws OrmException
   * @throws AccountException the identity belongs to a different account, or it cannot be linked at
   *     this time.
   */
  public AuthResult updateLink(Account.Id to, AuthRequest who)
      throws OrmException, AccountException, IOException {
    try (ReviewDb db = schema.open()) {
      Collection<ExternalId> filteredExtIdsByScheme =
          ExternalId.from(db.accountExternalIds().byAccount(to).toList())
              .stream()
              .filter(e -> e.isScheme(who.getExternalIdKey().scheme()))
              .collect(toSet());

      if (!filteredExtIdsByScheme.isEmpty()
          && (filteredExtIdsByScheme.size() > 1
              || !filteredExtIdsByScheme
                  .stream()
                  .filter(e -> e.key().equals(who.getExternalIdKey()))
                  .findAny()
                  .isPresent())) {
        externalIdsUpdateFactory.create().delete(db, filteredExtIdsByScheme);
      }
      byIdCache.evict(to);
      return link(to, who);
    }
  }

  /**
   * Unlink an authentication identity from an existing account.
   *
   * @param from account to unlink the identity from.
   * @param who the identity to delete
   * @return the result of unlinking the identity from the user.
   * @throws AccountException the identity belongs to a different account, or it cannot be unlinked
   *     at this time.
   */
  public AuthResult unlink(Account.Id from, AuthRequest who)
      throws AccountException, OrmException, IOException {
    try (ReviewDb db = schema.open()) {
      ExternalId extId = findExternalId(db, who.getExternalIdKey());
      if (extId != null) {
        if (!extId.accountId().equals(from)) {
          throw new AccountException(
              "Identity '" + who.getExternalIdKey().get() + "' in use by another account");
        }
        externalIdsUpdateFactory.create().delete(db, extId);

        if (who.getEmailAddress() != null) {
          Account a = db.accounts().get(from);
          if (a.getPreferredEmail() != null
              && a.getPreferredEmail().equals(who.getEmailAddress())) {
            a.setPreferredEmail(null);
            db.accounts().update(Collections.singleton(a));
            byIdCache.evict(from);
          }
          byEmailCache.evict(who.getEmailAddress());
        }

      } else {
        throw new AccountException("Identity '" + who.getExternalIdKey().get() + "' not found");
      }

      return new AuthResult(from, who.getExternalIdKey(), false);
    }
  }
}
