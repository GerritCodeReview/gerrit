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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.auth.NoSuchUserException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupsUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks authentication related details for user accounts. */
@Singleton
public class AccountManager {
  private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

  private final SchemaFactory<ReviewDb> schema;
  private final Sequences sequences;
  private final Accounts accounts;
  private final AccountsUpdate.Server accountsUpdateFactory;
  private final AccountCache byIdCache;
  private final Realm realm;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeUserName.Factory changeUserNameFactory;
  private final ProjectCache projectCache;
  private final AtomicBoolean awaitsFirstAccountCheck;
  private final ExternalIds externalIds;
  private final ExternalIdsUpdate.Server externalIdsUpdateFactory;
  private final GroupsUpdate.Factory groupsUpdateFactory;
  private final boolean autoUpdateAccountActiveStatus;
  private final SetInactiveFlag setInactiveFlag;

  @Inject
  AccountManager(
      SchemaFactory<ReviewDb> schema,
      Sequences sequences,
      @GerritServerConfig Config cfg,
      Accounts accounts,
      AccountsUpdate.Server accountsUpdateFactory,
      AccountCache byIdCache,
      Realm accountMapper,
      IdentifiedUser.GenericFactory userFactory,
      ChangeUserName.Factory changeUserNameFactory,
      ProjectCache projectCache,
      ExternalIds externalIds,
      ExternalIdsUpdate.Server externalIdsUpdateFactory,
      GroupsUpdate.Factory groupsUpdateFactory,
      SetInactiveFlag setInactiveFlag) {
    this.schema = schema;
    this.sequences = sequences;
    this.accounts = accounts;
    this.accountsUpdateFactory = accountsUpdateFactory;
    this.byIdCache = byIdCache;
    this.realm = accountMapper;
    this.userFactory = userFactory;
    this.changeUserNameFactory = changeUserNameFactory;
    this.projectCache = projectCache;
    this.awaitsFirstAccountCheck =
        new AtomicBoolean(cfg.getBoolean("capability", "makeFirstUserAdmin", true));
    this.externalIds = externalIds;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
    this.groupsUpdateFactory = groupsUpdateFactory;
    this.autoUpdateAccountActiveStatus =
        cfg.getBoolean("auth", "autoUpdateAccountActiveStatus", false);
    this.setInactiveFlag = setInactiveFlag;
  }

  /** @return user identified by this external identity string */
  public Optional<Account.Id> lookup(String externalId) throws AccountException {
    try {
      ExternalId extId = externalIds.get(ExternalId.Key.parse(externalId));
      return extId != null ? Optional.of(extId.accountId()) : Optional.empty();
    } catch (IOException | ConfigInvalidException e) {
      throw new AccountException("Cannot lookup account " + externalId, e);
    }
  }

