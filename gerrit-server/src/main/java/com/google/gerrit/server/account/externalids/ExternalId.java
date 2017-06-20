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

package com.google.gerrit.server.account.externalids;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.HashedPassword;
import java.io.Serializable;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class ExternalId implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final String EXTERNAL_ID_SECTION = "externalId";
  private static final String ACCOUNT_ID_KEY = "accountId";
  private static final String EMAIL_KEY = "email";
  private static final String PASSWORD_KEY = "password";

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
  public abstract static class Key implements Serializable {
    private static final long serialVersionUID = 1L;

    public static Key create(@Nullable String scheme, String id) {
      return new AutoValue_ExternalId_Key(Strings.emptyToNull(scheme), id);
    }

    /**
     * Parses an external ID key from a string in the format "scheme:id" or "id".
     *
     * @return the parsed external ID key
     */
    public static Key parse(String externalId) {
      int c = externalId.indexOf(':');
      if (c < 1 || c >= externalId.length() - 1) {
        return create(null, externalId);
      }
      return create(externalId.substring(0, c), externalId.substring(c + 1));
    }

    public abstract @Nullable String scheme();

    public abstract String id();

    public boolean isScheme(String scheme) {
      return scheme.equals(scheme());
    }

    /**
     * Returns the SHA1 of the external ID that is used as note ID in the refs/meta/external-ids
     * notes branch.
     */
    public ObjectId sha1() {
      return ObjectId.fromRaw(Hashing.murmur3_128().hashString(get(), UTF_8).asBytes());
    }

    /**
     * Exports this external ID key as string with the format "scheme:id", or "id" id scheme is
     * null.
     *
     * <p>This string representation is used as subsection name in the Git config file that stores
     * the external ID.
     */
    public String get() {
      if (scheme() != null) {
        return scheme() + ":" + id();
      }
      return id();
    }

    @Override
    public String toString() {
      return get();
    }
  }

  public static ExternalId create(String scheme, String id, Account.Id accountId) {
    return new AutoValue_ExternalId(Key.create(scheme, id), accountId, null, null);
  }

  public static ExternalId create(
      String scheme,
      String id,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword) {
    return create(Key.create(scheme, id), accountId, email, hashedPassword);
  }

  public static ExternalId create(Key key, Account.Id accountId) {
    return create(key, accountId, null, null);
  }

  public static ExternalId create(
      Key key, Account.Id accountId, @Nullable String email, @Nullable String hashedPassword) {
    return new AutoValue_ExternalId(
        key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword));
  }

  public static ExternalId createWithPassword(
      Key key, Account.Id accountId, @Nullable String email, String plainPassword) {
    plainPassword = Strings.emptyToNull(plainPassword);
    String hashedPassword =
        plainPassword != null ? HashedPassword.fromPassword(plainPassword).encode() : null;
    return create(key, accountId, email, hashedPassword);
  }

  public static ExternalId createUsername(String id, Account.Id accountId, String plainPassword) {
    return createWithPassword(Key.create(SCHEME_USERNAME, id), accountId, null, plainPassword);
  }

  public static ExternalId createWithEmail(
      String scheme, String id, Account.Id accountId, @Nullable String email) {
    return createWithEmail(Key.create(scheme, id), accountId, email);
  }

  public static ExternalId createWithEmail(Key key, Account.Id accountId, @Nullable String email) {
    return new AutoValue_ExternalId(key, accountId, Strings.emptyToNull(email), null);
  }

  public static ExternalId createEmail(Account.Id accountId, String email) {
    return createWithEmail(SCHEME_MAILTO, email, accountId, checkNotNull(email));
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
              "Expected exactly 1 '%s' section, found %d",
              EXTERNAL_ID_SECTION, externalIdKeys.size()));
    }

    String externalIdKeyStr = Iterables.getOnlyElement(externalIdKeys);
    Key externalIdKey = Key.parse(externalIdKeyStr);
    if (externalIdKey == null) {
      throw invalidConfig(noteId, String.format("External ID %s is invalid", externalIdKeyStr));
    }

    if (!externalIdKey.sha1().getName().equals(noteId)) {
      throw invalidConfig(
          noteId,
          String.format(
              "SHA1 of external ID '%s' does not match note ID '%s'", externalIdKeyStr, noteId));
    }

    String email = externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, EMAIL_KEY);
    String password =
        externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, PASSWORD_KEY);
    int accountId = readAccountId(noteId, externalIdConfig, externalIdKeyStr);

    return new AutoValue_ExternalId(
        externalIdKey,
        new Account.Id(accountId),
        Strings.emptyToNull(email),
        Strings.emptyToNull(password));
  }

  private static int readAccountId(String noteId, Config externalIdConfig, String externalIdKeyStr)
      throws ConfigInvalidException {
    String accountIdStr =
        externalIdConfig.getString(EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY);
    if (accountIdStr == null) {
      throw invalidConfig(
          noteId,
          String.format(
              "Value for '%s.%s.%s' is missing, expected account ID",
              EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY));
    }

    try {
      int accountId =
          externalIdConfig.getInt(EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY, -1);
      if (accountId <= 0) {
        throw invalidConfig(
            noteId,
            String.format(
                "Value %s for '%s.%s.%s' is invalid, expected account ID",
                accountIdStr, EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY));
      }
      return accountId;
    } catch (IllegalArgumentException e) {
      throw invalidConfig(
          noteId,
          String.format(
              "Value %s for '%s.%s.%s' is invalid, expected account ID",
              accountIdStr, EXTERNAL_ID_SECTION, externalIdKeyStr, ACCOUNT_ID_KEY));
    }
  }

  private static ConfigInvalidException invalidConfig(String noteId, String message) {
    return new ConfigInvalidException(
        String.format("Invalid external ID config for note '%s': %s", noteId, message));
  }

  public abstract Key key();

  public abstract Account.Id accountId();

  public abstract @Nullable String email();

  public abstract @Nullable String password();

  public boolean isScheme(String scheme) {
    return key().isScheme(scheme);
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
    Config c = new Config();
    writeToConfig(c);
    return c.toText();
  }

  public void writeToConfig(Config c) {
    String externalIdKey = key().get();
    // Do not use c.setInt(...) to write the account ID because c.setInt(...) persists integers
    // that can be expressed in KiB as a unit strings, e.g. "1024000" is stored as "100k". Using
    // c.setString(...) ensures that account IDs are human readable.
    c.setString(
        EXTERNAL_ID_SECTION, externalIdKey, ACCOUNT_ID_KEY, Integer.toString(accountId().get()));
    if (email() != null) {
      c.setString(EXTERNAL_ID_SECTION, externalIdKey, EMAIL_KEY, email());
    }
    if (password() != null) {
      c.setString(EXTERNAL_ID_SECTION, externalIdKey, PASSWORD_KEY, password());
    }
  }
}
