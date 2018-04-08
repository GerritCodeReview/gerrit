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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate.AccountUpdater;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.auth.NoSuchUserException;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  private final Sequences sequences;
  private final Accounts accounts;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final AccountCache byIdCache;
  private final Realm realm;
  private final IdentifiedUser.GenericFactory userFactory;
  private final SshKeyCache sshKeyCache;
  private final ProjectCache projectCache;
  private final AtomicBoolean awaitsFirstAccountCheck;
  private final ExternalIds externalIds;
  private final GroupsUpdate.Factory groupsUpdateFactory;
  private final boolean autoUpdateAccountActiveStatus;
  private final SetInactiveFlag setInactiveFlag;

  @Inject
  AccountManager(
      Sequences sequences,
      @GerritServerConfig Config cfg,
      Accounts accounts,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      AccountCache byIdCache,
      Realm accountMapper,
      IdentifiedUser.GenericFactory userFactory,
      SshKeyCache sshKeyCache,
      ProjectCache projectCache,
      ExternalIds externalIds,
      GroupsUpdate.Factory groupsUpdateFactory,
      SetInactiveFlag setInactiveFlag) {
    this.sequences = sequences;
    this.accounts = accounts;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.byIdCache = byIdCache;
    this.realm = accountMapper;
    this.userFactory = userFactory;
    this.sshKeyCache = sshKeyCache;
    this.projectCache = projectCache;
    this.awaitsFirstAccountCheck =
        new AtomicBoolean(cfg.getBoolean("capability", "makeFirstUserAdmin", true));
    this.externalIds = externalIds;
    this.groupsUpdateFactory = groupsUpdateFactory;
    this.autoUpdateAccountActiveStatus =
        cfg.getBoolean("auth", "autoUpdateAccountActiveStatus", false);
    this.setInactiveFlag = setInactiveFlag;
  }

  /** @return user identified by this external identity string */
  public Optional<Account.Id> lookup(String externalId) throws AccountException {
    try {
      return externalIds.get(ExternalId.Key.parse(externalId)).map(ExternalId::accountId);
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
      Optional<ExternalId> optionalExtId = externalIds.get(who.getExternalIdKey());
      if (!optionalExtId.isPresent()) {
        if (who.getUserName().isPresent()) {
          ExternalId.Key key = ExternalId.Key.create(SCHEME_USERNAME, who.getUserName().get());
          Optional<ExternalId> existingId = externalIds.get(key);
          if (existingId.isPresent()) {
            // An inconsistency is detected in the database, having a record for scheme "username:"
            // but no record for scheme "gerrit:". Try to recover by linking
            // "gerrit:" identity to the existing account.
            log.warn(
                "User {} already has an account; link new identity to the existing account.",
                who.getUserName());
            return link(existingId.get().accountId(), who);
          }
        }
        // New account, automatically create and return.
        log.info("External ID not found. Attempting to create new account.");
        return create(who);
      }

      ExternalId extId = optionalExtId.get();
      Optional<AccountState> accountState = byIdCache.get(extId.accountId());
      if (!accountState.isPresent()) {
        log.error(
            String.format(
                "Authentication with external ID %s failed. Account %s doesn't exist.",
                extId.key().get(), extId.accountId().get()));
        throw new AccountException("Authentication error, account not found");
      }

      // Account exists
      Optional<Account> act = updateAccountActiveStatus(who, accountState.get().getAccount());
      if (!act.isPresent()) {
        // The account was deleted since we checked for it last time. This should never happen
        // since we don't support deletion of accounts.
        throw new AccountException("Authentication error, account not found");
      }
      if (!act.get().isActive()) {
        throw new AccountException("Authentication error, account inactive");
      }

      // return the identity to the caller.
      update(who, extId);
      return new AuthResult(extId.accountId(), who.getExternalIdKey(), false);
    } catch (OrmException | ConfigInvalidException e) {
      throw new AccountException("Authentication error", e);
    }
  }

  private void deactivateAccountIfItExists(AuthRequest authRequest) {
    if (!shouldUpdateActiveStatus(authRequest)) {
      return;
    }
    try {
      Optional<ExternalId> extId = externalIds.get(authRequest.getExternalIdKey());
      if (!extId.isPresent()) {
        return;
      }
      setInactiveFlag.deactivate(extId.get().accountId());
    } catch (Exception e) {
      log.error(
          "Unable to deactivate account "
              + authRequest
                  .getUserName()
                  .orElse(" for external ID key " + authRequest.getExternalIdKey().get()),
          e);
    }
  }

  private Optional<Account> updateAccountActiveStatus(AuthRequest authRequest, Account account)
      throws AccountException {
    if (!shouldUpdateActiveStatus(authRequest) || authRequest.isActive() == account.isActive()) {
      return Optional.of(account);
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
    return byIdCache.get(account.getId()).map(AccountState::getAccount);
  }

  private boolean shouldUpdateActiveStatus(AuthRequest authRequest) {
    return autoUpdateAccountActiveStatus && authRequest.authProvidesAccountActiveStatus();
  }

  private void update(AuthRequest who, ExternalId extId)
      throws OrmException, IOException, ConfigInvalidException, AccountException {
    IdentifiedUser user = userFactory.create(extId.accountId());
    List<Consumer<InternalAccountUpdate.Builder>> accountUpdates = new ArrayList<>();

    // If the email address was modified by the authentication provider,
    // update our records to match the changed email.
    //
    String newEmail = who.getEmailAddress();
    String oldEmail = extId.email();
    if (newEmail != null && !newEmail.equals(oldEmail)) {
      ExternalId extIdWithNewEmail =
          ExternalId.create(extId.key(), extId.accountId(), newEmail, extId.password());
      checkEmailNotUsed(extIdWithNewEmail);
      accountUpdates.add(u -> u.replaceExternalId(extId, extIdWithNewEmail));

      if (oldEmail != null && oldEmail.equals(user.getAccount().getPreferredEmail())) {
        accountUpdates.add(u -> u.setPreferredEmail(newEmail));
      }
    }

    if (!realm.allowsEdit(AccountFieldName.FULL_NAME)
        && !Strings.isNullOrEmpty(who.getDisplayName())
        && !Objects.equals(user.getAccount().getFullName(), who.getDisplayName())) {
      accountUpdates.add(u -> u.setFullName(who.getDisplayName()));
    }

    if (!realm.allowsEdit(AccountFieldName.USER_NAME)
        && who.getUserName().isPresent()
        && !who.getUserName().equals(user.getUserName())) {
      if (user.getUserName().isPresent()) {
        log.warn(
            "Not changing already set username {} to {}",
            user.getUserName().get(),
            who.getUserName().get());
      } else {
        log.warn("Not setting username to {}", who.getUserName().get());
      }
    }

    if (!accountUpdates.isEmpty()) {
      accountsUpdateProvider
          .get()
          .update(
              "Update Account on Login",
              user.getAccountId(),
              AccountUpdater.joinConsumers(accountUpdates))
          .orElseThrow(
              () -> new OrmException("Account " + user.getAccountId() + " has been deleted"));
    }
  }

  private AuthResult create(AuthRequest who)
      throws OrmException, AccountException, IOException, ConfigInvalidException {
    Account.Id newId = new Account.Id(sequences.nextAccountId());

    ExternalId extId =
        ExternalId.createWithEmail(who.getExternalIdKey(), newId, who.getEmailAddress());
    checkEmailNotUsed(extId);
    ExternalId userNameExtId =
        who.getUserName().isPresent() ? createUsername(newId, who.getUserName().get()) : null;

    boolean isFirstAccount = awaitsFirstAccountCheck.getAndSet(false) && !accounts.hasAnyAccount();

    AccountState accountState;
    try {
      accountState =
          accountsUpdateProvider
              .get()
              .insert(
                  "Create Account on First Login",
                  newId,
                  u -> {
                    u.setFullName(who.getDisplayName())
                        .setPreferredEmail(extId.email())
                        .addExternalId(extId);
                    if (userNameExtId != null) {
                      u.addExternalId(userNameExtId);
                    }
                  });
    } catch (DuplicateExternalIdKeyException e) {
      throw new AccountException(
          "Cannot assign external ID \""
              + e.getDuplicateKey().get()
              + "\" to account "
              + newId
              + "; external ID already in use.");
    } finally {
      // If adding the account failed, it may be that it actually was the
      // first account. So we reset the 'check for first account'-guard, as
      // otherwise the first account would not get administration permissions.
      awaitsFirstAccountCheck.set(isFirstAccount);
    }

    if (userNameExtId != null) {
      who.getUserName().ifPresent(sshKeyCache::evict);
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

      AccountGroup.UUID adminGroupUuid = admin.getRules().get(0).getGroup().getUUID();
      addGroupMember(adminGroupUuid, user);
    }

    realm.onCreateAccount(who, accountState.getAccount());
    return new AuthResult(newId, extId.key(), true);
  }

  private ExternalId createUsername(Account.Id accountId, String username)
      throws AccountUserNameException {
    checkArgument(!Strings.isNullOrEmpty(username));

    if (!ExternalId.isValidUsername(username)) {
      throw new AccountUserNameException(
          String.format(
              "Cannot assign user name \"%s\" to account %s; name does not conform.",
              username, accountId));
    }
    return ExternalId.create(SCHEME_USERNAME, username, accountId);
  }

  private void checkEmailNotUsed(ExternalId extIdToBeCreated) throws IOException, AccountException {
    String email = extIdToBeCreated.email();
    if (email == null) {
      return;
    }

    Set<ExternalId> existingExtIdsWithEmail = externalIds.byEmail(email);
    if (existingExtIdsWithEmail.isEmpty()) {
      return;
    }

    log.warn(
        "Email {} is already assigned to account {};"
            + " cannot create external ID {} with the same email for account {}.",
        email,
        existingExtIdsWithEmail.iterator().next().accountId().get(),
        extIdToBeCreated.key().get(),
        extIdToBeCreated.accountId().get());
    throw new AccountException("Email '" + email + "' in use by another account");
  }

  private void addGroupMember(AccountGroup.UUID groupUuid, IdentifiedUser user)
      throws OrmException, IOException, ConfigInvalidException, AccountException {
    // The user initiated this request by logging in. -> Attribute all modifications to that user.
    GroupsUpdate groupsUpdate = groupsUpdateFactory.create(user);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(
                memberIds -> Sets.union(memberIds, ImmutableSet.of(user.getAccountId())))
            .build();
    try {
      groupsUpdate.updateGroup(groupUuid, groupUpdate);
    } catch (NoSuchGroupException e) {
      throw new AccountException(String.format("Group %s not found", groupUuid));
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
    Optional<ExternalId> optionalExtId = externalIds.get(who.getExternalIdKey());
    log.info("Link another authentication identity to an existing account");
    if (optionalExtId.isPresent()) {
      ExternalId extId = optionalExtId.get();
      if (!extId.accountId().equals(to)) {
        throw new AccountException(
            "Identity '" + extId.key().get() + "' in use by another account");
      }
      update(who, extId);
    } else {
      log.info("Linking new external ID to the existing account");
      ExternalId newExtId =
          ExternalId.createWithEmail(who.getExternalIdKey(), to, who.getEmailAddress());
      checkEmailNotUsed(newExtId);
      accountsUpdateProvider
          .get()
          .update(
              "Link External ID",
              to,
              (a, u) -> {
                u.addExternalId(newExtId);
                if (who.getEmailAddress() != null && a.getAccount().getPreferredEmail() == null) {
                  u.setPreferredEmail(who.getEmailAddress());
                }
              });
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
    accountsUpdateProvider
        .get()
        .update(
            "Delete External IDs on Update Link",
            to,
            (a, u) -> {
              Collection<ExternalId> filteredExtIdsByScheme =
                  a.getExternalIds(who.getExternalIdKey().scheme());
              if (filteredExtIdsByScheme.isEmpty()) {
                return;
              }

              if (filteredExtIdsByScheme.size() > 1
                  || !filteredExtIdsByScheme
                      .stream()
                      .anyMatch(e -> e.key().equals(who.getExternalIdKey()))) {
                u.deleteExternalIds(filteredExtIdsByScheme);
              }
            });

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
      Optional<ExternalId> extId = externalIds.get(extIdKey);
      if (extId.isPresent()) {
        if (!extId.get().accountId().equals(from)) {
          throw new AccountException("Identity '" + extIdKey.get() + "' in use by another account");
        }
        extIds.add(extId.get());
      } else {
        throw new AccountException("Identity '" + extIdKey.get() + "' not found");
      }
    }

    accountsUpdateProvider
        .get()
        .update(
            "Unlink External ID" + (extIds.size() > 1 ? "s" : ""),
            from,
            (a, u) -> {
              u.deleteExternalIds(extIds);
              if (a.getAccount().getPreferredEmail() != null
                  && extIds
                      .stream()
                      .anyMatch(e -> a.getAccount().getPreferredEmail().equals(e.email()))) {
                u.setPreferredEmail(null);
              }
            });
  }
}
