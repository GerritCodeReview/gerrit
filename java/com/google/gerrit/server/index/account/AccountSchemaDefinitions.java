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

import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.server.account.AccountState;

/** Definition of account index versions (schemata). See {@link SchemaDefinitions}. */
public class AccountSchemaDefinitions extends SchemaDefinitions<AccountState> {

  @Deprecated
  static final Schema<AccountState> V8 =
      schema(
          AccountField.ACTIVE,
          AccountField.EMAIL,
          AccountField.EXTERNAL_ID,
          AccountField.EXTERNAL_ID_STATE,
          AccountField.FULL_NAME,
          AccountField.ID,
          AccountField.NAME_PART,
          AccountField.NAME_PART_NO_SECONDARY_EMAIL,
          AccountField.PREFERRED_EMAIL,
          AccountField.PREFERRED_EMAIL_EXACT,
          AccountField.REF_STATE,
          AccountField.REGISTERED,
          AccountField.USERNAME,
          AccountField.WATCHED_PROJECT);

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
          .remove(AccountField.ID)
          .add(AccountField.ID_STR)
          .build();

  // Upgrade Lucene to 7.x requires reindexing.
  static final Schema<AccountState> V12 = schema(V11);

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
