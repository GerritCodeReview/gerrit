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

package com.google.gerrit.server.query.group;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.group.GroupField;
import java.util.Locale;

/** Utility class to create predicates for group index queries. */
public class GroupPredicates {
  public static Predicate<InternalGroup> id(Schema<InternalGroup> schema, AccountGroup.Id groupId) {
    return new GroupPredicate(
            schema.hasField(GroupField.ID_FIELD_SPEC)
                    ? GroupField.ID_FIELD_SPEC
                    : GroupField.ID_STR_FIELD_SPEC,
            GroupQueryBuilder.FIELD_ID,
            groupId.toString());
  }

  public static Predicate<InternalGroup> uuid(AccountGroup.UUID uuid) {
    return new GroupPredicate(GroupField.UUID_FIELD_SPEC, GroupQueryBuilder.FIELD_UUID, uuid.get());
  }

  public static Predicate<InternalGroup> description(String description) {
    return new GroupPredicate(
        GroupField.DESCRIPTION_SPEC, GroupQueryBuilder.FIELD_DESCRIPTION, description);
  }

  public static Predicate<InternalGroup> inname(String name) {
    return new GroupPredicate(
        GroupField.NAME_PART_SPEC, GroupQueryBuilder.FIELD_INNAME, name.toLowerCase(Locale.US));
  }

  public static Predicate<InternalGroup> name(String name) {
    return new GroupPredicate(GroupField.NAME_SPEC, name);
  }

  public static Predicate<InternalGroup> owner(AccountGroup.UUID ownerUuid) {
    return new GroupPredicate(
        GroupField.OWNER_UUID_SPEC, GroupQueryBuilder.FIELD_OWNER, ownerUuid.get());
  }

  public static Predicate<InternalGroup> isVisibleToAll() {
    return new GroupPredicate(GroupField.IS_VISIBLE_TO_ALL_SPEC, "1");
  }

  public static Predicate<InternalGroup> member(Account.Id memberId) {
    return new GroupPredicate(GroupField.MEMBER_SPEC, memberId.toString());
  }

  public static Predicate<InternalGroup> subgroup(AccountGroup.UUID subgroupUuid) {
    return new GroupPredicate(GroupField.SUBGROUP_SPEC, subgroupUuid.get());
  }

  /** Predicate that is mapped to a field in the group index. */
  static class GroupPredicate extends IndexPredicate<InternalGroup> {
    GroupPredicate(SchemaField<InternalGroup, ?> def, String value) {
      super(def, value);
    }

    GroupPredicate(SchemaField<InternalGroup, ?> def, String name, String value) {
      super(def, name, value);
    }
  }

  private GroupPredicates() {}
}
