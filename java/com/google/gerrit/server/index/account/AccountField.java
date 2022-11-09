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

package com.google.gerrit.server.index.account;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Secondary index schemas for accounts.
 *
 * <p>Note that this class does not override {@link Object#equals(Object)}. It relies on instances
 * being singletons so that the default (i.e. reference) comparison works.
 */
public class AccountField {

  public static final IndexedField<AccountState, Integer> ID_FIELD =
      IndexedField.<AccountState>integerBuilder("Id")
          .stored()
          .required()
          .build(a -> a.account().id().get());

  public static final IndexedField<AccountState, Integer>.SearchSpec ID_FIELD_SPEC =
      ID_FIELD.integer("id");

  public static final IndexedField<AccountState, String> ID_STR_FIELD =
      IndexedField.<AccountState>stringBuilder("IdStr")
          .stored()
          .required()
          .build(a -> String.valueOf(a.account().id().get()));

  public static final IndexedField<AccountState, String>.SearchSpec ID_STR_FIELD_SPEC =
      ID_STR_FIELD.exact("id_str");

  /**
   * External IDs.
   *
   * <p>This field includes secondary emails. Use this field only if the current user is allowed to
   * see secondary emails (requires the {@link GlobalCapability#MODIFY_ACCOUNT} capability).
   */
  public static final IndexedField<AccountState, Iterable<String>> EXTERNAL_ID_FIELD =
      IndexedField.<AccountState>iterableStringBuilder("ExternalId")
          .required()
          .build(a -> Iterables.transform(a.externalIds(), id -> id.key().get()));

  public static final IndexedField<AccountState, Iterable<String>>.SearchSpec
      EXTERNAL_ID_FIELD_SPEC = EXTERNAL_ID_FIELD.exact("external_id");

  /**
   * Fuzzy prefix match on name and email parts.
   *
   * <p>This field includes parts from the secondary emails. Use this field only if the current user
   * is allowed to see secondary emails (requires the {@link GlobalCapability#MODIFY_ACCOUNT}
   * capability).
   *
   * <p>Use the {@link AccountField#NAME_PART_NO_SECONDARY_EMAIL_SPEC} if the current user can't see
   * secondary emails.
   */
  public static final IndexedField<AccountState, Iterable<String>> NAME_PART_FIELD =
      IndexedField.<AccountState>iterableStringBuilder("FullNameAndAllEmailsParts")
          .description("Full name, all linked emails and their parts (split at special characters)")
          .required()
          .build(a -> getNameParts(a, Iterables.transform(a.externalIds(), ExternalId::email)));

  public static final IndexedField<AccountState, Iterable<String>>.SearchSpec NAME_PART_SPEC =
      NAME_PART_FIELD.prefix("name");

  /**
   * Fuzzy prefix match on name and preferred email parts. Parts of secondary emails are not
   * included.
   */
  public static final IndexedField<AccountState, Iterable<String>>
      NAME_PART_NO_SECONDARY_EMAIL_FIELD =
          IndexedField.<AccountState>iterableStringBuilder("FullNameAndPreferredEmailParts")
              .description(
                  "Full name, preferred emails and its parts (split at special characters)")
              .required()
              .build(a -> getNameParts(a, Arrays.asList(a.account().preferredEmail())));

  public static final IndexedField<AccountState, Iterable<String>>.SearchSpec
      NAME_PART_NO_SECONDARY_EMAIL_SPEC = NAME_PART_NO_SECONDARY_EMAIL_FIELD.prefix("name2");

  public static final IndexedField<AccountState, String> FULL_NAME_FIELD =
      IndexedField.<AccountState>stringBuilder("FullName").build(a -> a.account().fullName());

  public static final IndexedField<AccountState, String>.SearchSpec FULL_NAME_SPEC =
      FULL_NAME_FIELD.exact("full_name");

  public static final IndexedField<AccountState, String> ACTIVE_FIELD =
      IndexedField.<AccountState>stringBuilder("Active")
          .required()
          .build(a -> a.account().isActive() ? "1" : "0");

  public static final IndexedField<AccountState, String>.SearchSpec ACTIVE_FIELD_SPEC =
      ACTIVE_FIELD.exact("inactive");
  /**
   * All emails (preferred email + secondary emails). Use this field only if the current user is
   * allowed to see secondary emails (requires the 'Modify Account' capability).
   *
   * <p>Use the {@link AccountField#PREFERRED_EMAIL_LOWER_CASE_SPEC} if the current user can't see
   * secondary emails.
   */
  public static final IndexedField<AccountState, Iterable<String>> EMAIL_FIELD =
      IndexedField.<AccountState>iterableStringBuilder("Email")
          .required()
          .build(
              a ->
                  FluentIterable.from(a.externalIds())
                      .transform(ExternalId::email)
                      .append(Collections.singleton(a.account().preferredEmail()))
                      .filter(Objects::nonNull)
                      .transform(String::toLowerCase)
                      .toSet());

  public static final IndexedField<AccountState, Iterable<String>>.SearchSpec EMAIL_SPEC =
      EMAIL_FIELD.prefix("email");

  public static final IndexedField<AccountState, String> PREFERRED_EMAIL_LOWER_CASE_FIELD =
      IndexedField.<AccountState>stringBuilder("PreferredEmailLowerCase")
          .build(
              a -> {
                String preferredEmail = a.account().preferredEmail();
                return preferredEmail != null ? preferredEmail.toLowerCase() : null;
              });

  public static final IndexedField<AccountState, String>.SearchSpec
      PREFERRED_EMAIL_LOWER_CASE_SPEC = PREFERRED_EMAIL_LOWER_CASE_FIELD.prefix("preferredemail");

  public static final IndexedField<AccountState, String> PREFERRED_EMAIL_EXACT_FIELD =
      IndexedField.<AccountState>stringBuilder("PreferredEmail")
          .build(a -> a.account().preferredEmail());

  public static final IndexedField<AccountState, String>.SearchSpec PREFERRED_EMAIL_EXACT_SPEC =
      PREFERRED_EMAIL_EXACT_FIELD.exact("preferredemail_exact");

  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final IndexedField<AccountState, Timestamp> REGISTERED_FIELD =
      IndexedField.<AccountState>timestampBuilder("Registered")
          .required()
          .build(a -> Timestamp.from(a.account().registeredOn()));

  public static final IndexedField<AccountState, Timestamp>.SearchSpec REGISTERED_SPEC =
      REGISTERED_FIELD.timestamp("registered");

  public static final IndexedField<AccountState, String> USERNAME_FIELD =
      IndexedField.<AccountState>stringBuilder("Username")
          .build(a -> a.userName().map(String::toLowerCase).orElse(""));

  public static final IndexedField<AccountState, String>.SearchSpec USERNAME_SPEC =
      USERNAME_FIELD.exact("username");

  public static final IndexedField<AccountState, Iterable<String>> WATCHED_PROJECT_FIELD =
      IndexedField.<AccountState>iterableStringBuilder("WatchedProject")
          .build(
              a ->
                  FluentIterable.from(a.projectWatches().keySet())
                      .transform(k -> k.project().get())
                      .toSet());

  public static final IndexedField<AccountState, Iterable<String>>.SearchSpec WATCHED_PROJECT_SPEC =
      WATCHED_PROJECT_FIELD.exact("watchedproject");

  /**
   * All values of all refs that were used in the course of indexing this document, except the
   * refs/meta/external-ids notes branch which is handled specially (see {@link
   * #EXTERNAL_ID_STATE_SPEC}).
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final IndexedField<AccountState, Iterable<byte[]>> REF_STATE_FIELD =
      IndexedField.<AccountState>iterableByteArrayBuilder("RefState")
          .stored()
          .required()
          .build(
              a -> {
                if (a.account().metaId() == null) {
                  return ImmutableList.of();
                }

                return ImmutableList.of(
                    RefState.create(
                            RefNames.refsUsers(a.account().id()),
                            ObjectId.fromString(a.account().metaId()))
                        // We use the default AllUsers name to avoid having to pass around that
                        // variable just for indexing.
                        // This field is only used for staleness detection which will discover the
                        // default name and replace it with the actually configured name.
                        .toByteArray(new AllUsersName(AllUsersNameProvider.DEFAULT)));
              });

  public static final IndexedField<AccountState, Iterable<byte[]>>.SearchSpec REF_STATE_SPEC =
      REF_STATE_FIELD.storedOnly("ref_state");

  /**
   * All note values of all external IDs that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code [hex sha of external ID]:[hex sha of
   * note blob]}, or with other words {@code [note ID]:[note data ID]}.
   */
  public static final IndexedField<AccountState, Iterable<byte[]>> EXTERNAL_ID_STATE_FIELD =
      IndexedField.<AccountState>iterableByteArrayBuilder("ExternalIdState")
          .stored()
          .required()
          .build(
              a ->
                  a.externalIds().stream()
                      .filter(e -> e.blobId() != null)
                      .map(ExternalId::toByteArray)
                      .collect(toSet()));

  public static final IndexedField<AccountState, Iterable<byte[]>>.SearchSpec
      EXTERNAL_ID_STATE_SPEC = EXTERNAL_ID_STATE_FIELD.storedOnly("external_id_state");

  private static final Set<String> getNameParts(AccountState a, Iterable<String> emails) {
    String fullName = a.account().fullName();
    Set<String> parts = SchemaUtil.getNameParts(fullName, emails);

    // Additional values not currently added by getPersonParts.
    // TODO(dborowitz): Move to getPersonParts and remove this hack.
    if (fullName != null) {
      parts.add(fullName.toLowerCase(Locale.US));
    }
    return parts;
  }

  private AccountField() {}
}
