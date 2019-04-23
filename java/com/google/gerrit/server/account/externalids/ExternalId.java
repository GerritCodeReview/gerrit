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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.HashedPassword;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class ExternalId implements Serializable {
  // If these regular expressions are modified the same modifications should be done to the
  // corresponding regular expressions in the
  // com.google.gerrit.client.account.UsernameField class.
  private static final String USER_NAME_PATTERN_FIRST_REGEX = "[a-zA-Z0-9]";
  private static final String USER_NAME_PATTERN_REST_REGEX = "[a-zA-Z0-9.!#$%&’*+=?^_`\\{|\\}~@-]";
  private static final String USER_NAME_PATTERN_LAST_REGEX = "[a-zA-Z0-9]";

  /** Regular expression that a username must match. */
  private static final String USER_NAME_PATTERN_REGEX =
      "^("
          + //
          USER_NAME_PATTERN_FIRST_REGEX
          + //
          USER_NAME_PATTERN_REST_REGEX
          + "*"
          + //
          USER_NAME_PATTERN_LAST_REGEX
          + //
          "|"
          + //
          USER_NAME_PATTERN_FIRST_REGEX
          + //
          ")$";

  private static final Pattern USER_NAME_PATTERN = Pattern.compile(USER_NAME_PATTERN_REGEX);

  public static boolean isValidUsername(String username) {
    return USER_NAME_PATTERN.matcher(username).matches();
  }

  /**
   * Returns the ID of the first external ID from the provided external IDs that has the {@link
   * ExternalId#SCHEME_USERNAME} scheme.
   *
   * @param extIds external IDs
   * @return the ID of the first external ID from the provided external IDs that has the {@link
   *     ExternalId#SCHEME_USERNAME} scheme
   */
  public static Optional<String> getUserName(Collection<ExternalId> extIds) {
    return extIds.stream()
        .filter(e -> e.isScheme(SCHEME_USERNAME))
        .map(e -> e.key().id())
        .filter(u -> !Strings.isNullOrEmpty(u))
        .findFirst();
  }

  /**
   * Returns all IDs of the provided external IDs that have the {@link ExternalId#SCHEME_MAILTO}
   * scheme as a distinct stream.
   *
   * @param extIds external IDs
   * @return distinct stream of all IDs of the provided external IDs that have the {@link
   *     ExternalId#SCHEME_MAILTO} scheme
   */
  public static Stream<String> getEmails(Collection<ExternalId> extIds) {
    return extIds.stream().filter(e -> e.isScheme(SCHEME_MAILTO)).map(e -> e.key().id()).distinct();
  }

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
   *
   * <p>Scheme names must not contain colons (':').
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

    /**
     * Creates an external ID key.
     *
     * @param scheme the scheme name, must not contain colons (':'), can be {@code null}
     * @param id the external ID, must not contain colons (':')
     * @return the created external ID key
     */
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
    @SuppressWarnings("deprecation") // Use Hashing.sha1 for compatibility.
    public ObjectId sha1() {
      return ObjectId.fromRaw(Hashing.sha1().hashString(get(), UTF_8).asBytes());
    }

    /**
     * Exports this external ID key as string with the format "scheme:id", or "id" if scheme is
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

    public static ImmutableSet<ExternalId.Key> from(Collection<ExternalId> extIds) {
      return extIds.stream().map(ExternalId::key).collect(toImmutableSet());
    }
  }

  /**
   * Creates an external ID.
   *
   * @param scheme the scheme name, must not contain colons (':')
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @return the created external ID
   */
  public static ExternalId create(String scheme, String id, Account.Id accountId) {
    return create(Key.create(scheme, id), accountId, null, null);
  }

  /**
   * Creates an external ID.
   *
   * @param scheme the scheme name, must not contain colons (':')
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @param hashedPassword the hashed password of the external ID, may be {@code null}
   * @return the created external ID
   */
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
    return create(
        key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword), null);
  }

  public static ExternalId createWithPassword(
      Key key, Account.Id accountId, @Nullable String email, @Nullable String plainPassword) {
    plainPassword = Strings.emptyToNull(plainPassword);
    String hashedPassword =
        plainPassword != null ? HashedPassword.fromPassword(plainPassword).encode() : null;
    return create(key, accountId, email, hashedPassword);
  }

  /**
   * Create a external ID for a username (scheme "username").
   *
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @param plainPassword the plain HTTP password, may be {@code null}
   * @return the created external ID
   */
  public static ExternalId createUsername(
      String id, Account.Id accountId, @Nullable String plainPassword) {
    return createWithPassword(Key.create(SCHEME_USERNAME, id), accountId, null, plainPassword);
  }

  /**
   * Creates an external ID with an email.
   *
   * @param scheme the scheme name, must not contain colons (':')
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @return the created external ID
   */
  public static ExternalId createWithEmail(
      String scheme, String id, Account.Id accountId, @Nullable String email) {
    return createWithEmail(Key.create(scheme, id), accountId, email);
  }

  public static ExternalId createWithEmail(Key key, Account.Id accountId, @Nullable String email) {
    return create(key, accountId, Strings.emptyToNull(email), null);
  }

  public static ExternalId createEmail(Account.Id accountId, String email) {
    return createWithEmail(SCHEME_MAILTO, email, accountId, requireNonNull(email));
  }

  static ExternalId create(ExternalId extId, @Nullable ObjectId blobId) {
    return new AutoValue_ExternalId(
        extId.key(), extId.accountId(), extId.email(), extId.password(), blobId);
  }

  @VisibleForTesting
  public static ExternalId create(
      Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword,
      @Nullable ObjectId blobId) {
    return new AutoValue_ExternalId(
        key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword), blobId);
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
  public static ExternalId parse(String noteId, byte[] raw, ObjectId blobId)
      throws ConfigInvalidException {
    Config externalIdConfig = new Config();
    try {
      externalIdConfig.fromText(new String(raw, UTF_8));
    } catch (ConfigInvalidException e) {
      throw invalidConfig(noteId, e.getMessage());
    }

    return parse(noteId, externalIdConfig, blobId);
  }

  public static ExternalId parse(String noteId, Config externalIdConfig, ObjectId blobId)
      throws ConfigInvalidException {
    requireNonNull(blobId);

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

    return create(
        externalIdKey,
        Account.id(accountId),
        Strings.emptyToNull(email),
        Strings.emptyToNull(password),
        blobId);
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
      if (accountId < 0) {
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

  /**
   * ID of the note blob in the external IDs branch that stores this external ID. {@code null} if
   * the external ID was created in code and is not yet stored in Git.
   */
  public abstract @Nullable ObjectId blobId();

  public void checkThatBlobIdIsSet() {
    checkState(blobId() != null, "No blob ID set for external ID %s", key().get());
  }

  public boolean isScheme(String scheme) {
    return key().isScheme(scheme);
  }

  public byte[] toByteArray() {
    checkState(blobId() != null, "Missing blobId in external ID %s", key().get());
    byte[] b = new byte[2 * Constants.OBJECT_ID_STRING_LENGTH + 1];
    key().sha1().copyTo(b, 0);
    b[Constants.OBJECT_ID_STRING_LENGTH] = ':';
    blobId().copyTo(b, Constants.OBJECT_ID_STRING_LENGTH + 1);
    return b;
  }

  /**
   * For checking if two external IDs are equals the blobId is excluded and external IDs that have
   * different blob IDs but identical other fields are considered equal. This way an external ID
   * that was loaded from Git can be equal with an external ID that was created from code.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ExternalId)) {
      return false;
    }
    ExternalId o = (ExternalId) obj;
    return Objects.equals(key(), o.key())
        && Objects.equals(accountId(), o.accountId())
        && Objects.equals(email(), o.email())
        && Objects.equals(password(), o.password());
  }

  @Override
  public int hashCode() {
    return Objects.hash(key(), accountId(), email(), password());
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
    } else {
      c.unset(EXTERNAL_ID_SECTION, externalIdKey, EMAIL_KEY);
    }

    if (password() != null) {
      c.setString(EXTERNAL_ID_SECTION, externalIdKey, PASSWORD_KEY, password());
    } else {
      c.unset(EXTERNAL_ID_SECTION, externalIdKey, PASSWORD_KEY);
    }
  }
}
