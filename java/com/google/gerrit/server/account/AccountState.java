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

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser.PropertyKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.commons.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superset of all information related to an Account. This includes external IDs, project watches,
 * and properties from the account config file. AccountState maps one-to-one to Account.
 */
public class AccountState {
  private static final Logger logger = LoggerFactory.getLogger(AccountState.class);

  public static final Function<AccountState, Account.Id> ACCOUNT_ID_FUNCTION =
      a -> a.getAccount().getId();

  private final AllUsersName allUsersName;
  private final Account account;
  private final Collection<ExternalId> externalIds;
  private final Map<ProjectWatchKey, Set<NotifyType>> projectWatches;
  private final GeneralPreferencesInfo generalPreferences;
  private Cache<IdentifiedUser.PropertyKey<Object>, Object> properties;

  public AccountState(
      AllUsersName allUsersName,
      Account account,
      Collection<ExternalId> externalIds,
      Map<ProjectWatchKey, Set<NotifyType>> projectWatches,
      GeneralPreferencesInfo generalPreferences) {
    this.allUsersName = allUsersName;
    this.account = account;
    this.externalIds = externalIds;
    this.projectWatches = projectWatches;
    this.generalPreferences = generalPreferences;
    this.account.setUserName(ExternalId.getUserName(externalIds).orElse(null));
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
   */
  public String getUserName() {
    return account.getUserName();
  }

  public boolean checkPassword(String password, String username) {
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
  public Collection<ExternalId> getExternalIds() {
    return externalIds;
  }

  /** The project watches of the account. */
  public Map<ProjectWatchKey, Set<NotifyType>> getProjectWatches() {
    return projectWatches;
  }

  /** The general preferences of the account. */
  public GeneralPreferencesInfo getGeneralPreferences() {
    return generalPreferences;
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