  /**
   * Authenticate the user, potentially creating a new account if they are new.
   *
   * @param who identity of the user, with any details we received about them.
   * @return the result of authenticating the user.
   * @throws AccountException the account does not exist, and cannot be created, or exists, but
   *     cannot be located, is unable to be activated or deactivated, or is inactive, or cannot be
   *     added to the admin group (only for the first account).
   */
  public AuthResult authenticate(AuthRequest who) throws AccountException, IOException {
    try {
      who = realm.authenticate(who);
    } catch (NoSuchUserException e) {
      deactivateAccountIfItExists(who);
      throw e;
    }
    try {
      try (ReviewDb db = schema.open()) {
        ExternalId id = externalIds.get(who.getExternalIdKey());
        if (id == null) {
          if (who.getUserName() != null) {
            ExternalId.Key key = ExternalId.Key.create(SCHEME_USERNAME, who.getUserName());
            ExternalId existingId = externalIds.get(key);
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
        Account act = updateAccountActiveStatus(who, byIdCache.get(id.accountId()).getAccount());
        if (!act.isActive()) {
          throw new AccountException("Authentication error, account inactive");
        }

        // return the identity to the caller.
        update(who, id);
        return new AuthResult(id.accountId(), who.getExternalIdKey(), false);
      }
    } catch (OrmException | ConfigInvalidException e) {
      throw new AccountException("Authentication error", e);
    }
  }

  private void deactivateAccountIfItExists(AuthRequest authRequest) {
    if (!shouldUpdateActiveStatus(authRequest)) {
      return;
    }
    try {
      ExternalId id = externalIds.get(authRequest.getExternalIdKey());
      if (id == null) {
        return;
      }
      setInactiveFlag.deactivate(id.accountId());
    } catch (Exception e) {
      log.error("Unable to deactivate account " + authRequest.getUserName(), e);
    }
  }

  private Account updateAccountActiveStatus(AuthRequest authRequest, Account account)
      throws AccountException {
    if (!shouldUpdateActiveStatus(authRequest) || authRequest.isActive() == account.isActive()) {
      return account;
    }

    if (authRequest.isActive()) {
      try {
        setInactiveFlag.activate(account.getId());
      } catch (Exception e) {
        throw new AccountException("Unable to activate account " + account.getId(), e);
      }
    } else {
      try {
        setInactiveFlag.deactivate(account.getId());
      } catch (Exception e) {
        throw new AccountException("Unable to deactivate account " + account.getId(), e);
      }
    }
    return byIdCache.get(account.getId()).getAccount();
  }

  private boolean shouldUpdateActiveStatus(AuthRequest authRequest) {
    return autoUpdateAccountActiveStatus && authRequest.authProvidesAccountActiveStatus();
  }

  private void update(AuthRequest who, ExternalId extId)
      throws OrmException, IOException, ConfigInvalidException {
    IdentifiedUser user = userFactory.create(extId.accountId());
    List<Consumer<Account>> accountUpdates = new ArrayList<>();

    // If the email address was modified by the authentication provider,
    // update our records to match the changed email.
    //
    String newEmail = who.getEmailAddress();
    String oldEmail = extId.email();
    if (newEmail != null && !newEmail.equals(oldEmail)) {
      if (oldEmail != null && oldEmail.equals(user.getAccount().getPreferredEmail())) {
        accountUpdates.add(a -> a.setPreferredEmail(newEmail));
      }

      externalIdsUpdateFactory
          .create()
          .replace(
              extId, ExternalId.create(extId.key(), extId.accountId(), newEmail, extId.password()));
    }

    if (!realm.allowsEdit(AccountFieldName.FULL_NAME)
        && !Strings.isNullOrEmpty(who.getDisplayName())
        && !eq(user.getAccount().getFullName(), who.getDisplayName())) {
      accountUpdates.add(a -> a.setFullName(who.getDisplayName()));
    }

    if (!realm.allowsEdit(AccountFieldName.USER_NAME)
        && who.getUserName() != null
        && !eq(user.getUserName(), who.getUserName())) {
      log.warn(
          String.format(
              "Not changing already set username %s to %s", user.getUserName(), who.getUserName()));
    }

    if (!accountUpdates.isEmpty()) {
      Account account = accountsUpdateFactory.create().update(user.getAccountId(), accountUpdates);
      if (account == null) {
        throw new OrmException("Account " + user.getAccountId() + " has been deleted");
      }
    }
  }

  private static boolean eq(String a, String b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }

  private AuthResult create(ReviewDb db, AuthRequest who)
      throws OrmException, AccountException, IOException, ConfigInvalidException {
    Account.Id newId = new Account.Id(sequences.nextAccountId());

    ExternalId extId =
        ExternalId.createWithEmail(who.getExternalIdKey(), newId, who.getEmailAddress());

    boolean isFirstAccount = awaitsFirstAccountCheck.getAndSet(false) && !accounts.hasAnyAccount();

    Account account;
    try {
      AccountsUpdate accountsUpdate = accountsUpdateFactory.create();
      account =
          accountsUpdate.insert(
              newId,
              a -> {
                a.setFullName(who.getDisplayName());
                a.setPreferredEmail(extId.email());
              });

      ExternalId existingExtId = externalIds.get(extId.key());
      if (existingExtId != null && !existingExtId.accountId().equals(extId.accountId())) {
        // external ID is assigned to another account, do not overwrite
        accountsUpdate.delete(account);
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

    IdentifiedUser user = userFactory.create(newId);

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
      // The user initiated this request by logging in. -> Attribute all modifications to that user.
      GroupsUpdate groupsUpdate = groupsUpdateFactory.create(user);
      try {
        groupsUpdate.addGroupMember(db, uuid, newId);
      } catch (NoSuchGroupException e) {
        throw new AccountException(String.format("Group %s not found", uuid));
      }
    }

    if (who.getUserName() != null) {
      // Only set if the name hasn't been used yet, but was given to us.
      //
      try {
        changeUserNameFactory.create(user, who.getUserName()).call();
      } catch (NameAlreadyUsedException e) {
        String message =
            "Cannot assign user name \""
                + who.getUserName()
                + "\" to account "
                + newId
                + "; name already in use.";
        handleSettingUserNameFailure(account, extId, message, e, false);
      } catch (InvalidUserNameException e) {
        String message =
            "Cannot assign user name \""
                + who.getUserName()
                + "\" to account "
                + newId
                + "; name does not conform.";
        handleSettingUserNameFailure(account, extId, message, e, false);
      } catch (OrmException e) {
        String message = "Cannot assign user name";
        handleSettingUserNameFailure(account, extId, message, e, true);
      }
    }

    realm.onCreateAccount(who, account);
    return new AuthResult(newId, extId.key(), true);
  }

  /**
   * This method handles an exception that occurred during the setting of the user name for a newly
   * created account. If the realm does not allow the user to set a user name manually this method
   * deletes the newly created account and throws an {@link AccountUserNameException}. In any case
   * the error message is logged.
   *
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
      Account account, ExternalId extId, String errorMessage, Exception e, boolean logException)
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
      // (without 'username:<USERNAME>' external ID),
      // such an account cannot be used for uploading changes,
      // this is why the best we can do here is to fail early and cleanup
      // the database
      accountsUpdateFactory.create().delete(account);
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
    ExternalId extId = externalIds.get(who.getExternalIdKey());
    log.info("Link another authentication identity to an existing account");
    if (extId != null) {
      if (!extId.accountId().equals(to)) {
        throw new AccountException(
            "Identity '" + extId.key().get() + "' in use by another account");
      }
      update(who, extId);
    } else {
      log.info("Linking new external ID to the existing account");
      externalIdsUpdateFactory
          .create()
          .insert(ExternalId.createWithEmail(who.getExternalIdKey(), to, who.getEmailAddress()));

      if (who.getEmailAddress() != null) {
        accountsUpdateFactory
            .create()
            .update(
                to,
                a -> {
                  if (a.getPreferredEmail() == null) {
                    a.setPreferredEmail(who.getEmailAddress());
                  }
                });
      }
    }

    return new AuthResult(to, who.getExternalIdKey(), false);
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

    List<ExternalId> extIds = new ArrayList<>(extIdKeys.size());
    for (ExternalId.Key extIdKey : extIdKeys) {
      ExternalId extId = externalIds.get(extIdKey);
      if (extId != null) {
        if (!extId.accountId().equals(from)) {
          throw new AccountException("Identity '" + extIdKey.get() + "' in use by another account");
        }
        extIds.add(extId);
      } else {
        throw new AccountException("Identity '" + extIdKey.get() + "' not found");
      }
    }

    externalIdsUpdateFactory.create().delete(extIds);

    if (extIds.stream().anyMatch(e -> e.email() != null)) {
      accountsUpdateFactory
          .create()
          .update(
              from,
              a -> {
                if (a.getPreferredEmail() != null) {
                  for (ExternalId extId : extIds) {
                    if (a.getPreferredEmail().equals(extId.email())) {
                      a.setPreferredEmail(null);
                      break;
                    }
                  }
                }
              });
    }
  }
}
