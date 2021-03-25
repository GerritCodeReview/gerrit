package com.google.gerrit.server.account.externalids;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gerrit.server.account.externalids.ExternalId.Key;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

public class ExternalIdFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final boolean isUserNameCaseInsensitive;
  private final ExternalIdKeyFactory externalIdKeyFactory;

  @Inject
  public ExternalIdFactory(AuthConfig authConfig, ExternalIdKeyFactory externalIdKeyFactory) {
    this.isUserNameCaseInsensitive = authConfig.isUserNameCaseInsensitive();
    this.externalIdKeyFactory = externalIdKeyFactory;
  }

  /**
   * Creates an external ID.
   *
   * @param scheme the scheme name, must not contain colons (':')
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @return the created external ID
   */
  public ExternalId create(String scheme, String id, Account.Id accountId) {
    return create(externalIdKeyFactory.create(scheme, id), accountId, null, null);
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
  public ExternalId create(
      String scheme,
      String id,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword) {
    return create(externalIdKeyFactory.create(scheme, id), accountId, email, hashedPassword);
  }

  public ExternalId create(Key key, Account.Id accountId) {
    return create(key, accountId, null, null);
  }

  public ExternalId create(
      Key key, Account.Id accountId, @Nullable String email, @Nullable String hashedPassword) {
    return create(
        key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword), null);
  }

  public ExternalId createWithPassword(
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
  public ExternalId createUsername(
      String id, Account.Id accountId, @Nullable String plainPassword) {
    return createWithPassword(
        externalIdKeyFactory.create(ExternalId.SCHEME_USERNAME, id),
        accountId,
        null,
        plainPassword);
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
  public ExternalId createWithEmail(
      String scheme, String id, Account.Id accountId, @Nullable String email) {
    return createWithEmail(externalIdKeyFactory.create(scheme, id), accountId, email);
  }

  public ExternalId createWithEmail(Key key, Account.Id accountId, @Nullable String email) {
    return create(key, accountId, Strings.emptyToNull(email), null);
  }

  public ExternalId createEmail(Account.Id accountId, String email) {
    return createWithEmail(ExternalId.SCHEME_MAILTO, email, accountId, requireNonNull(email));
  }

  ExternalId create(ExternalId extId, @Nullable ObjectId blobId) {
    return create(extId.key(), extId.accountId(), extId.email(), extId.password(), blobId);
  }

  @VisibleForTesting
  public ExternalId create(
      Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword,
      @Nullable ObjectId blobId) {
    if (key.scheme() != null
        && (key.scheme().equals(ExternalId.SCHEME_USERNAME)
            || key.scheme().equals(ExternalId.SCHEME_GERRIT))) {
      return ExternalId.create(
          key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword), blobId);
    }
    return ExternalId.create(
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
  public ExternalId parse(String noteId, byte[] raw, ObjectId blobId)
      throws ConfigInvalidException {
    Config externalIdConfig = new Config();
    try {
      externalIdConfig.fromText(new String(raw, UTF_8));
    } catch (ConfigInvalidException e) {
      throw invalidConfig(noteId, e.getMessage());
    }

    return parse(noteId, externalIdConfig, blobId);
  }

  public ExternalId parse(String noteId, Config externalIdConfig, ObjectId blobId)
      throws ConfigInvalidException {
    requireNonNull(blobId);

    Set<String> externalIdKeys = externalIdConfig.getSubsections(ExternalId.EXTERNAL_ID_SECTION);
    if (externalIdKeys.size() != 1) {
      throw invalidConfig(
          noteId,
          String.format(
              "Expected exactly 1 '%s' section, found %d",
              ExternalId.EXTERNAL_ID_SECTION, externalIdKeys.size()));
    }

    String externalIdKeyStr = Iterables.getOnlyElement(externalIdKeys);
    Key externalIdKey = externalIdKeyFactory.parse(externalIdKeyStr);
    if (externalIdKey == null) {
      throw invalidConfig(noteId, String.format("External ID %s is invalid", externalIdKeyStr));
    }

    if (!ExternalIdNotes.computeNoteId(externalIdKey).getName().equals(noteId)) {
      throw invalidConfig(
          noteId,
          String.format(
              "SHA1 of external ID '%s' does not match note ID '%s'", externalIdKeyStr, noteId));
    }

    String email =
        externalIdConfig.getString(
            ExternalId.EXTERNAL_ID_SECTION, externalIdKeyStr, ExternalId.EMAIL_KEY);
    String password =
        externalIdConfig.getString(
            ExternalId.EXTERNAL_ID_SECTION, externalIdKeyStr, ExternalId.PASSWORD_KEY);
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
        externalIdConfig.getString(
            ExternalId.EXTERNAL_ID_SECTION, externalIdKeyStr, ExternalId.ACCOUNT_ID_KEY);
    if (accountIdStr == null) {
      throw invalidConfig(
          noteId,
          String.format(
              "Value for '%s.%s.%s' is missing, expected account ID",
              ExternalId.EXTERNAL_ID_SECTION, externalIdKeyStr, ExternalId.ACCOUNT_ID_KEY));
    }

    try {
      int accountId =
          externalIdConfig.getInt(
              ExternalId.EXTERNAL_ID_SECTION, externalIdKeyStr, ExternalId.ACCOUNT_ID_KEY, -1);
      if (accountId < 0) {
        throw invalidConfig(
            noteId,
            String.format(
                "Value %s for '%s.%s.%s' is invalid, expected account ID",
                accountIdStr,
                ExternalId.EXTERNAL_ID_SECTION,
                externalIdKeyStr,
                ExternalId.ACCOUNT_ID_KEY));
      }
      return accountId;
    } catch (IllegalArgumentException e) {
      String msg =
          String.format(
              "Value %s for '%s.%s.%s' is invalid, expected account ID",
              accountIdStr,
              ExternalId.EXTERNAL_ID_SECTION,
              externalIdKeyStr,
              ExternalId.ACCOUNT_ID_KEY);
      logger.atSevere().withCause(e).log(msg);
      throw invalidConfig(noteId, msg);
    }
  }

  private static ConfigInvalidException invalidConfig(String noteId, String message) {
    return new ConfigInvalidException(
        String.format("Invalid external ID config for note '%s': %s", noteId, message));
  }
}
