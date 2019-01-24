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

public class AccountSchemaDefinitions extends SchemaDefinitions<AccountState> {
  @Deprecated
  static final Schema<AccountState> V4 =
      schema(
          AccountField.ACTIVE,
          AccountField.EMAIL,
          AccountField.EXTERNAL_ID,
          AccountField.FULL_NAME,
          AccountField.ID,
          AccountField.NAME_PART,
          AccountField.REGISTERED,
          AccountField.USERNAME,
          AccountField.WATCHED_PROJECT);

  @Deprecated static final Schema<AccountState> V5 = schema(V4, AccountField.PREFERRED_EMAIL);

  @Deprecated
  static final Schema<AccountState> V6 =
      schema(V5, AccountField.REF_STATE, AccountField.EXTERNAL_ID_STATE);

  @Deprecated static final Schema<AccountState> V7 = schema(V6, AccountField.PREFERRED_EMAIL_EXACT);

  @Deprecated
  static final Schema<AccountState> V8 = schema(V7, AccountField.NAME_PART_NO_SECONDARY_EMAIL);

  // Bump Lucene version requires reindexing
  @Deprecated static final Schema<AccountState> V9 = schema(V8);

  // Lucene index was changed to add additional fields for sorting.
  @Deprecated static final Schema<AccountState> V10 = schema(V9);

  // Change document ID type from LegacyIntField to StringField
  static final Schema<AccountState> V11 = schema(V10);

  public static final String NAME = "accounts";
  public static final AccountSchemaDefinitions INSTANCE = new AccountSchemaDefinitions();

  private AccountSchemaDefinitions() {
    super(NAME, AccountState.class);
  }
}
