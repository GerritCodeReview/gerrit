// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.config.AuthConfig;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class ExternalIdFactoryNoteDbImpl implements ExternalIdFactory {
  private final ExternalIdKeyFactory externalIdKeyFactory;
  private AuthConfig authConfig;

  @Inject
  @VisibleForTesting
  public ExternalIdFactoryNoteDbImpl(
      ExternalIdKeyFactory externalIdKeyFactory, AuthConfig authConfig) {
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.authConfig = authConfig;
  }

  @Override
  public ExternalId create(String scheme, String id, Account.Id accountId) {
    return create(externalIdKeyFactory.create(scheme, id), accountId, null, null);
  }

  @Override
  public ExternalId create(
      String scheme,
      String id,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword) {
    return create(externalIdKeyFactory.create(scheme, id), accountId, email, hashedPassword);
  }

  @Override
  public ExternalId create(ExternalId.Key key, Account.Id accountId) {
    return create(key, accountId, null, null);
  }

  @Override
  public ExternalId create(
      ExternalId.Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword) {
    return create(
        key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword), null);
  }

  ExternalId create(ExternalId extId, @Nullable ObjectId blobId) {
    return create(extId.key(), extId.accountId(), extId.email(), extId.password(), blobId);
  }

  /**
   * Creates an external ID.
   *
   * @param key the external Id key
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @param hashedPassword the hashed password of the external ID, may be {@code null}
   * @param blobId the ID of the note blob in the external IDs branch that stores this external ID.
   *     {@code null} if the external ID was created in code and is not yet stored in Git.
   * @return the created external ID
   */
  public ExternalId create(
      ExternalId.Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword,
      @Nullable ObjectId blobId) {
    return ExternalId.create(
        key, accountId, Strings.emptyToNull(email), Strings.emptyToNull(hashedPassword), blobId);
  }

  @Override
  public ExternalId createWithPassword(
      ExternalId.Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String plainPassword) {
    plainPassword = Strings.emptyToNull(plainPassword);
    String hashedPassword =
        plainPassword != null ? HashedPassword.fromPassword(plainPassword).encode() : null;
    return create(key, accountId, email, hashedPassword);
  }

  @Override
  public ExternalId createUsername(
      String id, Account.Id accountId, @Nullable String plainPassword) {
    return createWithPassword(
        externalIdKeyFactory.create(ExternalId.SCHEME_USERNAME, id),
        accountId,
        null,
        plainPassword);
  }

  @Override
  public ExternalId createWithEmail(
      String scheme, String id, Account.Id accountId, @Nullable String email) {
    return createWithEmail(externalIdKeyFactory.create(scheme, id), accountId, email);
  }

  @Override
  public ExternalId createWithEmail(
      ExternalId.Key key, Account.Id accountId, @Nullable String email) {
    return create(key, accountId, Strings.emptyToNull(email), null);
  }

  @Override
  public ExternalId createEmail(Account.Id accountId, String email) {
    return createWithEmail(ExternalId.SCHEME_MAILTO, email, accountId, requireNonNull(email));
  }

  /**
   * Parses an external ID from a byte array that contains the external ID as a Git config file
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
   *
   * @param noteId the SHA-1 sum of the external ID used as the note's ID
   * @param raw a byte array that contains the external ID as a Git config file text.
   * @param blobId the ID of the note blob in the external IDs branch that stores this external ID.
   *     {@code null} if the external ID was created in code and is not yet stored in Git.
   * @return the parsed external ID
   */
  public ExternalId parse(String noteId, byte[] raw, ObjectId blobId)
      throws ConfigInvalidException {
    requireNonNull(blobId);

    Config externalIdConfig = new Config();
    try {
      externalIdConfig.fromText(new String(raw, UTF_8));
    } catch (ConfigInvalidException e) {
      throw invalidConfig(noteId, e.getMessage());
    }

    Set<String> externalIdKeys = externalIdConfig.getSubsections(ExternalId.EXTERNAL_ID_SECTION);
    if (externalIdKeys.size() != 1) {
      throw invalidConfig(
          noteId,
          String.format(
              "Expected exactly 1 '%s' section, found %d",
              ExternalId.EXTERNAL_ID_SECTION, externalIdKeys.size()));
    }

    String externalIdKeyStr = Iterables.getOnlyElement(externalIdKeys);
    ExternalId.Key externalIdKey = externalIdKeyFactory.parse(externalIdKeyStr);
    if (externalIdKey == null) {
      throw invalidConfig(noteId, String.format("External ID %s is invalid", externalIdKeyStr));
    }

    if (!externalIdKey.sha1().getName().equals(noteId)) {
      if (!authConfig.isUserNameCaseInsensitiveMigrationMode()) {
        throw invalidConfig(
            noteId,
            String.format(
                "SHA1 of external ID '%s' does not match note ID '%s'", externalIdKeyStr, noteId));
      }

      if (!externalIdKey.caseSensitiveSha1().getName().equals(noteId)) {
        throw invalidConfig(
            noteId,
            String.format(
                "Neither case sensitive nor case insensitive SHA1 of external ID '%s' match note ID"
                    + " '%s'",
                externalIdKeyStr, noteId));
      }
      externalIdKey =
          externalIdKeyFactory.create(externalIdKey.scheme(), externalIdKey.id(), false);
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
      ConfigInvalidException newException =
          invalidConfig(
              noteId,
              String.format(
                  "Value %s for '%s.%s.%s' is invalid, expected account ID",
                  accountIdStr,
                  ExternalId.EXTERNAL_ID_SECTION,
                  externalIdKeyStr,
                  ExternalId.ACCOUNT_ID_KEY));
      newException.initCause(e);
      throw newException;
    }
  }

  private static ConfigInvalidException invalidConfig(String noteId, String message) {
    return new ConfigInvalidException(
        String.format("Invalid external ID config for note '%s': %s", noteId, message));
  }
}
