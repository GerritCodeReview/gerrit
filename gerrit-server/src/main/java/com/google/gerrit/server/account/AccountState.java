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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_MAILTO;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.CurrentUser.PropertyKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AccountState {
  public static final Function<AccountState, Account.Id> ACCOUNT_ID_FUNCTION =
      a -> a.getAccount().getId();

  private final Account account;
  private final Set<AccountGroup.UUID> internalGroups;
  private final Collection<AccountExternalId> externalIds;
  private final Map<ProjectWatchKey, Set<NotifyType>> projectWatches;
  private Cache<IdentifiedUser.PropertyKey<Object>, Object> properties;

  public AccountState(
      Account account,
      Set<AccountGroup.UUID> actualGroups,
      Collection<AccountExternalId> externalIds,
      Map<ProjectWatchKey, Set<NotifyType>> projectWatches) {
    this.account = account;
    this.internalGroups = actualGroups;
    this.externalIds = externalIds;
    this.projectWatches = projectWatches;
    this.account.setUserName(getUserName(externalIds));
  }

  /** Get the cached account metadata. */
  public Account getAccount() {
    return account;
  }

  /**
   * Get the username, if one has been declared for this user.
   *
   * <p>The username is the {@link AccountExternalId} using the scheme {@link
   * AccountExternalId#SCHEME_USERNAME}.
   */
  public String getUserName() {
    return account.getUserName();
  }

  /** @return the password matching the requested username; or null. */
  public String getPassword(String username) {
    for (AccountExternalId id : getExternalIds()) {
      if (id.isScheme(AccountExternalId.SCHEME_USERNAME) && username.equals(id.getSchemeRest())) {
        return id.getPassword();
      }
    }
    return null;
  }

  /** The external identities that identify the account holder. */
  public Collection<AccountExternalId> getExternalIds() {
    return externalIds;
  }

  /** The project watches of the account. */
  public Map<ProjectWatchKey, Set<NotifyType>> getProjectWatches() {
    return projectWatches;
  }

  /** The set of groups maintained directly within the Gerrit database. */
  public Set<AccountGroup.UUID> getInternalGroups() {
    return internalGroups;
  }

  public static String getUserName(Collection<AccountExternalId> ids) {
    for (AccountExternalId id : ids) {
      if (id.isScheme(SCHEME_USERNAME)) {
        return id.getSchemeRest();
      }
    }
    return null;
  }

  public static Set<String> getEmails(Collection<AccountExternalId> ids) {
    Set<String> emails = new HashSet<>();
    for (AccountExternalId id : ids) {
      if (id.isScheme(SCHEME_MAILTO)) {
        emails.add(id.getSchemeRest());
      }
    }
    return emails;
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
