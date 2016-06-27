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

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;

import java.sql.Timestamp;

/** Secondary index schemas for accounts. */
public class AccountField {
  public static final FieldDef<AccountState, Integer> ID =
      new FieldDef.Single<AccountState, Integer>(
          "id", FieldType.INTEGER, true) {
        @Override
        public Integer get(AccountState input, FillArgs args) {
          return input.getAccount().getId().get();
        }
      };

  public static final FieldDef<AccountState, Iterable<String>> EXTERNAL_ID =
      new FieldDef.Repeatable<AccountState, String>(
          "external_id", FieldType.EXACT, false) {
        @Override
        public Iterable<String> get(AccountState input, FillArgs args) {
          return Iterables.transform(
              input.getExternalIds(),
              new Function<AccountExternalId, String>() {
                @Override
                public String apply(AccountExternalId in) {
                  return in.getKey().get();
                }
              });
        }
      };

  /** Fuzzy prefix match on name and email parts. */
  public static final FieldDef<AccountState, Iterable<String>> NAME_PART =
      new FieldDef.Repeatable<AccountState, String>(
          "name", FieldType.PREFIX, false) {
        @Override
        public Iterable<String> get(AccountState input, FillArgs args) {
          return input.getNameParts();
        }
      };

  public static final FieldDef<AccountState, String> ACTIVE =
      new FieldDef.Single<AccountState, String>(
          "inactive", FieldType.EXACT, false) {
        @Override
        public String get(AccountState input, FillArgs args) {
          return input.getAccount().isActive() ? "1" : "0";
        }
      };

  public static final FieldDef<AccountState, Iterable<String>> EMAIL =
      new FieldDef.Repeatable<AccountState, String>(
          "email", FieldType.PREFIX, false) {
        @Override
        public Iterable<String> get(AccountState input, FillArgs args) {
          return input.getEmails();
        }
      };

  public static final FieldDef<AccountState, Timestamp> REGISTERED =
      new FieldDef.Single<AccountState, Timestamp>(
          "registered", FieldType.TIMESTAMP, false) {
        @Override
        public Timestamp get(AccountState input, FillArgs args) {
          return input.getAccount().getRegisteredOn();
        }
      };

  public static final FieldDef<AccountState, String> USERNAME =
      new FieldDef.Single<AccountState, String>(
            "username", FieldType.EXACT, false) {
        @Override
        public String get(AccountState input, FillArgs args) {
          return Strings.nullToEmpty(input.getUserName()).toLowerCase();
        }
      };

  private AccountField() {
  }
}
