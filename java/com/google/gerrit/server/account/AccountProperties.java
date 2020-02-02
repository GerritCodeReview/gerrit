// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Parses/writes account properties from/to a {@link Config} file.
 *
 * <p>This is a low-level API. Read/write of account properties in a user branch should be done
 * through {@link AccountsUpdate} or {@link AccountConfig}.
 *
 * <p>The config file has one 'account' section with the properties of the account:
 *
 * <pre>
 *   [account]
 *     active = false
 *     fullName = John Doe
 *     displayName = John
 *     preferredEmail = john.doe@foo.com
 *     status = Overloaded with reviews
 * </pre>
 *
 * <p>All keys are optional.
 *
 * <p>Not setting a key and setting a key to an empty string are treated the same way and result in
 * a {@code null} value.
 *
 * <p>If no value for 'active' is specified, by default the account is considered as active.
 *
 * <p>The account is lazily parsed.
 */
public class AccountProperties {
  public static final String ACCOUNT_CONFIG = "account.config";
  public static final String ACCOUNT = "account";
  public static final String KEY_ACTIVE = "active";
  public static final String KEY_FULL_NAME = "fullName";
  public static final String KEY_DISPLAY_NAME = "displayName";
  public static final String KEY_PREFERRED_EMAIL = "preferredEmail";
  public static final String KEY_STATUS = "status";

  private final Account.Id accountId;
  private final Timestamp registeredOn;
  private final Config accountConfig;
  private @Nullable ObjectId metaId;
  private Account account;

  AccountProperties(
      Account.Id accountId,
      Timestamp registeredOn,
      Config accountConfig,
      @Nullable ObjectId metaId) {
    this.accountId = accountId;
    this.registeredOn = registeredOn;
    this.accountConfig = accountConfig;
    this.metaId = metaId;
  }

  Account getAccount() {
    if (account == null) {
      parse();
    }
    return account;
  }

  public Timestamp getRegisteredOn() {
    return registeredOn;
  }

  void setMetaId(@Nullable ObjectId metaId) {
    this.metaId = metaId;
    this.account = null;
  }

  private void parse() {
    Account.Builder accountBuilder = Account.builder(accountId, registeredOn);
    accountBuilder.setActive(accountConfig.getBoolean(ACCOUNT, null, KEY_ACTIVE, true));
    accountBuilder.setFullName(get(accountConfig, KEY_FULL_NAME));
    accountBuilder.setDisplayName(get(accountConfig, KEY_DISPLAY_NAME));

    String preferredEmail = get(accountConfig, KEY_PREFERRED_EMAIL);
    accountBuilder.setPreferredEmail(preferredEmail);

    accountBuilder.setStatus(get(accountConfig, KEY_STATUS));
    accountBuilder.setMetaId(metaId != null ? metaId.name() : null);
    account = accountBuilder.build();
  }

  Config save(InternalAccountUpdate accountUpdate) {
    writeToAccountConfig(accountUpdate, accountConfig);
    return accountConfig;
  }

  public static void writeToAccountConfig(InternalAccountUpdate accountUpdate, Config cfg) {
    accountUpdate.getActive().ifPresent(active -> setActive(cfg, active));
    accountUpdate.getFullName().ifPresent(fullName -> set(cfg, KEY_FULL_NAME, fullName));
    accountUpdate
        .getDisplayName()
        .ifPresent(displayName -> set(cfg, KEY_DISPLAY_NAME, displayName));
    accountUpdate
        .getPreferredEmail()
        .ifPresent(preferredEmail -> set(cfg, KEY_PREFERRED_EMAIL, preferredEmail));
    accountUpdate.getStatus().ifPresent(status -> set(cfg, KEY_STATUS, status));
  }

  /**
   * Gets the given key from the given config.
   *
   * <p>Empty values are returned as {@code null}
   *
   * @param cfg the config
   * @param key the key
   * @return the value, {@code null} if key was not set or key was set to empty string
   */
  private static String get(Config cfg, String key) {
    return Strings.emptyToNull(cfg.getString(ACCOUNT, null, key));
  }

  /**
   * Sets/Unsets {@code account.active} in the given config.
   *
   * <p>{@code account.active} is set to {@code false} if the account is inactive.
   *
   * <p>If the account is active {@code account.active} is unset since {@code true} is the default
   * if this field is missing.
   *
   * @param cfg the config
   * @param value whether the account is active
   */
  private static void setActive(Config cfg, boolean value) {
    if (!value) {
      cfg.setBoolean(ACCOUNT, null, KEY_ACTIVE, false);
    } else {
      cfg.unset(ACCOUNT, null, KEY_ACTIVE);
    }
  }

  /**
   * Sets/Unsets the given key in the given config.
   *
   * <p>The key unset if the value is {@code null}.
   *
   * @param cfg the config
   * @param key the key
   * @param value the value
   */
  private static void set(Config cfg, String key, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      cfg.setString(ACCOUNT, null, key, value);
    } else {
      cfg.unset(ACCOUNT, null, key);
    }
  }
}
