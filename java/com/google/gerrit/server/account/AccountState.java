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

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Superset of all information related to an Account. This includes external IDs, project watches,
 * and properties from the account config file. AccountState maps one-to-one to Account.
 *
 * <p>Most callers should not construct AccountStates directly but rather lookup accounts via the
 * account cache (see {@link AccountCache#get(Account.Id)}).
 */
@AutoValue
public abstract class AccountState {
  /**
   * Creates an AccountState from the given account config.
   *
   * @param externalIds class to access external IDs
   * @param accountConfig the account config, must already be loaded
   * @return the account state, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if accessing the external IDs fails
   */
  public static Optional<AccountState> fromAccountConfig(
      ExternalIds externalIds, AccountConfig accountConfig) throws IOException {
    return fromAccountConfig(externalIds, accountConfig, null);
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
   * @param externalIds class to access external IDs
   * @param accountConfig the account config, must already be loaded
   * @param extIdNotes external ID notes, must already be loaded, may be {@code null}
   * @return the account state, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if accessing the external IDs fails
   */
  public static Optional<AccountState> fromAccountConfig(
      ExternalIds externalIds, AccountConfig accountConfig, @Nullable ExternalIdNotes extIdNotes)
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
            ? ImmutableSet.copyOf(externalIds.byAccount(account.id(), extIdsRev.get()))
            : ImmutableSet.of();

    // Don't leak references to AccountConfig into the AccountState, since it holds a reference to
    // an open Repository instance.
    ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches =
        accountConfig.getProjectWatches();

    return Optional.of(
        new AutoValue_AccountState(
            account,
            extIds,
            ExternalId.getUserName(extIds),
            projectWatches,
            Optional.of(accountConfig.getRawDefaultPreferences()),
            Optional.of(accountConfig.getRawPreferences())));
  }

  /**
   * Creates an AccountState for a given account with no external IDs, no project watches and
   * default preferences.
   *
   * @param account the account
   * @return the account state
   */
  public static AccountState forAccount(Account account) {
    return forAccount(account, ImmutableSet.of());
  }

  /**
   * Creates an AccountState for a given account with no external IDs, no project watches and
   * default preferences.
   *
   * @param account the account
   * @return the account state
   */
  public static AccountState forCachedAccount(
      CachedAccountDetails account, String defaultConfig, ExternalIds externalIds)
      throws IOException {
    ImmutableSet<ExternalId> extIds =
        ImmutableSet.copyOf(externalIds.byAccount(account.account().id()));
    return new AutoValue_AccountState(
        account.account(),
        extIds,
        ExternalId.getUserName(extIds),
        account.projectWatches(),
        Optional.of(defaultConfig),
        Optional.of(account.rawPreferences()));
  }

  /**
   * Creates an AccountState for a given account with no project watches and default preferences.
   *
   * @param account the account
   * @param extIds the external IDs
   * @return the account state
   */
  public static AccountState forAccount(Account account, Collection<ExternalId> extIds) {
    return new AutoValue_AccountState(
        account,
        ImmutableSet.copyOf(extIds),
        ExternalId.getUserName(extIds),
        ImmutableMap.of(),
        Optional.empty(),
        Optional.empty());
  }

  /** Get the cached account metadata. */
  public abstract Account account();
  /** The external identities that identify the account holder. */
  public abstract ImmutableSet<ExternalId> externalIds();
  /**
   * Get the username, if one has been declared for this user.
   *
   * <p>The username is the {@link ExternalId} using the scheme {@link ExternalId#SCHEME_USERNAME}.
   *
   * @return the username, {@link Optional#empty()} if the user has no username, or if the username
   *     is empty
   */
  public abstract Optional<String> userName();
  /** The project watches of the account. */
  public abstract ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches();
  /** The general preferences of the account. */

  /** The general preferences of the account. */
  public GeneralPreferencesInfo generalPreferences() {
    if (!defaultPreferences().isPresent() || !userPreferences().isPresent()) {
      return GeneralPreferencesInfo.defaults();
    }
    return parsePreferences(StoredPreferences::parseGeneralPreferences);
  }

  /** The diff preferences of the account. */
  public DiffPreferencesInfo diffPreferences() {
    if (!defaultPreferences().isPresent() || !userPreferences().isPresent()) {
      return DiffPreferencesInfo.defaults();
    }
    return parsePreferences(StoredPreferences::parseDiffPreferences);
  }

  /** The edit preferences of the account. */
  public EditPreferencesInfo editPreferences() {
    if (!defaultPreferences().isPresent() || !userPreferences().isPresent()) {
      return EditPreferencesInfo.defaults();
    }
    return parsePreferences(StoredPreferences::parseEditPreferences);
  }

  @Override
  public final String toString() {
    MoreObjects.ToStringHelper h = MoreObjects.toStringHelper(this);
    h.addValue(account().id());
    return h.toString();
  }

  /** Gerrit's default preferences as stored in {@code preferences.config}. */
  protected abstract Optional<String> defaultPreferences();

  /** User preferences as stored in {@code preferences.config}. */
  protected abstract Optional<String> userPreferences();

  @FunctionalInterface
  private interface PreferenceParser<T> {
    T apply(Config defaultPreferences, Config userPreferences, T overlay)
        throws ConfigInvalidException;
  }

  private <T> T parsePreferences(PreferenceParser<T> parser) {
    Config userPrefs = new Config();
    Config defaultPrefs = new Config();
    try {
      userPrefs.fromText(userPreferences().get());
      defaultPrefs.fromText(defaultPreferences().get());
      return parser.apply(userPrefs, defaultPrefs, null);
    } catch (ConfigInvalidException e) {
      // This is a programmer error because these Configs have been parsed before.
      throw new StorageException(e);
    }
  }
}
