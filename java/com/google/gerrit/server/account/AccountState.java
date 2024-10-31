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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.CachedPreferences;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

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
   * Creates an AccountState for a given account and external IDs.
   *
   * @param account the account
   * @return the account state
   */
  public static AccountState forCachedAccount(
      CachedAccountDetails account, CachedPreferences defaultConfig, ExternalIds externalIds)
      throws IOException {
    ImmutableSet<ExternalId> extIds = externalIds.byAccount(account.account().id());
    return new AutoValue_AccountState(
        account.account(),
        extIds,
        ExternalId.getUserName(extIds),
        account.projectWatches(),
        Optional.of(defaultConfig),
        Optional.of(account.preferences()));
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

  /** Creates an AccountState instance containing the given data. */
  public static AccountState withState(
      Account account,
      ImmutableSet<ExternalId> externalIds,
      Optional<String> userName,
      ImmutableMap<ProjectWatches.ProjectWatchKey, ImmutableSet<NotifyType>> projectWatches,
      Optional<CachedPreferences> defaultPreferences,
      Optional<CachedPreferences> userPreferences) {
    return new AutoValue_AccountState(
        account, externalIds, userName, projectWatches, defaultPreferences, userPreferences);
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
    return CachedPreferences.general(
        defaultPreferences(), userPreferences().orElse(CachedPreferences.EMPTY));
  }

  /** The diff preferences of the account. */
  public DiffPreferencesInfo diffPreferences() {
    return CachedPreferences.diff(
        defaultPreferences(), userPreferences().orElse(CachedPreferences.EMPTY));
  }

  /** The edit preferences of the account. */
  public EditPreferencesInfo editPreferences() {
    return CachedPreferences.edit(
        defaultPreferences(), userPreferences().orElse(CachedPreferences.EMPTY));
  }

  @Override
  public final String toString() {
    MoreObjects.ToStringHelper h = MoreObjects.toStringHelper(this);
    h.addValue(account().id());
    return h.toString();
  }

  /** Gerrit's default preferences as stored in {@code preferences.config}. */
  public abstract Optional<CachedPreferences> defaultPreferences();

  /** User preferences as stored in {@code preferences.config}. */
  public abstract Optional<CachedPreferences> userPreferences();
}
