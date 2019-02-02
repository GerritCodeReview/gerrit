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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.index.FieldDef.exact;
import static com.google.gerrit.index.FieldDef.fullText;
import static com.google.gerrit.index.FieldDef.integer;
import static com.google.gerrit.index.FieldDef.prefix;
import static com.google.gerrit.index.FieldDef.storedOnly;
import static com.google.gerrit.index.FieldDef.timestamp;

import com.google.common.base.MoreObjects;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.InternalGroup;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/** Secondary index schemas for groups. */
public class GroupField {
  /** Legacy group ID. */
  public static final FieldDef<InternalGroup, Integer> ID =
      integer("id").build(g -> g.getId().get());

  /** Group UUID. */
  public static final FieldDef<InternalGroup, String> UUID =
      exact("uuid").sorted().stored().build(g -> g.getGroupUUID().get());

  /** Group owner UUID. */
  public static final FieldDef<InternalGroup, String> OWNER_UUID =
      exact("owner_uuid").build(g -> g.getOwnerGroupUUID().get());

  /** Timestamp indicating when this group was created. */
  public static final FieldDef<InternalGroup, Timestamp> CREATED_ON =
      timestamp("created_on").build(InternalGroup::getCreatedOn);

  /** Group name. */
  public static final FieldDef<InternalGroup, String> NAME =
      exact("name").build(InternalGroup::getName);

  /** Prefix match on group name parts. */
  public static final FieldDef<InternalGroup, Iterable<String>> NAME_PART =
      prefix("name_part").buildRepeatable(g -> SchemaUtil.getNameParts(g.getName()));

  /** Group description. */
  public static final FieldDef<InternalGroup, String> DESCRIPTION =
      fullText("description").build(InternalGroup::getDescription);

  /** Whether the group is visible to all users. */
  public static final FieldDef<InternalGroup, String> IS_VISIBLE_TO_ALL =
      exact("is_visible_to_all").build(g -> g.isVisibleToAll() ? "1" : "0");

  public static final FieldDef<InternalGroup, Iterable<Integer>> MEMBER =
      integer("member")
          .buildRepeatable(
              g -> g.getMembers().stream().map(Account.Id::get).collect(toImmutableList()));

  public static final FieldDef<InternalGroup, Iterable<String>> SUBGROUP =
      exact("subgroup")
          .buildRepeatable(
              g ->
                  g.getSubgroups().stream().map(AccountGroup.UUID::get).collect(toImmutableList()));

  /** ObjectId of HEAD:refs/groups/<UUID>. */
  public static final FieldDef<InternalGroup, byte[]> REF_STATE =
      storedOnly("ref_state")
          .build(
              g -> {
                byte[] a = new byte[Constants.OBJECT_ID_STRING_LENGTH];
                MoreObjects.firstNonNull(g.getRefState(), ObjectId.zeroId()).copyTo(a, 0);
                return a;
              });
}
