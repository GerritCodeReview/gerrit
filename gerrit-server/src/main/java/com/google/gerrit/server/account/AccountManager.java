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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
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
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks authentication related details for user accounts. */
@Singleton
public class AccountManager {
  private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

  private final SchemaFactory<ReviewDb> schema;
  private final Accounts accounts;
  private final AccountsUpdate.Server accountsUpdateFactory;
  private final AccountCache byIdCache;
  private final AccountByEmailCache byEmailCache;
  private final Realm realm;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeUserName.Factory changeUserNameFactory;
  private final ProjectCache projectCache;
  private final AtomicBoolean awaitsFirstAccountCheck;
  private final AuditService auditService;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final ExternalIds externalIds;
  private final ExternalIdsUpdate.Server externalIdsUpdateFactory;

  @Inject
  AccountManager(
      SchemaFactory<ReviewDb> schema,
      @GerritServerConfig Config cfg,
      Accounts accounts,
      AccountsUpdate.Server accountsUpdateFactory,
      AccountCache byIdCache,
      AccountByEmailCache byEmailCache,
      Realm accountMapper,
      IdentifiedUser.GenericFactory userFactory,
      ChangeUserName.Factory changeUserNameFactory,
      ProjectCache projectCache,
      AuditService auditService,
      Provider<InternalAccountQuery> accountQueryProvider,
      ExternalIds externalIds,
      ExternalIdsUpdate.Server externalIdsUpdateFactory) {
    this.schema = schema;
    this.accounts = accounts;
    this.accountsUpdateFactory = accountsUpdateFactory;
    this.byIdCache = byIdCache;
    this.byEmailCache = byEmailCache;
    this.realm = accountMapper;
    this.userFactory = userFactory;
    this.changeUserNameFactory = changeUserNameFactory;
    this.projectCache = projectCache;
    this.awaitsFirstAccountCheck =
        new AtomicBoolean(cfg.getBoolean("capability", "makeFirstUserAdmin", true));
    this.auditService = auditService;
    this.accountQueryProvider = accountQueryProvider;
    this.externalIds = externalIds;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
  }

