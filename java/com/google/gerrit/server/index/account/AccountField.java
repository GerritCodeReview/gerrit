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

import static com.google.gerrit.index.FieldDef.exact;
import static com.google.gerrit.index.FieldDef.integer;
import static com.google.gerrit.index.FieldDef.prefix;
import static com.google.gerrit.index.FieldDef.storedOnly;
import static com.google.gerrit.index.FieldDef.timestamp;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Streams;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;

/** Secondary index schemas for accounts. */
public class AccountField {
  public static final FieldDef<AccountState, Integer> ID =
      integer("id").stored().build(a -> a.account().id().get());

  public static final FieldDef<AccountState, String> ID_STR =
      exact("id_str").stored().build(a -> String.valueOf(a.account().id().get()));

  /**
   * External IDs.
   *
   * <p>This field includes secondary emails. Use this field only if the current user is allowed to
   * see secondary emails (requires the {@link GlobalCapability#MODIFY_ACCOUNT} capability).
   */
  public static final FieldDef<AccountState, Stream<String>> EXTERNAL_ID =
      exact("external_id")
          .buildRepeatable(
              a -> a.externalIds().stream().map(ExternalId::key).map(ExternalId.Key::get));

  /**
   * Fuzzy prefix match on name and email parts.
   *
   * <p>This field includes parts from the secondary emails. Use this field only if the current user
   * is allowed to see secondary emails (requires the {@link GlobalCapability#MODIFY_ACCOUNT}
   * capability).
   *
   * <p>Use the {@link AccountField#NAME_PART_NO_SECONDARY_EMAIL} if the current user can't see
   * secondary emails.
   */
  public static final FieldDef<AccountState, Stream<String>> NAME_PART =
      prefix("name")
          .buildRepeatable(a -> getNameParts(a, a.externalIds().stream().map(ExternalId::email)));

  /**
   * Fuzzy prefix match on name and preferred email parts. Parts of secondary emails are not
   * included.
   */
  public static final FieldDef<AccountState, Stream<String>> NAME_PART_NO_SECONDARY_EMAIL =
      prefix("name2")
          .buildRepeatable(a -> getNameParts(a, Stream.of(a.account().preferredEmail())));

  public static final FieldDef<AccountState, String> FULL_NAME =
      exact("full_name").build(a -> a.account().fullName());

  public static final FieldDef<AccountState, String> ACTIVE =
      exact("inactive").build(a -> a.account().isActive() ? "1" : "0");

  /**
   * All emails (preferred email + secondary emails). Use this field only if the current user is
   * allowed to see secondary emails (requires the 'Modify Account' capability).
   *
   * <p>Use the {@link AccountField#PREFERRED_EMAIL} if the current user can't see secondary emails.
   */
  public static final FieldDef<AccountState, Stream<String>> EMAIL =
      prefix("email")
          .buildRepeatable(
              a ->
                  Streams.concat(
                          a.externalIds().stream().map(ExternalId::email),
                          Stream.of(a.account().preferredEmail()))
                      .filter(Objects::nonNull)
                      .map(String::toLowerCase));

  public static final FieldDef<AccountState, String> PREFERRED_EMAIL =
      prefix("preferredemail")
          .build(
              a -> {
                String preferredEmail = a.account().preferredEmail();
                return preferredEmail != null ? preferredEmail.toLowerCase() : null;
              });

  public static final FieldDef<AccountState, String> PREFERRED_EMAIL_EXACT =
      exact("preferredemail_exact").build(a -> a.account().preferredEmail());

  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final FieldDef<AccountState, Timestamp> REGISTERED =
      timestamp("registered").build(a -> Timestamp.from(a.account().registeredOn()));

  public static final FieldDef<AccountState, String> USERNAME =
      exact("username").build(a -> a.userName().map(String::toLowerCase).orElse(""));

  public static final FieldDef<AccountState, Stream<String>> WATCHED_PROJECT =
      exact("watchedproject")
          .buildRepeatable(a -> a.projectWatches().keySet().stream().map(k -> k.project().get()));

  /**
   * All values of all refs that were used in the course of indexing this document, except the
   * refs/meta/external-ids notes branch which is handled specially (see {@link
   * #EXTERNAL_ID_STATE}).
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final FieldDef<AccountState, Stream<byte[]>> REF_STATE =
      storedOnly("ref_state")
          .buildRepeatable(
              a -> {
                if (a.account().metaId() == null) {
                  return Stream.empty();
                }

                return Stream.of(
                    RefState.create(
                            RefNames.refsUsers(a.account().id()),
                            ObjectId.fromString(a.account().metaId()))
                        // We use the default AllUsers name to avoid having to pass around that
                        // variable just for indexing.
                        // This field is only used for staleness detection which will discover the
                        // default name and replace it with the actually configured name.
                        .toByteArray(new AllUsersName(AllUsersNameProvider.DEFAULT)));
              });

  /**
   * All note values of all external IDs that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code [hex sha of external ID]:[hex sha of
   * note blob]}, or with other words {@code [note ID]:[note data ID]}.
   */
  public static final FieldDef<AccountState, Stream<byte[]>> EXTERNAL_ID_STATE =
      storedOnly("external_id_state")
          .buildRepeatable(
              a ->
                  a.externalIds().stream()
                      .filter(e -> e.blobId() != null)
                      .map(ExternalId::toByteArray));

  private static final Stream<String> getNameParts(AccountState a, Stream<String> emails) {
    String fullName = a.account().fullName();
    Set<String> parts = SchemaUtil.getNameParts(fullName, emails.collect(toSet()));

    // Additional values not currently added by getPersonParts.
    // TODO(dborowitz): Move to getPersonParts and remove this hack.
    if (fullName != null) {
      parts.add(fullName.toLowerCase(Locale.US));
    }
    return parts.stream();
  }

  private AccountField() {}
}
