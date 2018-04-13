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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser.PropertyKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superset of all information related to an Account. This includes external IDs, project watches,
 * and properties from the account config file. AccountState maps one-to-one to Account.
 *
 * <p>Most callers should not construct AccountStates directly but rather lookup accounts via the
 * account cache (see {@link AccountCache#get(Account.Id)}).
 */
public class AccountState {
  private static final Logger logger = LoggerFactory.getLogger(AccountState.class);

  public static final Function<AccountState, Account.Id> ACCOUNT_ID_FUNCTION =
      a -> a.getAccount().getId();

  /**
   * Creates an AccountState from the given account config.
   *
   * @param allUsersName the name of the All-Users repository
   * @param externalIds class to access external IDs
   * @param accountConfig the account config, must already be loaded
   * @return the account state, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if accessing the external IDs fails
   */
  public static Optional<AccountState> fromAccountConfig(
      AllUsersName allUsersName, ExternalIds externalIds, AccountConfig accountConfig)
      throws IOException {
    return fromAccountConfig(allUsersName, externalIds, accountConfig, null);
  }

  /**
   * Creates an AccountState from the given account config.
   *
   * <p>If external ID notes are provided the revision of the external IDs branch from which the
   * external IDs for the account should be loaded is taken from the external ID notes. If external
   * ID notes are not given the revision of the external IDs branch is taken from the account
   * config. Updating external IDs is done via {@link ExternalIdNotes} and if external IDs were
   * updated the revision of the external IDs branch in account config is outdated. Hence after
   * updating external IDs the external ID notes must be provided.
   *
   * @param allUsersName the name of the All-Users repository
   * @param externalIds class to access external IDs
   * @param accountConfig the account config, must already be loaded
   * @param extIdNotes external ID notes, must already be loaded, may be {@code null}
   * @return the account state, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if accessing the external IDs fails
   */
  public static Optional<AccountState> fromAccountConfig(
      AllUsersName allUsersName,
      ExternalIds externalIds,
      AccountConfig accountConfig,
      @Nullable ExternalIdNotes extIdNotes)
      throws IOException {
    if (!accountConfig.getLoadedAccount().isPresent()) {
      return Optional.empty();
    }
    Account account = accountConfig.getLoadedAccount().get();

    Optional<ObjectId> extIdsRev =
        extIdNotes != null
            ? Optional.ofNullable(extIdNotes.getRevision())
            : accountConfig.getExternalIdsRev();
    ImmutableSet<ExternalId> extIds =
        extIdsRev.isPresent()
            ? ImmutableSet.copyOf(externalIds.byAccount(account.getId(), extIdsRev.get()))
            : ImmutableSet.of();

    // Don't leak references to AccountConfig into the AccountState, since it holds a reference to
    // an open Repository instance.
    ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches =
        accountConfig.getProjectWatches();
    GeneralPreferencesInfo generalPreferences = accountConfig.getGeneralPreferences();
    DiffPreferencesInfo diffPreferences = accountConfig.getDiffPreferences();
    EditPreferencesInfo editPreferences = accountConfig.getEditPreferences();

    return Optional.of(
        new AccountState(
            allUsersName,
            account,
            extIds,
            projectWatches,
            generalPreferences,
            diffPreferences,
            editPreferences));
  }

  /**
   * Creates an AccountState for a given account with no external IDs, no project watches and
   * default preferences.
   *
   * @param allUsersName the name of the All-Users repository
   * @param account the account
   * @return the account state
   */
  public static AccountState forAccount(AllUsersName allUsersName, Account account) {
    return forAccount(allUsersName, account, ImmutableSet.of());
  }

  /**
   * Creates an AccountState for a given account with no project watches and default preferences.
   *
   * @param allUsersName the name of the All-Users repository
   * @param account the account
   * @param extIds the external IDs
   * @return the account state
   */
  public static AccountState forAccount(
      AllUsersName allUsersName, Account account, Collection<ExternalId> extIds) {
    return new AccountState(
        allUsersName,
        account,
        ImmutableSet.copyOf(extIds),
        ImmutableMap.of(),
        GeneralPreferencesInfo.defaults(),
        DiffPreferencesInfo.defaults(),
        EditPreferencesInfo.defaults());
  }

  private final AllUsersName allUsersName;
  private final Account account;
  private final ImmutableSet<ExternalId> externalIds;
  private final Optional<String> userName;
  private final ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches;
  private final GeneralPreferencesInfo generalPreferences;
  private final DiffPreferencesInfo diffPreferences;
  private final EditPreferencesInfo editPreferences;
  private Cache<IdentifiedUser.PropertyKey<Object>, Object> properties;

  private AccountState(
      AllUsersName allUsersName,
      Account account,
      ImmutableSet<ExternalId> externalIds,
      ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches,
      GeneralPreferencesInfo generalPreferences,
      DiffPreferencesInfo diffPreferences,
      EditPreferencesInfo editPreferences) {
    this.allUsersName = allUsersName;
    this.account = account;
    this.externalIds = externalIds;
    this.userName = ExternalId.getUserName(externalIds);
    this.projectWatches = projectWatches;
    this.generalPreferences = generalPreferences;
    this.diffPreferences = diffPreferences;
    this.editPreferences = editPreferences;
  }

  public AllUsersName getAllUsersNameForIndexing() {
    return allUsersName;
  }

  /** Get the cached account metadata. */
  public Account getAccount() {
    return account;
  }

  /**
   * Get the username, if one has been declared for this user.
   *
   * <p>The username is the {@link ExternalId} using the scheme {@link ExternalId#SCHEME_USERNAME}.
   *
   * @return the username, {@link Optional#empty()} if the user has no username, or if the username
   *     is empty
   */
  public Optional<String> getUserName() {
    return userName;
  }

  public boolean checkPassword(@Nullable String password, String username) {
    if (password == null) {
      return false;
    }
    for (ExternalId id : getExternalIds()) {
      // Only process the "username:$USER" entry, which is unique.
      if (!id.isScheme(SCHEME_USERNAME) || !username.equals(id.key().id())) {
        continue;
      }

      String hashedStr = id.password();
      if (!Strings.isNullOrEmpty(hashedStr)) {
        try {
          return HashedPassword.decode(hashedStr).checkPassword(password);
        } catch (DecoderException e) {
          logger.error(
              String.format("DecoderException for user %s: %s ", username, e.getMessage()));
          return false;
        }
      }
    }
    return false;
  }

  /** The external identities that identify the account holder. */
  public ImmutableSet<ExternalId> getExternalIds() {
    return externalIds;
  }

  /** The external identities that identify the account holder that match the given scheme. */
  public ImmutableSet<ExternalId> getExternalIds(String scheme) {
    return externalIds.stream().filter(e -> e.key().isScheme(scheme)).collect(toImmutableSet());
  }

  /** The project watches of the account. */
  public ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> getProjectWatches() {
    return projectWatches;
  }

  /** The general preferences of the account. */
  public GeneralPreferencesInfo getGeneralPreferences() {
    return generalPreferences;
  }

  /** The diff preferences of the account. */
  public DiffPreferencesInfo getDiffPreferences() {
    return diffPreferences;
  }

  /** The edit preferences of the account. */
  public EditPreferencesInfo getEditPreferences() {
    return editPreferences;
  }

  /**
   * Lookup a previously stored property.
   *
   * <p>All properties are automatically cleared when the account cache invalidates the {@code
   * AccountState}. This method is thread-safe.
   *
   * @param key unique property key.
   * @return previously stored value, or {@code null}.
   */
  @Nullable
  public <T> T get(PropertyKey<T> key) {
    Cache<PropertyKey<Object>, Object> p = properties(false);
    if (p != null) {
      @SuppressWarnings("unchecked")
      T value = (T) p.getIfPresent(key);
      return value;
    }
    return null;
  }

  /**
   * Store a property for later retrieval.
   *
   * <p>This method is thread-safe.
   *
   * @param key unique property key.
   * @param value value to store; or {@code null} to clear the value.
   */
  public <T> void put(PropertyKey<T> key, @Nullable T value) {
    Cache<PropertyKey<Object>, Object> p = properties(value != null);
    if (p != null) {
      @SuppressWarnings("unchecked")
      PropertyKey<Object> k = (PropertyKey<Object>) key;
      if (value != null) {
        p.put(k, value);
      } else {
        p.invalidate(k);
      }
    }
  }

  private synchronized Cache<PropertyKey<Object>, Object> properties(boolean allocate) {
    if (properties == null && allocate) {
      properties =
          CacheBuilder.newBuilder()
              .concurrencyLevel(1)
              .initialCapacity(16)
              // Use weakKeys to ensure plugins that garbage collect will also
              // eventually release data held in any still live AccountState.
              .weakKeys()
              .build();
    }
    return properties;
  }
}
