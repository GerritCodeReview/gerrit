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
import com.google.gerrit.server.index.RangeUtil;
import com.google.gerrit.server.index.RangeUtil.Range;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LabelPredicate extends OrPredicate<ChangeData> {
  protected static final int MAX_LABEL_VALUE = 4;

  protected static class Args {
    protected final ProjectCache projectCache;
    protected final PermissionBackend permissionBackend;
    protected final ChangeControl.GenericFactory ccFactory;
    protected final IdentifiedUser.GenericFactory userFactory;
    protected final Provider<ReviewDb> dbProvider;
    protected final String value;
    protected final Set<Account.Id> accounts;
    protected final AccountGroup.UUID group;

    protected Args(
        ProjectCache projectCache,
        PermissionBackend permissionBackend,
        ChangeControl.GenericFactory ccFactory,
        IdentifiedUser.GenericFactory userFactory,
        Provider<ReviewDb> dbProvider,
        String value,
        Set<Account.Id> accounts,
        AccountGroup.UUID group) {
      this.projectCache = projectCache;
      this.permissionBackend = permissionBackend;
      this.ccFactory = ccFactory;
      this.userFactory = userFactory;
      this.dbProvider = dbProvider;
      this.value = value;
      this.accounts = accounts;
      this.group = group;
    }
  }

  protected static class Parsed {
    protected final String label;
    protected final String test;
    protected final int expVal;

    protected Parsed(String label, String test, int expVal) {
      this.label = label;
      this.test = test;
      this.expVal = expVal;
    }
  }

  protected final String value;

  public LabelPredicate(
      ChangeQueryBuilder.Arguments a,
      String value,
      Set<Account.Id> accounts,
      AccountGroup.UUID group) {
    super(
        predicates(
            new Args(
                a.projectCache,
                a.permissionBackend,
                a.changeControlGenericFactory,
                a.userFactory,
                a.db,
                value,
                accounts,
                group)));
    this.value = value;
  }

  protected static List<Predicate<ChangeData>> predicates(Args args) {
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

  protected static Predicate<ChangeData> onePredicate(Args args, String label, int expVal) {
    if (expVal != 0) {
      return equalsLabelPredicate(args, label, expVal);
    }
    return noLabelQuery(args, label);
  }

  protected static Predicate<ChangeData> noLabelQuery(Args args, String label) {
    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    for (int i = 1; i <= MAX_LABEL_VALUE; i++) {
      r.add(equalsLabelPredicate(args, label, i));
      r.add(equalsLabelPredicate(args, label, -i));
    }
    return not(or(r));
  }

  protected static Predicate<ChangeData> equalsLabelPredicate(Args args, String label, int expVal) {
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
