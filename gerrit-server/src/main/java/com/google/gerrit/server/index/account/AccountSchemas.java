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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.SchemaUtil;

import java.util.Collection;

public class AccountSchemas {
  static final Schema<AccountState> V1 = schema(
      AccountField.ID,
      AccountField.ACTIVE,
      AccountField.EMAIL,
      AccountField.EXTERNAL_ID,
      AccountField.NAME_PART,
      AccountField.REGISTERED,
      AccountField.USERNAME);

  private static Schema<AccountState> schema(
      Collection<FieldDef<AccountState, ?>> fields) {
    return new Schema<>(ImmutableList.copyOf(fields));
  }

  @SafeVarargs
  private static Schema<AccountState> schema(
      FieldDef<AccountState, ?>... fields) {
    return schema(ImmutableList.copyOf(fields));
  }

  public static final ImmutableMap<Integer, Schema<AccountState>> ALL =
      SchemaUtil.schemasFromClass(AccountSchemas.class, AccountState.class);

  public static Schema<AccountState> get(int version) {
    Schema<AccountState> schema = ALL.get(version);
    checkArgument(schema != null, "Unrecognized schema version: %s", version);
    return schema;
  }

  public static Schema<AccountState> getLatest() {
    return Iterables.getLast(ALL.values());
  }
}
