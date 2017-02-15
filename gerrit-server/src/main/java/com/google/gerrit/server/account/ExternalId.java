// Copyright (C) 2016 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import java.util.Collection;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class ExternalId {
  private static final String EXTERNAL_ID_SECTION = "externalId";
  private static final String ACCOUNT_ID_KEY = "accountId";
  private static final String EMAIL_KEY = "email";
  private static final String PASSWORD_KEY = "password";

  // TODO(ekempin): Remove this once plain passwords are gone
  private static final String PLAIN_PASSWORD_KEY = "plainPassword";

  /**
   * Scheme used for {@link AuthType#LDAP}, {@link AuthType#CLIENT_SSL_CERT_LDAP}, {@link
   * AuthType#HTTP_LDAP}, and {@link AuthType#LDAP_BIND} usernames.
   *
   * <p>The name {@code gerrit:} was a very poor choice.
   */
  public static final String SCHEME_GERRIT = "gerrit";

  /** Scheme used for randomly created identities constructed by a UUID. */
  public static final String SCHEME_UUID = "uuid";

  /** Scheme used to represent only an email address. */
  public static final String SCHEME_MAILTO = "mailto";

  /** Scheme for the username used to authenticate an account, e.g. over SSH. */
  public static final String SCHEME_USERNAME = "username";

  /** Scheme used for GPG public keys. */
  public static final String SCHEME_GPGKEY = "gpgkey";

  /** Scheme for external auth used during authentication, e.g. OAuth Token */
  public static final String SCHEME_EXTERNAL = "external";

  @AutoValue
  public abstract static class Key {
    public static Key create(String scheme, String id) {
      return new AutoValue_ExternalId_Key(scheme, id);
    }

    public static ExternalId.Key from(AccountExternalId.Key externalIdKey) {
      return parse(externalIdKey.get());
    }

    /**
     * Parses an external ID key from a string in the format "scheme:id".
     *
     * @return the parsed external ID key, {@code null} if the given external Id has an invalid
     *     format.
     */
    @Nullable
    public static Key parse(String externalId) {
      int c = externalId.indexOf(':');
      if (c < 1 || c >= externalId.length() - 1) {
        return null;
      }
      return create(externalId.substring(0, c), externalId.substring(c + 1));
    }

    public static Set<AccountExternalId.Key> toAccountExternalIdKeys(
        Collection<ExternalId.Key> extIdKeys) {
      return extIdKeys.stream().map(k -> k.asAccountExternalIdKey()).collect(toSet());
    }

    public abstract String scheme();

    public abstract String id();

    public boolean isScheme(String scheme) {
      return scheme.equals(scheme());
    }

    public AccountExternalId.Key asAccountExternalIdKey() {
      return new AccountExternalId.Key(scheme(), id());
    }

    /**
     * Returns the SHA1 of the external ID that is used as note ID in the refs/meta/external-ids
     * notes branch.
     */
    public ObjectId sha1() {
      return ObjectId.fromString(
          Hashing.sha1().newHasher().putString(toString(), UTF_8).hash().toString());
    }

    /**
     * Exports this external ID key as string with the format "scheme:id".
     *
     * <p>This string representation is used as subsection name in the Git config file that stores
     * the external ID.
     */
    public String get() {
      return scheme() + ":" + id();
    }

    @Override
    public String toString() {
      return get();
    }
  }

  public static ExternalId create(String scheme, String id, Account.Id accountId) {
    return new AutoValue_ExternalId(Key.create(scheme, id), accountId, null, null, null);
  }

  public static ExternalId create(
      String scheme,
      String id,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String password) {
    return create(Key.create(scheme, id), accountId, email, password);
  }

  public static ExternalId create(Key key, Account.Id accountId) {
    return create(key, accountId, null, null);
  }

  public static ExternalId create(
      Key key, Account.Id accountId, @Nullable String email, @Nullable String password) {
    password = Strings.emptyToNull(password);
    String hashedPassword =
        password != null ? HashedPassword.fromPassword(password).encode() : null;
    return new AutoValue_ExternalId(key, accountId, email, hashedPassword, password);
  }

  public static ExternalId createWithPassword(
      String scheme, String id, Account.Id accountId, String password) {
    return create(Key.create(scheme, id), accountId, null, password);
  }

  public static ExternalId createUsername(String id, Account.Id accountId, String password) {
    return createWithPassword(SCHEME_USERNAME, id, accountId, password);
  }

  public static ExternalId createWithEmail(
      String scheme, String id, Account.Id accountId, String email) {
    return createWithEmail(Key.create(scheme, id), accountId, email);
  }

  public static ExternalId createWithEmail(Key key, Account.Id accountId, String email) {
    return new AutoValue_ExternalId(key, accountId, email, null, null);
  }

  public static ExternalId createEmail(Account.Id accountId, String email) {
    return createWithEmail(SCHEME_MAILTO, email, accountId, email);
  }

  /**
   * Parses an external ID from a byte array that contain the external ID as an Git config file
   * text.
   *
   * <p>The Git config must have exactly one externalId subsection with an accountId and optionally
   * email and password:
   *
   * <pre>
   * [externalId "username:jdoe"]
   *   accountId = 1003407
   *   email = jdoe@example.com
   *   password = bcrypt:4:LCbmSBDivK/hhGVQMfkDpA==:XcWn0pKYSVU/UJgOvhidkEtmqCp6oKB7
   * </pre>
   */
  public static ExternalId parse(String noteId, byte[] raw) throws ConfigInvalidException {
    Config externalIdConfig = new Config();
    try {
      externalIdConfig.fromText(new String(raw, UTF_8));
    } catch (ConfigInvalidException e) {
      throw invalidConfig(noteId, e.getMessage());
    }

    Set<String> externalIdKeys = externalIdConfig.getSubsections(EXTERNAL_ID_SECTION);
    if (externalIdKeys.size() != 1) {
      throw invalidConfig(
          noteId,
          String.format(
              "Expected exactly 1 %s section, found %d",
              EXTERNAL_ID_SECTION, externalIdKeys.size()));
    }

    String externalIdKeyStr = Iterables.getOnlyElement(externalIdKeys);
    Key externalIdKey = Key.parse(externalIdKeyStr);
    if (externalIdKey == null) {
      throw invalidConfig(noteId, String.format("Invalid external id: %s", externalIdKeyStr));
    }

    String accountIdStr =
        externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY);
    String email = externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, EMAIL_KEY);
    String password =
        externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, PASSWORD_KEY);
    String plainPassword =
        externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, PLAIN_PASSWORD_KEY);
    if (accountIdStr == null) {
      throw invalidConfig(
          noteId,
          String.format(
              "Missing value for %s.%s.%s", EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY));
    }
    Integer accountId = Ints.tryParse(accountIdStr);
    if (accountId == null) {
      throw invalidConfig(
          noteId,
          String.format(
              "Value %s for %s.%s.%s is invalid, expected account ID",
              accountIdStr, EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY));
    }

    return new AutoValue_ExternalId(
        externalIdKey, new Account.Id(accountId), email, password, plainPassword);
  }

  private static ConfigInvalidException invalidConfig(String noteId, String message) {
    return new ConfigInvalidException(
        String.format("Invalid external id config for note %s: %s", noteId, message));
  }

  public static ExternalId from(AccountExternalId externalId) {
    if (externalId == null) {
      return null;
    }

    return new AutoValue_ExternalId(
        ExternalId.Key.create(externalId.getKey().getScheme(), externalId.getSchemeRest()),
        externalId.getAccountId(),
        externalId.getEmailAddress(),
        externalId.getHashedPassword(),
        externalId.getPassword());
  }

  public static Set<ExternalId> from(Collection<AccountExternalId> externalIds) {
    if (externalIds == null) {
      return ImmutableSet.of();
    }
    return externalIds.stream().map(ExternalId::from).collect(toSet());
  }

  public static Set<AccountExternalId> toAccountExternalIds(Collection<ExternalId> extIds) {
    return extIds.stream().map(e -> e.asAccountExternalId()).collect(toSet());
  }

  public abstract Key key();

  public abstract Account.Id accountId();

  public abstract @Nullable String email();

  public abstract @Nullable String password();

  // TODO(ekempin): Remove this once plain passwords are gone
  public abstract @Nullable String plainPassword();

  public AccountExternalId asAccountExternalId() {
    AccountExternalId extId = new AccountExternalId(accountId(), key().asAccountExternalIdKey());
    extId.setEmailAddress(email());
    extId.setPassword(plainPassword());
    extId.setHashedPassword(password());
    return extId;
  }

  /**
   * Exports this external ID as Git config file text.
   *
   * <p>The Git config has exactly one externalId subsection with an accountId and optionally email
   * and password:
   *
   * <pre>
   * [externalId "username:jdoe"]
   *   accountId = 1003407
   *   email = jdoe@example.com
   *   password = bcrypt:4:LCbmSBDivK/hhGVQMfkDpA==:XcWn0pKYSVU/UJgOvhidkEtmqCp6oKB7
   * </pre>
   */
  @Override
  public String toString() {
    Config externalIdConfig = new Config();

    String externalIdKey = key().get();
    externalIdConfig.setInt(EXTERNAL_ID_SECTION, externalIdKey, ACCOUNT_ID_KEY, accountId().get());
    if (email() != null) {
      externalIdConfig.setString(EXTERNAL_ID_SECTION, externalIdKey, EMAIL_KEY, email());
    }
    if (password() != null) {
      externalIdConfig.setString(EXTERNAL_ID_SECTION, externalIdKey, PASSWORD_KEY, password());
    }
    if (plainPassword() != null) {
      externalIdConfig.setString(
          EXTERNAL_ID_SECTION, externalIdKey, PLAIN_PASSWORD_KEY, plainPassword());
    }

    return externalIdConfig.toText();
  }
}
