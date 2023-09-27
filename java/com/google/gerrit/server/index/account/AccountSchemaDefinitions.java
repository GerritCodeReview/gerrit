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

import static com.google.gerrit.index.SchemaUtil.schema;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.server.account.AccountState;

/**
 * Definition of account index versions (schemata). See {@link SchemaDefinitions}.
 *
 * <p>Upgrades are subject to constraints, see {@code
 * com.google.gerrit.index.IndexUpgradeValidator}.
 */
public class AccountSchemaDefinitions extends SchemaDefinitions<AccountState> {

  @Deprecated
  static final Schema<AccountState> V8 =
      schema(
          /* version= */ 8,
          ImmutableList.of(
              AccountField.ID_FIELD,
              AccountField.ACTIVE_FIELD,
              AccountField.EMAIL_FIELD,
              AccountField.EXTERNAL_ID_FIELD,
              AccountField.EXTERNAL_ID_STATE_FIELD,
              AccountField.FULL_NAME_FIELD,
              AccountField.NAME_PART_FIELD,
              AccountField.NAME_PART_NO_SECONDARY_EMAIL_FIELD,
              AccountField.PREFERRED_EMAIL_EXACT_FIELD,
              AccountField.PREFERRED_EMAIL_LOWER_CASE_FIELD,
              AccountField.REF_STATE_FIELD,
              AccountField.REGISTERED_FIELD,
              AccountField.USERNAME_FIELD,
              AccountField.WATCHED_PROJECT_FIELD),
          ImmutableList.<IndexedField<AccountState, ?>.SearchSpec>of(
              AccountField.ID_FIELD_SPEC,
              AccountField.ACTIVE_FIELD_SPEC,
              AccountField.EMAIL_SPEC,
              AccountField.EXTERNAL_ID_FIELD_SPEC,
              AccountField.EXTERNAL_ID_STATE_SPEC,
              AccountField.FULL_NAME_SPEC,
              AccountField.NAME_PART_NO_SECONDARY_EMAIL_SPEC,
              AccountField.NAME_PART_SPEC,
              AccountField.PREFERRED_EMAIL_LOWER_CASE_SPEC,
              AccountField.PREFERRED_EMAIL_EXACT_SPEC,
              AccountField.REF_STATE_SPEC,
              AccountField.REGISTERED_SPEC,
              AccountField.USERNAME_SPEC,
              AccountField.WATCHED_PROJECT_SPEC));

  // Bump Lucene version requires reindexing
  @Deprecated static final Schema<AccountState> V9 = schema(V8);

  // Lucene index was changed to add additional fields for sorting.
  @Deprecated static final Schema<AccountState> V10 = schema(V9);

  // New numeric types: use dimensional points using the k-d tree geo-spatial data structure
  // to offer fast single- and multi-dimensional numeric range. As the consequense, integer
  // document id type is replaced with string document id type.
  @Deprecated
  static final Schema<AccountState> V11 =
      new Schema.Builder<AccountState>()
          .add(V10)
          .remove(AccountField.ID_FIELD_SPEC)
          .remove(AccountField.ID_FIELD)
          .addIndexedFields(AccountField.ID_STR_FIELD)
          .addSearchSpecs(AccountField.ID_STR_FIELD_SPEC)
          .build();

  // Upgrade Lucene to 7.x requires reindexing.
  @Deprecated static final Schema<AccountState> V12 = schema(V11);

  // Upgrade Lucene to 8.x requires reindexing.
  @Deprecated static final Schema<AccountState> V13 = schema(V12);

  // Upgrade Lucene to 9.x requires reindexing.
  static final Schema<AccountState> V14 = schema(V13);

  /**
   * Name of the account index to be used when contacting index backends or loading configurations.
   */
  public static final String NAME = "accounts";

  /** Singleton instance of the schema definitions. This is one per JVM. */
  public static final AccountSchemaDefinitions INSTANCE = new AccountSchemaDefinitions();

  private AccountSchemaDefinitions() {
    super(NAME, AccountState.class);
  }
}