  /** @return user identified by this external identity string */
  public Optional<Account.Id> lookup(String externalId) throws AccountException {
    try {
      AccountState accountState = accountQueryProvider.get().oneByExternalId(externalId);
      return accountState != null
          ? Optional.of(accountState.getAccount().getId())
          : Optional.empty();
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
        ExternalId id = findExternalId(who.getExternalIdKey());
        if (id == null) {
          // New account, automatically create and return.
          //
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

  private ExternalId findExternalId(ExternalId.Key key) throws OrmException {
    AccountState accountState = accountQueryProvider.get().oneByExternalId(key);
    if (accountState != null) {
      for (ExternalId extId : accountState.getExternalIds()) {
        if (extId.key().equals(key)) {
          return extId;
        }
      }
    }
    return null;
  }

  private void update(ReviewDb db, AuthRequest who, ExternalId extId)
      throws OrmException, IOException, ConfigInvalidException {
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
              extId, ExternalId.create(extId.key(), extId.accountId(), newEmail, extId.password()));
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
      accountsUpdateFactory.create().update(db, toUpdate);
    }

    if (newEmail != null && !newEmail.equals(oldEmail)) {
      byEmailCache.evict(oldEmail);
      byEmailCache.evict(newEmail);
    }
  }

  private Account load(Account toUpdate, Account.Id accountId, ReviewDb db) throws OrmException {
    if (toUpdate == null) {
      toUpdate = accounts.get(db, accountId);
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

    boolean isFirstAccount = awaitsFirstAccountCheck.getAndSet(false) && !accounts.hasAnyAccount();

    try {
      AccountsUpdate accountsUpdate = accountsUpdateFactory.create();
      accountsUpdate.upsert(db, account);

      ExternalId existingExtId = externalIds.get(extId.key());
      if (existingExtId != null && !existingExtId.accountId().equals(extId.accountId())) {
        // external ID is assigned to another account, do not overwrite
        accountsUpdate.delete(db, account);
        throw new AccountException(
            "Cannot assign external ID \""
                + extId.key().get()
                + "\" to account "
                + newId
                + "; external ID already in use.");
      }
      externalIdsUpdateFactory.create().upsert(extId);
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
      AccountGroup g = db.accountGroups().byUUID(uuid).iterator().next();
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
        changeUserNameFactory.create(user, who.getUserName()).call();
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
      throws AccountUserNameException, OrmException, IOException, ConfigInvalidException {
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
      accountsUpdateFactory.create().delete(db, account);
      externalIdsUpdateFactory.create().delete(extId);
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
      throws AccountException, OrmException, IOException, ConfigInvalidException {
    try (ReviewDb db = schema.open()) {
      ExternalId extId = findExternalId(who.getExternalIdKey());
      if (extId != null) {
        if (!extId.accountId().equals(to)) {
          throw new AccountException("Identity in use by another account");
        }
        update(db, who, extId);
      } else {
        externalIdsUpdateFactory
            .create()
            .insert(ExternalId.createWithEmail(who.getExternalIdKey(), to, who.getEmailAddress()));

        if (who.getEmailAddress() != null) {
          Account a = accounts.get(db, to);
          if (a.getPreferredEmail() == null) {
            a.setPreferredEmail(who.getEmailAddress());
            accountsUpdateFactory.create().update(db, a);
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
      throws OrmException, AccountException, IOException, ConfigInvalidException {
    Collection<ExternalId> filteredExtIdsByScheme =
        externalIds.byAccount(to, who.getExternalIdKey().scheme());

    if (!filteredExtIdsByScheme.isEmpty()
        && (filteredExtIdsByScheme.size() > 1
            || !filteredExtIdsByScheme
                .stream()
                .filter(e -> e.key().equals(who.getExternalIdKey()))
                .findAny()
                .isPresent())) {
      externalIdsUpdateFactory.create().delete(filteredExtIdsByScheme);
    }
    return link(to, who);
  }

  /**
   * Unlink an external identity from an existing account.
   *
   * @param from account to unlink the external identity from
   * @param extIdKey the key of the external ID that should be deleted
   * @throws AccountException the identity belongs to a different account, or the identity was not
   *     found
   */
  public void unlink(Account.Id from, ExternalId.Key extIdKey)
      throws AccountException, OrmException, IOException, ConfigInvalidException {
    unlink(from, ImmutableList.of(extIdKey));
  }

  /**
   * Unlink an external identities from an existing account.
   *
   * @param from account to unlink the external identity from
   * @param extIdKeys the keys of the external IDs that should be deleted
   * @throws AccountException any of the identity belongs to a different account, or any of the
   *     identity was not found
   */
  public void unlink(Account.Id from, Collection<ExternalId.Key> extIdKeys)
      throws AccountException, OrmException, IOException, ConfigInvalidException {
    if (extIdKeys.isEmpty()) {
      return;
    }

    try (ReviewDb db = schema.open()) {
      List<ExternalId> extIds = new ArrayList<>(extIdKeys.size());
      for (ExternalId.Key extIdKey : extIdKeys) {
        ExternalId extId = findExternalId(extIdKey);
        if (extId != null) {
          if (!extId.accountId().equals(from)) {
            throw new AccountException(
                "Identity '" + extIdKey.get() + "' in use by another account");
          }
          extIds.add(extId);
        } else {
          throw new AccountException("Identity '" + extIdKey.get() + "' not found");
        }
      }

      externalIdsUpdateFactory.create().delete(extIds);

      if (extIds.stream().anyMatch(e -> e.email() != null)) {
        Account a = accounts.get(db, from);
        for (ExternalId extId : extIds) {
          if (a.getPreferredEmail() != null) {
            if (a.getPreferredEmail().equals(extId.email())) {
              a.setPreferredEmail(null);
              accountsUpdateFactory.create().update(db, a);
              byEmailCache.evict(extId.email());
              break;
            }
          } else {
            break;
          }
        }
      }
    }
  }
}
