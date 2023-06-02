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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.AuthType;
import java.io.Serializable;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
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

  static final String EXTERNAL_ID_SECTION = "externalId";
  static final String ACCOUNT_ID_KEY = "accountId";
  static final String EMAIL_KEY = "email";
  static final String PASSWORD_KEY = "password";

  /**
   * Scheme used to label accounts created, when using the LDAP-based authentication types {@link
   * AuthType#LDAP}, {@link AuthType#CLIENT_SSL_CERT_LDAP}, {@link AuthType#HTTP_LDAP}, and {@link
   * AuthType#LDAP_BIND}. The external ID stores the username. Accounts with such an external ID
   * will be authenticated against the configured LDAP identity provider.
   *
   * <p>The name {@code gerrit:} was a very poor choice.
   *
   * <p>Scheme names must not contain colons (':').
   *
   * <p>Will be handled case insensitive, if auth.userNameCaseInsensitive = true.
   */
  public static final String SCHEME_GERRIT = "gerrit";

  /** Scheme used for randomly created identities constructed by a UUID. */
  public static final String SCHEME_UUID = "uuid";

  /** Scheme used to represent only an email address. */
  public static final String SCHEME_MAILTO = "mailto";

  /**
   * Scheme for the username used to authenticate an account, e.g. over SSH.
   *
   * <p>Will be handled case insensitive, if auth.userNameCaseInsensitive = true.
   */
  public static final String SCHEME_USERNAME = "username";

  /** Scheme used for GPG public keys. */
  public static final String SCHEME_GPGKEY = "gpgkey";

  /** Scheme for imported accounts from other servers with different GerritServerId */
  public static final String SCHEME_IMPORTED = "imported";

  /** Scheme for external auth used during authentication, e.g. OAuth Token */
  public static final String SCHEME_EXTERNAL = "external";

  /** Scheme for http resources. OpenID in particular makes use of these external IDs. */
  public static final String SCHEME_HTTP = "http";

  /** Scheme for https resources. OpenID in particular makes use of these external IDs. */
  public static final String SCHEME_HTTPS = "https";

  /** Scheme for xri resources. OpenID in particular makes use of these external IDs. */
  public static final String SCHEME_XRI = "xri";

  /** Scheme for Google OAuth external IDs. */
  public static final String SCHEME_GOOGLE_OAUTH = "google-oauth";

  @AutoValue
  public abstract static class Key implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an external ID key.
     *
     * @param scheme the scheme name, must not contain colons (':'), can be {@code null}
     * @param id the external ID, must not contain colons (':')
     * @param isCaseInsensitive whether the external ID key is matched case insensitively
     * @return the created external ID key
     */
    @VisibleForTesting
    public static Key create(@Nullable String scheme, String id, boolean isCaseInsensitive) {
      return new AutoValue_ExternalId_Key(Strings.emptyToNull(scheme), id, isCaseInsensitive);
    }

    /**
     * Parses an external ID key from a string in the format "scheme:id" or "id".
     *
     * @return the parsed external ID key
     */
    @VisibleForTesting
    public static Key parse(String externalId, boolean isCaseInsensitive) {
      int c = externalId.indexOf(':');
      if (c < 1 || c >= externalId.length() - 1) {
        return create(null, externalId, isCaseInsensitive);
      }
      return create(externalId.substring(0, c), externalId.substring(c + 1), isCaseInsensitive);
    }

    public abstract @Nullable String scheme();

    public abstract String id();

    public abstract boolean isCaseInsensitive();

    public boolean isScheme(String scheme) {
      return scheme.equals(scheme());
    }

    @Memoized
    public ObjectId sha1() {
      return sha1(isCaseInsensitive());
    }

    /**
     * Returns the SHA1 of the external ID that is used as note ID in the refs/meta/external-ids
     * notes branch.
     */
    @SuppressWarnings("deprecation") // Use Hashing.sha1 for compatibility.
    private ObjectId sha1(Boolean isCaseInsensitive) {
      String keyString = isCaseInsensitive ? get().toLowerCase(Locale.US) : get();
      return ObjectId.fromRaw(Hashing.sha1().hashString(keyString, UTF_8).asBytes());
    }

    @Memoized
    public ObjectId caseSensitiveSha1() {
      return sha1(false);
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
    public final String toString() {
      return get();
    }

    @Override
    public final boolean equals(Object obj) {
      if (!(obj instanceof ExternalId.Key)) {
        return false;
      }
      ExternalId.Key o = (ExternalId.Key) obj;

      return sha1().equals(o.sha1());
    }

    @Override
    @Memoized
    public int hashCode() {
      return Objects.hash(sha1());
    }

    public static ImmutableSet<ExternalId.Key> from(Collection<ExternalId> extIds) {
      return extIds.stream().map(ExternalId::key).collect(toImmutableSet());
    }
  }

  @VisibleForTesting
  public static ExternalId create(
      Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword,
      @Nullable ObjectId blobId) {
    return new AutoValue_ExternalId(
        key,
        accountId,
        key.isCaseInsensitive(),
        Strings.emptyToNull(email),
        Strings.emptyToNull(hashedPassword),
        blobId);
  }

  public abstract Key key();

  public abstract Account.Id accountId();

  public abstract boolean isCaseInsensitive();

  public abstract @Nullable String email();

  public abstract @Nullable String password();

  /**
   * ID of the note blob in the external IDs branch that stores this external ID. {@code null} if
   * the external ID was created in code and is not yet stored in Git.
   */
  public abstract @Nullable ObjectId blobId();

  public boolean isScheme(String scheme) {
    return key().isScheme(scheme);
  }

  /**
   * For checking if two external IDs are equals the blobId is excluded and external IDs that have
   * different blob IDs but identical other fields are considered equal. This way an external ID
   * that was loaded from Git can be equal with an external ID that was created from code.
   */
  @Override
  public final boolean equals(Object obj) {
    if (!(obj instanceof ExternalId)) {
      return false;
    }
    ExternalId o = (ExternalId) obj;
    return Objects.equals(key(), o.key())
        && Objects.equals(accountId(), o.accountId())
        && isCaseInsensitive() == o.isCaseInsensitive()
        && Objects.equals(email(), o.email())
        && Objects.equals(password(), o.password());
  }

  @Memoized
  @Override
  public int hashCode() {
    return Objects.hash(key(), accountId(), isCaseInsensitive(), email(), password());
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
  @Memoized
  public String toString() {
    Config c = new Config();
    writeToConfig(c);
    return c.toText();
  }

  @VisibleForTesting
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

  void checkThatBlobIdIsSet() {
    checkState(blobId() != null, "No blob ID set for external ID %s", key().get());
  }
}
