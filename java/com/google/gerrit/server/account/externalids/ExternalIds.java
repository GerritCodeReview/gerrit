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

package com.google.gerrit.server.account.externalids;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

public interface ExternalIds {
  /** Returns all external IDs. */
  ImmutableSet<ExternalId> all() throws IOException, ConfigInvalidException;

  /** Returns the specified external ID. */
  Optional<ExternalId> get(ExternalId.Key key) throws IOException;

  /** Returns the specified external IDs. */
  ImmutableSet<ExternalId> get(Set<ExternalId.Key> keys) throws IOException;

  /** Returns the external IDs of the specified account. */
  ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException;

  /**
   * Returns the external IDs of the specified account that have the given scheme.
   *
   * <p>Callers to this method should care about accuracy rather than latency. For better latency
   * performance, call {@link ExternalIdCache#byAccount} directly.
   */
  ImmutableSet<ExternalId> byAccount(Account.Id accountId, String scheme) throws IOException;

  /** Returns all external IDs by account. */
  ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException;

  /**
   * Returns the external ID with the given email.
   *
   * <p>Each email should belong to a single external ID only. This means if more than one external
   * ID is returned there is an inconsistency in the external IDs.
   *
   * <p>Callers to this method should care about accuracy rather than latency. For better latency
   * performance, call {@link ExternalIdCache#byEmail(String)} directly.
   */
  ImmutableSet<ExternalId> byEmail(String email) throws IOException;

  /**
   * Returns the external IDs for the given emails.
   *
   * <p>Each email should belong to a single external ID only. This means if more than one external
   * ID for an email is returned there is an inconsistency in the external IDs.
   *
   * <p>Callers to this method should care about accuracy rather than latency. For better latency
   * performance, call {@link ExternalIdCache#byEmails(String...)} directly.
   *
   * @see #byEmail(String)
   */
  ImmutableSetMultimap<String, ExternalId> byEmails(String... emails) throws IOException;
}
