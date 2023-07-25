// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;

public interface ExternalIdFactory {
  /**
   * Creates an external ID.
   *
   * @param scheme the scheme name, must not contain colons (':'). E.g. {@link
   *     ExternalId#SCHEME_USERNAME}.
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @return the created external ID
   */
  ExternalId create(String scheme, String id, Account.Id accountId);

  /**
   * Creates an external ID.
   *
   * @param scheme the scheme name, must not contain colons (':'). E.g. {@link
   *     ExternalId#SCHEME_USERNAME}.
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @param hashedPassword the hashed password of the external ID, may be {@code null}
   * @return the created external ID
   */
  ExternalId create(
      String scheme,
      String id,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword);

  /**
   * Creates an external ID.
   *
   * @param key the external Id key
   * @param accountId the ID of the account to which the external ID belongs
   * @return the created external ID
   */
  ExternalId create(ExternalId.Key key, Account.Id accountId);

  /**
   * Creates an external ID.
   *
   * @param key the external Id key
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @param hashedPassword the hashed password of the external ID, may be {@code null}
   * @return the created external ID
   */
  ExternalId create(
      ExternalId.Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String hashedPassword);

  /**
   * Creates an external ID adding a hashed password computed from a plain password.
   *
   * @param key the external Id key
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @param plainPassword the plain HTTP password, may be {@code null}
   * @return the created external ID
   */
  ExternalId createWithPassword(
      ExternalId.Key key,
      Account.Id accountId,
      @Nullable String email,
      @Nullable String plainPassword);

  /**
   * Create a external ID for a username (scheme "username").
   *
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @param plainPassword the plain HTTP password, may be {@code null}
   * @return the created external ID
   */
  ExternalId createUsername(String id, Account.Id accountId, @Nullable String plainPassword);

  /**
   * Creates an external ID with an email.
   *
   * @param scheme the scheme name, must not contain colons (':'). E.g. {@link
   *     ExternalId#SCHEME_USERNAME}.
   * @param id the external ID, must not contain colons (':')
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @return the created external ID
   */
  ExternalId createWithEmail(
      String scheme, String id, Account.Id accountId, @Nullable String email);

  /**
   * Creates an external ID with an email.
   *
   * @param key the external Id key
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @return the created external ID
   */
  ExternalId createWithEmail(ExternalId.Key key, Account.Id accountId, @Nullable String email);

  /**
   * Creates an external ID using the `mailto`-scheme.
   *
   * @param accountId the ID of the account to which the external ID belongs
   * @param email the email of the external ID, may be {@code null}
   * @return the created external ID
   */
  ExternalId createEmail(Account.Id accountId, String email);
}
