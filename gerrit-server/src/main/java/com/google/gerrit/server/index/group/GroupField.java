// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.index.group;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.SchemaUtil;
import com.google.gwtorm.server.OrmException;

/** Secondary index schemas for groups. */
public class GroupField {
  /** Legacy group ID. */
  public static final FieldDef<AccountGroup, Integer> ID =
      new FieldDef.Single<AccountGroup, Integer>(
          "id", FieldType.INTEGER, false) {
        @Override
        public Integer get(AccountGroup input, FillArgs args) {
          return input.getId().get();
        }
      };

  /** Group UUID. */
  public static final FieldDef<AccountGroup, String> UUID =
      new FieldDef.Single<AccountGroup, String>(
          "uuid", FieldType.EXACT, true) {
        @Override
        public String get(AccountGroup input, FillArgs args) {
          return input.getGroupUUID().get();
        }
      };

  /** Group owner UUID. */
  public static final FieldDef<AccountGroup, String> OWNER_UUID =
      new FieldDef.Single<AccountGroup, String>(
          "owner_uuid", FieldType.EXACT, false) {
        @Override
        public String get(AccountGroup input, FillArgs args) {
          return input.getOwnerGroupUUID().get();
        }
      };

  /** Group name. */
  public static final FieldDef<AccountGroup, String> NAME =
      new FieldDef.Single<AccountGroup, String>(
          "name", FieldType.EXACT, false) {
        @Override
        public String get(AccountGroup input, FillArgs args) {
          return input.getName();
        }
      };

  /** Fuzzy prefix match on group name parts. */
  public static final FieldDef<AccountGroup, Iterable<String>> NAME_PART =
      new FieldDef.Repeatable<AccountGroup, String>(
          "name_part", FieldType.PREFIX, false) {
        @Override
        public Iterable<String> get(AccountGroup input, FillArgs args) {
          return SchemaUtil.getNameParts(input.getName());
        }
      };

  /** Group description. */
  public static final FieldDef<AccountGroup, String> DESCRIPTION =
      new FieldDef.Single<AccountGroup, String>(
          "description", FieldType.FULL_TEXT, false) {
        @Override
        public String get(AccountGroup input, FillArgs args) {
          return input.getDescription();
        }
      };

  /** Whether the group is visible to all users. */
  public static final FieldDef<AccountGroup, String> IS_VISIBLE_TO_ALL =
      new FieldDef.Single<AccountGroup, String>(
          "is_visible_to_all", FieldType.EXACT, false) {
        @Override
        public String get(AccountGroup input, FillArgs args)
            throws OrmException {
          return input.isVisibleToAll() ? "1" : "0";
        }
      };
}
