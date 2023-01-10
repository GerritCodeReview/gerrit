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

import com.google.common.base.MoreObjects;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.git.ObjectIds;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.SchemaUtil;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Secondary index schemas for groups.
 *
 * <p>Note that this class does not override {@link Object#equals(Object)}. It relies on instances
 * being singletons so that the default (i.e. reference) comparison works.
 */
public class GroupField {
  /** Deprecated - legacy group ID as number. */
  public static final IndexedField<InternalGroup, Integer> ID_FIELD =
      IndexedField.<InternalGroup>integerBuilder("Id").required().build(g -> g.getId().get());

  public static final IndexedField<InternalGroup, Integer>.SearchSpec ID_FIELD_SPEC =
      ID_FIELD.integer("id");

  /** Legacy group ID. */
  public static final IndexedField<InternalGroup, String> ID_STR_FIELD =
      IndexedField.<InternalGroup>stringBuilder("IdStr")
          .stored()
          .required()
          .build(g -> Integer.toString(g.getId().get()));

  public static final IndexedField<InternalGroup, String>.SearchSpec ID_STR_FIELD_SPEC =
      ID_STR_FIELD.exact("id_str");

  /** Group UUID. */
  public static final IndexedField<InternalGroup, String> UUID_FIELD =
      IndexedField.<InternalGroup>stringBuilder("UUID")
          .required()
          .stored()
          .build(g -> g.getGroupUUID().get());

  public static final IndexedField<InternalGroup, String>.SearchSpec UUID_FIELD_SPEC =
      UUID_FIELD.exact("uuid");

  /** Group owner UUID. */
  public static final IndexedField<InternalGroup, String> OWNER_UUID_FIELD =
      IndexedField.<InternalGroup>stringBuilder("OwnerUUID")
          .required()
          .build(g -> g.getOwnerGroupUUID().get());

  public static final IndexedField<InternalGroup, String>.SearchSpec OWNER_UUID_SPEC =
      OWNER_UUID_FIELD.exact("owner_uuid");

  /** Timestamp indicating when this group was created. */
  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final IndexedField<InternalGroup, Timestamp> CREATED_ON_FIELD =
      IndexedField.<InternalGroup>timestampBuilder("CreatedOn")
          .required()
          .build(internalGroup -> Timestamp.from(internalGroup.getCreatedOn()));

  public static final IndexedField<InternalGroup, Timestamp>.SearchSpec CREATED_ON_SPEC =
      CREATED_ON_FIELD.timestamp("created_on");

  /** Group name. */
  public static final IndexedField<InternalGroup, String> NAME_FIELD =
      IndexedField.<InternalGroup>stringBuilder("Name")
          .required()
          .size(200)
          .build(InternalGroup::getName);

  public static final IndexedField<InternalGroup, String>.SearchSpec NAME_SPEC =
      NAME_FIELD.exact("name");

  /** Prefix match on group name parts. */
  public static final IndexedField<InternalGroup, Iterable<String>> NAME_PART_FIELD =
      IndexedField.<InternalGroup>iterableStringBuilder("NamePart")
          .required()
          .size(200)
          .build(g -> SchemaUtil.getNameParts(g.getName()));

  public static final IndexedField<InternalGroup, Iterable<String>>.SearchSpec NAME_PART_SPEC =
      NAME_PART_FIELD.prefix("name_part");

  /** Group description. */
  public static final IndexedField<InternalGroup, String> DESCRIPTION_FIELD =
      IndexedField.<InternalGroup>stringBuilder("Description").build(InternalGroup::getDescription);

  public static final IndexedField<InternalGroup, String>.SearchSpec DESCRIPTION_SPEC =
      DESCRIPTION_FIELD.fullText("description");

  /** Whether the group is visible to all users. */
  public static final IndexedField<InternalGroup, String> IS_VISIBLE_TO_ALL_FIELD =
      IndexedField.<InternalGroup>stringBuilder("IsVisibleToAll")
          .required()
          .size(1)
          .build(g -> g.isVisibleToAll() ? "1" : "0");

  public static final IndexedField<InternalGroup, String>.SearchSpec IS_VISIBLE_TO_ALL_SPEC =
      IS_VISIBLE_TO_ALL_FIELD.exact("is_visible_to_all");

  public static final IndexedField<InternalGroup, Iterable<Integer>> MEMBER_FIELD =
      IndexedField.<InternalGroup>iterableIntegerBuilder("Member")
          .build(g -> g.getMembers().stream().map(Account.Id::get).collect(toImmutableList()));

  public static final IndexedField<InternalGroup, Iterable<Integer>>.SearchSpec MEMBER_SPEC =
      MEMBER_FIELD.integer("member");

  public static final IndexedField<InternalGroup, Iterable<String>> SUBGROUP_FIELD =
      IndexedField.<InternalGroup>iterableStringBuilder("Subgroup")
          .build(
              g ->
                  g.getSubgroups().stream().map(AccountGroup.UUID::get).collect(toImmutableList()));

  public static final IndexedField<InternalGroup, Iterable<String>>.SearchSpec SUBGROUP_SPEC =
      SUBGROUP_FIELD.exact("subgroup");

  /** ObjectId of HEAD:refs/groups/<UUID>. */
  public static final IndexedField<InternalGroup, byte[]> REF_STATE_FIELD =
      IndexedField.<InternalGroup>byteArrayBuilder("RefState")
          .stored()
          .required()
          .build(
              g -> {
                byte[] a = new byte[ObjectIds.STR_LEN];
                MoreObjects.firstNonNull(g.getRefState(), ObjectId.zeroId()).copyTo(a, 0);
                return a;
              });

  public static final IndexedField<InternalGroup, byte[]>.SearchSpec REF_STATE_SPEC =
      REF_STATE_FIELD.storedOnly("ref_state");
}
