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

import static com.google.gerrit.server.index.FieldDef.exact;
import static com.google.gerrit.server.index.FieldDef.fullText;
import static com.google.gerrit.server.index.FieldDef.integer;
import static com.google.gerrit.server.index.FieldDef.prefix;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.SchemaUtil;

/** Secondary index schemas for groups. */
public class GroupField {
  /** Legacy group ID. */
  public static final FieldDef<AccountGroup, Integer> ID =
      integer("id").build(g -> g.getId().get());

  /** Group UUID. */
  public static final FieldDef<AccountGroup, String> UUID =
      exact("uuid").stored().build(g -> g.getGroupUUID().get());

  /** Group owner UUID. */
  public static final FieldDef<AccountGroup, String> OWNER_UUID =
      exact("owner_uuid").build(g -> g.getOwnerGroupUUID().get());

  /** Group name. */
  public static final FieldDef<AccountGroup, String> NAME =
      exact("name").build(AccountGroup::getName);

  /** Prefix match on group name parts. */
  public static final FieldDef<AccountGroup, Iterable<String>> NAME_PART =
      prefix("name_part").buildRepeatable(g -> SchemaUtil.getNameParts(g.getName()));

  /** Group description. */
  public static final FieldDef<AccountGroup, String> DESCRIPTION =
      fullText("description").build(AccountGroup::getDescription);

  /** Whether the group is visible to all users. */
  public static final FieldDef<AccountGroup, String> IS_VISIBLE_TO_ALL =
      exact("is_visible_to_all").build(g -> g.isVisibleToAll() ? "1" : "0");
}
