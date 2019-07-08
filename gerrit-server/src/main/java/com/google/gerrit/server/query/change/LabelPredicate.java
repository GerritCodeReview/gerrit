// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.RangeUtil;
import com.google.gerrit.server.util.RangeUtil.Range;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LabelPredicate extends OrPredicate<ChangeData> {
  private static final int MAX_LABEL_VALUE = 4;

  static class Args {
    final FieldDef<ChangeData, ?> field;
    final ProjectCache projectCache;
    final ChangeControl.GenericFactory ccFactory;
    final IdentifiedUser.GenericFactory userFactory;
    final Provider<ReviewDb> dbProvider;
    final String value;
    final Set<Account.Id> accounts;
    final AccountGroup.UUID group;

    private Args(
        FieldDef<ChangeData, ?> field,
        ProjectCache projectCache,
        ChangeControl.GenericFactory ccFactory,
        IdentifiedUser.GenericFactory userFactory,
        Provider<ReviewDb> dbProvider,
        String value,
        Set<Account.Id> accounts,
        AccountGroup.UUID group) {
      this.field = field;
      this.projectCache = projectCache;
      this.ccFactory = ccFactory;
      this.userFactory = userFactory;
      this.dbProvider = dbProvider;
      this.value = value;
      this.accounts = accounts;
      this.group = group;
    }
  }

  private static class Parsed {
    private final String label;
    private final String test;
    private final int expVal;

    private Parsed(String label, String test, int expVal) {
      this.label = label;
      this.test = test;
      this.expVal = expVal;
    }
  }

  private final String value;

  @SuppressWarnings("deprecation")
  LabelPredicate(
      ChangeQueryBuilder.Arguments a,
      String value,
      Set<Account.Id> accounts,
      AccountGroup.UUID group) {
    super(
        predicates(
            new Args(
                a.getSchema().getField(ChangeField.LABEL2, ChangeField.LABEL).get(),
                a.projectCache,
                a.changeControlGenericFactory,
                a.userFactory,
                a.db,
                value,
                accounts,
                group)));
    this.value = value;
  }

  private static List<Predicate<ChangeData>> predicates(Args args) {
    String v = args.value;
    Parsed parsed = null;

    try {
      LabelVote lv = LabelVote.parse(v);
      parsed = new Parsed(lv.label(), "=", lv.value());
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    try {
      LabelVote lv = LabelVote.parseWithEquals(v);
      parsed = new Parsed(lv.label(), "=", lv.value());
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    Range range;
    if (parsed == null) {
      range = RangeUtil.getRange(v, -MAX_LABEL_VALUE, MAX_LABEL_VALUE);
      if (range == null) {
        range = new Range(v, 1, 1);
      }
    } else {
      range =
          RangeUtil.getRange(
              parsed.label, parsed.test, parsed.expVal, -MAX_LABEL_VALUE, MAX_LABEL_VALUE);
    }
    String prefix = range.prefix;
    int min = range.min;
    int max = range.max;

    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(max - min + 1);
    for (int i = min; i <= max; i++) {
      r.add(onePredicate(args, prefix, i));
    }
    return r;
  }

  private static Predicate<ChangeData> onePredicate(Args args, String label, int expVal) {
    if (expVal != 0) {
      return equalsLabelPredicate(args, label, expVal);
    }
    return noLabelQuery(args, label);
  }

  private static Predicate<ChangeData> noLabelQuery(Args args, String label) {
    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    for (int i = 1; i <= MAX_LABEL_VALUE; i++) {
      r.add(equalsLabelPredicate(args, label, i));
      r.add(equalsLabelPredicate(args, label, -i));
    }
    return not(or(r));
  }

  private static Predicate<ChangeData> equalsLabelPredicate(Args args, String label, int expVal) {
    if (args.accounts == null || args.accounts.isEmpty()) {
      return new EqualsLabelPredicate(args, label, expVal, null);
    }
    List<Predicate<ChangeData>> r = new ArrayList<>();
    for (Account.Id a : args.accounts) {
      r.add(new EqualsLabelPredicate(args, label, expVal, a));
    }
    return or(r);
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_LABEL + ":" + value;
  }
}
