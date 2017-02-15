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

import static com.google.gerrit.server.account.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser.PropertyKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.codec.DecoderException;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountState {
  public static final Function<AccountState, Account.Id> ACCOUNT_ID_FUNCTION =
      a -> a.getAccount().getId();
  private static final Logger logger = LoggerFactory.getLogger(AccountState.class);

  private final Account account;
  private final Set<AccountGroup.UUID> internalGroups;
  private final Collection<ExternalId> externalIds;
  private final Map<ProjectWatchKey, Set<NotifyType>> projectWatches;
  private Cache<IdentifiedUser.PropertyKey<Object>, Object> properties;

  public AccountState(
      Account account,
      Set<AccountGroup.UUID> actualGroups,
      Collection<ExternalId> externalIds,
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
      if (!id.key().isScheme(SCHEME_USERNAME) || !username.equals(id.key().id())) {
        continue;
      }

      String hashedStr = id.password();
      if (!Strings.isNullOrEmpty(hashedStr)) {
        try {
          return HashedPassword.decode(hashedStr).checkPassword(password);
        } catch (DecoderException e) {
          logger.error("DecoderException for user " + username, e);
          return false;
        }
      }

      // TODO(ekempin): Remove this once plain passwords are gone
      String want = id.plainPassword();
      if (!Strings.isNullOrEmpty(want)) {
        byte wantBytes[] = want.getBytes();
        byte gotBytes[] = password.getBytes();
        // Constant-time comparison.
        return Arrays.areEqual(wantBytes, gotBytes);
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

  /** The set of groups maintained directly within the Gerrit database. */
  public Set<AccountGroup.UUID> getInternalGroups() {
    return internalGroups;
  }

  public static String getUserName(Collection<ExternalId> ids) {
    for (ExternalId extId : ids) {
      if (extId.key().isScheme(SCHEME_USERNAME)) {
        return extId.key().id();
      }
    }
    return null;
  }

  public static Set<String> getEmails(Collection<ExternalId> ids) {
    Set<String> emails = new HashSet<>();
    for (ExternalId extId : ids) {
      if (extId.key().isScheme(SCHEME_MAILTO)) {
        emails.add(extId.key().id());
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
