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
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupField;
import java.util.Locale;

public class GroupPredicates {
  public static Predicate<InternalGroup> id(AccountGroup.Id groupId) {
    return new GroupPredicate(GroupField.ID, groupId.toString());
  }

  public static Predicate<InternalGroup> uuid(AccountGroup.UUID uuid) {
    return new GroupPredicate(GroupField.UUID, GroupQueryBuilder.FIELD_UUID, uuid.get());
  }

  public static Predicate<InternalGroup> description(String description) {
    return new GroupPredicate(
        GroupField.DESCRIPTION, GroupQueryBuilder.FIELD_DESCRIPTION, description);
  }

  public static Predicate<InternalGroup> inname(String name) {
    return new GroupPredicate(
        GroupField.NAME_PART, GroupQueryBuilder.FIELD_INNAME, name.toLowerCase(Locale.US));
  }

  public static Predicate<InternalGroup> name(String name) {
    return new GroupPredicate(GroupField.NAME, GroupQueryBuilder.FIELD_NAME, name);
  }

  public static Predicate<InternalGroup> owner(AccountGroup.UUID ownerUuid) {
    return new GroupPredicate(
        GroupField.OWNER_UUID, GroupQueryBuilder.FIELD_OWNER, ownerUuid.get());
  }

  public static Predicate<InternalGroup> isVisibleToAll() {
    return new GroupPredicate(GroupField.IS_VISIBLE_TO_ALL, "1");
  }

  public static Predicate<InternalGroup> member(Account.Id memberId) {
    return new GroupPredicate(GroupField.MEMBER, memberId.toString());
  }

  public static Predicate<InternalGroup> subgroup(AccountGroup.UUID subgroupUuid) {
    return new GroupPredicate(GroupField.SUBGROUP, subgroupUuid.get());
  }

  static class GroupPredicate extends IndexPredicate<InternalGroup> {
    GroupPredicate(FieldDef<InternalGroup, ?> def, String value) {
      super(def, value);
    }

    GroupPredicate(FieldDef<InternalGroup, ?> def, String name, String value) {
      super(def, name, value);
    }
  }

  private GroupPredicates() {}
}
