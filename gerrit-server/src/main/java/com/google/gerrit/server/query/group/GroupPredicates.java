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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.query.Predicate;
import java.util.List;
import java.util.Locale;

public class GroupPredicates {
  public static Predicate<AccountGroup> defaultPredicate(String query) {
    // Adapt the capacity of this list when adding more default predicates.
    List<Predicate<AccountGroup>> preds = Lists.newArrayListWithCapacity(5);
    preds.add(uuid(new AccountGroup.UUID(query)));
    preds.add(name(query));
    preds.add(inname(query));
    if (!Strings.isNullOrEmpty(query)) {
      preds.add(description(query));
    }
    preds.add(owner(query));
    return Predicate.or(preds);
  }

  public static Predicate<AccountGroup> uuid(AccountGroup.UUID uuid) {
    return new GroupPredicate(GroupField.UUID, GroupQueryBuilder.FIELD_UUID, uuid.get());
  }

  public static Predicate<AccountGroup> description(String description) {
    return new GroupPredicate(
        GroupField.DESCRIPTION, GroupQueryBuilder.FIELD_DESCRIPTION, description);
  }

  public static Predicate<AccountGroup> inname(String name) {
    return new GroupPredicate(
        GroupField.NAME_PART, GroupQueryBuilder.FIELD_INNAME, name.toLowerCase(Locale.US));
  }

  public static Predicate<AccountGroup> name(String name) {
    return new GroupPredicate(
        GroupField.NAME, GroupQueryBuilder.FIELD_NAME, name.toLowerCase(Locale.US));
  }

  public static Predicate<AccountGroup> owner(String owner) {
    return new GroupPredicate(GroupField.OWNER_UUID, GroupQueryBuilder.FIELD_OWNER, owner);
  }

  public static Predicate<AccountGroup> isVisibleToAll() {
    return new GroupPredicate(GroupField.IS_VISIBLE_TO_ALL, "1");
  }

  static class GroupPredicate extends IndexPredicate<AccountGroup> {
    GroupPredicate(FieldDef<AccountGroup, ?> def, String value) {
      super(def, value);
    }

    GroupPredicate(FieldDef<AccountGroup, ?> def, String name, String value) {
      super(def, name, value);
    }
  }

  private GroupPredicates() {}
}
