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

import static com.google.gerrit.server.index.FieldDef.exact;
import static com.google.gerrit.server.index.FieldDef.integer;
import static com.google.gerrit.server.index.FieldDef.prefix;
import static com.google.gerrit.server.index.FieldDef.timestamp;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.ExternalId;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.SchemaUtil;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/** Secondary index schemas for accounts. */
public class AccountField {
  public static final FieldDef<AccountState, Integer> ID =
      integer("id").stored().build(a -> a.getAccount().getId().get());

  public static final FieldDef<AccountState, Iterable<String>> EXTERNAL_ID =
      exact("external_id")
          .buildRepeatable(a -> Iterables.transform(a.getExternalIds(), id -> id.key().get()));

  /** Fuzzy prefix match on name and email parts. */
  public static final FieldDef<AccountState, Iterable<String>> NAME_PART =
      prefix("name")
          .buildRepeatable(
              a -> {
                String fullName = a.getAccount().getFullName();
                Set<String> parts =
                    SchemaUtil.getNameParts(
                        fullName, Iterables.transform(a.getExternalIds(), ExternalId::email));

                // Additional values not currently added by getPersonParts.
                // TODO(dborowitz): Move to getPersonParts and remove this hack.
                if (fullName != null) {
                  parts.add(fullName.toLowerCase(Locale.US));
                }
                return parts;
              });

  public static final FieldDef<AccountState, String> FULL_NAME =
      exact("full_name").build(a -> a.getAccount().getFullName());

  public static final FieldDef<AccountState, String> ACTIVE =
      exact("inactive").build(a -> a.getAccount().isActive() ? "1" : "0");

  public static final FieldDef<AccountState, Iterable<String>> EMAIL =
      prefix("email")
          .buildRepeatable(
              a ->
                  FluentIterable.from(a.getExternalIds())
                      .transform(ExternalId::email)
                      .append(Collections.singleton(a.getAccount().getPreferredEmail()))
                      .filter(Predicates.notNull())
                      .transform(String::toLowerCase)
                      .toSet());

  public static final FieldDef<AccountState, Timestamp> REGISTERED =
      timestamp("registered").build(a -> a.getAccount().getRegisteredOn());

  public static final FieldDef<AccountState, String> USERNAME =
      exact("username").build(a -> Strings.nullToEmpty(a.getUserName()).toLowerCase());

  public static final FieldDef<AccountState, Iterable<String>> WATCHED_PROJECT =
      exact("watchedproject")
          .buildRepeatable(
              a ->
                  FluentIterable.from(a.getProjectWatches().keySet())
                      .transform(k -> k.project().get())
                      .toSet());

  private AccountField() {}
}
