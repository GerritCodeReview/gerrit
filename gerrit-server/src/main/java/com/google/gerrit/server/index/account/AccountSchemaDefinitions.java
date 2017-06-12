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

import static com.google.gerrit.server.index.SchemaUtil.schema;

import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.SchemaDefinitions;

public class AccountSchemaDefinitions extends SchemaDefinitions<AccountState> {
  static final Schema<AccountState> V1 =
      schema(
          AccountField.ID,
          AccountField.ACTIVE,
          AccountField.EMAIL,
          AccountField.EXTERNAL_ID,
          AccountField.NAME_PART,
          AccountField.REGISTERED,
          AccountField.USERNAME);

  static final Schema<AccountState> V2 = schema(V1, AccountField.WATCHED_PROJECT);

  static final Schema<AccountState> V3 = schema(V2, AccountField.FULL_NAME);

  public static final AccountSchemaDefinitions INSTANCE = new AccountSchemaDefinitions();

  private AccountSchemaDefinitions() {
    super("accounts", AccountState.class);
  }
}
