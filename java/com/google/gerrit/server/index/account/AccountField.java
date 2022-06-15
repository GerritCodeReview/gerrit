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
import com.google.gerrit.index.Field;
import com.google.gerrit.index.Field.FieldSpec;
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

/** Secondary index schemas for accounts. */
public class AccountField {

  public static final Field<AccountState, Integer> ID_FIELD =
      Field.<AccountState>integerBuilder("Id").stored().build(a -> a.account().id().get());

  public static final FieldSpec ID_FIELD_SPEC = ID_FIELD.integer("id");

  public static final Field<AccountState, String> ID_STR_FIELD =
      Field.<AccountState>stringBuilder("IdStr")
          .stored()
          .build(a -> String.valueOf(a.account().id().get()));

  public static final FieldSpec ID_STR_FIELD_SPEC = ID_STR_FIELD.exact("id_str");

  /**
   * External IDs.
   *
   * <p>This field includes secondary emails. Use this field only if the current user is allowed to
   * see secondary emails (requires the {@link GlobalCapability#MODIFY_ACCOUNT} capability).
   */
  public static final Field<AccountState, Iterable<String>> EXTERNAL_ID_FIELD =
      Field.<AccountState>iterableStringBuilder("ExternalId")
          .build(a -> Iterables.transform(a.externalIds(), id -> id.key().get()));

  public static final FieldSpec EXTERNAL_ID_FIELD_SPEC = EXTERNAL_ID_FIELD.exact("external_id");

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
  public static final Field<AccountState, Iterable<String>> NAME_PART_FIELD =
      Field.<AccountState>iterableStringBuilder("NamePart")
          .build(a -> getNameParts(a, Iterables.transform(a.externalIds(), ExternalId::email)));

  public static final FieldSpec NAME_PART_SPEC = NAME_PART_FIELD.prefix("name");

  /**
   * Fuzzy prefix match on name and preferred email parts. Parts of secondary emails are not
   * included.
   */
  public static final Field<AccountState, Iterable<String>> NAME_PART_NO_SECONDARY_EMAIL_FIELD =
      Field.<AccountState>iterableStringBuilder("NamePartNoEmail")
          .build(a -> getNameParts(a, Arrays.asList(a.account().preferredEmail())));

  public static final FieldSpec NAME_PART_NO_SECONDARY_EMAIL_SPEC =
      NAME_PART_NO_SECONDARY_EMAIL_FIELD.prefix("name2");

  public static final Field<AccountState, String> FULL_NAME_FIELD =
      Field.<AccountState>stringBuilder("FullName").build(a -> a.account().fullName());

  public static final FieldSpec FULL_NAME_SPEC = FULL_NAME_FIELD.exact("full_name");

  public static final Field<AccountState, String> ACTIVE_FIELD =
      Field.<AccountState>stringBuilder("Active").build(a -> a.account().isActive() ? "1" : "0");

  public static final FieldSpec ACTIVE_FIELD_SPEC = ACTIVE_FIELD.exact("inactive");
  /**
   * All emails (preferred email + secondary emails). Use this field only if the current user is
   * allowed to see secondary emails (requires the 'Modify Account' capability).
   *
   * <p>Use the {@link AccountField#PREFERRED_EMAIL_SPEC} if the current user can't see secondary
   * emails.
   */
  public static final Field<AccountState, Iterable<String>> EMAIL_FIELD =
      Field.<AccountState>iterableStringBuilder("Email")
          .build(
              a ->
                  FluentIterable.from(a.externalIds())
                      .transform(ExternalId::email)
                      .append(Collections.singleton(a.account().preferredEmail()))
                      .filter(Objects::nonNull)
                      .transform(String::toLowerCase)
                      .toSet());

  public static final FieldSpec EMAIL_SPEC = EMAIL_FIELD.prefix("email");

  public static final Field<AccountState, String> PREFERRED_EMAIL_FIELD =
      Field.<AccountState>stringBuilder("PreferredEmail").build(a -> a.account().preferredEmail());

  public static final FieldSpec PREFERRED_EMAIL_SPEC =
      PREFERRED_EMAIL_FIELD.prefix("preferredemail");
  public static final FieldSpec PREFERRED_EMAIL_EXACT_SPEC =
      PREFERRED_EMAIL_FIELD.exact("preferredemail_exact");

  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final Field<AccountState, Timestamp> REGISTERED_FIELD =
      Field.<AccountState>timestampBuilder("Registered")
          .build(a -> Timestamp.from(a.account().registeredOn()));

  public static final FieldSpec REGISTERED_SPEC = REGISTERED_FIELD.timestamp("registered");

  public static final Field<AccountState, String> USERNAME_FIELD =
      Field.<AccountState>stringBuilder("username")
          .build(a -> a.userName().map(String::toLowerCase).orElse(""));

  public static final FieldSpec USERNAME_SPEC = USERNAME_FIELD.exact("username");

  public static final Field<AccountState, Iterable<String>> WATCHED_PROJECT_FIELD =
      Field.<AccountState>iterableStringBuilder("WatchedProject")
          .build(
              a ->
                  FluentIterable.from(a.projectWatches().keySet())
                      .transform(k -> k.project().get())
                      .toSet());

  public static final FieldSpec WATCHED_PROJECT_SPEC =
      WATCHED_PROJECT_FIELD.exact("watchedproject");

  /**
   * All values of all refs that were used in the course of indexing this document, except the
   * refs/meta/external-ids notes branch which is handled specially (see {@link
   * #EXTERNAL_ID_STATE_SPEC}).
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final Field<AccountState, Iterable<byte[]>> REF_STATE_FIELD =
      Field.<AccountState>iterableByteArrayBuilder("RefState")
          .stored()
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

  public static final FieldSpec REF_STATE_SPEC = REF_STATE_FIELD.storedOnly("ref_state");

  /**
   * All note values of all external IDs that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code [hex sha of external ID]:[hex sha of
   * note blob]}, or with other words {@code [note ID]:[note data ID]}.
   */
  public static final Field<AccountState, Iterable<byte[]>> EXTERNAL_ID_STATE_FIELD =
      Field.<AccountState>iterableByteArrayBuilder("ExternalIdState")
          .stored()
          .build(
              a ->
                  a.externalIds().stream()
                      .filter(e -> e.blobId() != null)
                      .map(ExternalId::toByteArray)
                      .collect(toSet()));

  public static final FieldSpec EXTERNAL_ID_STATE_SPEC =
      EXTERNAL_ID_STATE_FIELD.storedOnly("external_id_state");

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
