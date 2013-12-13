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
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.util.LabelVote;
import com.google.inject.Provider;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LabelPredicate extends OrPredicate<ChangeData> {
  private static final int MAX_LABEL_VALUE = 4;

  private static enum Test {
    EQ, GT, GT_EQ, LT, LT_EQ;

    static Test op(String op) {
      if ("=".equals(op)) {
        return EQ;

      } else if (">".equals(op)) {
        return GT;

      } else if (">=".equals(op)) {
        return GT_EQ;

      } else if ("<".equals(op)) {
        return LT;

      } else if ("<=".equals(op)) {
        return LT_EQ;

      } else {
        throw new IllegalArgumentException("Unsupported operation " + op);
      }
    }
  }

  private final String value;

  LabelPredicate(ProjectCache projectCache,
      ChangeControl.GenericFactory ccFactory,
      IdentifiedUser.GenericFactory userFactory, Provider<ReviewDb> dbProvider,
      String value, Set<Account.Id> accounts, AccountGroup.UUID group) {
    super(predicates(new Args(projectCache, ccFactory, userFactory, dbProvider,
        value, accounts, group)));
    this.value = value;
  }

  static class Args {
    final ProjectCache projectCache;
    final ChangeControl.GenericFactory ccFactory;
    final IdentifiedUser.GenericFactory userFactory;
    final Provider<ReviewDb> dbProvider;
    final String value;
    final Set<Account.Id> accounts;
    final AccountGroup.UUID group;

    private Args(
        ProjectCache projectCache,
        ChangeControl.GenericFactory ccFactory,
        IdentifiedUser.GenericFactory userFactory,
        Provider<ReviewDb> dbProvider,
        String value,
        Set<Account.Id> accounts,
        AccountGroup.UUID group) {
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
    private Test test;
    private int expVal;

    private Parsed(String label, Test test, int expVal) {
      this.label = label;
      this.test = test;
      this.expVal = expVal;
    }
  }

  private static List<Predicate<ChangeData>> predicates(Args args) {
    String v = args.value;
    Parsed parsed = null;

    try {
      LabelVote lv = LabelVote.parse(v);
      parsed = new Parsed(lv.getLabel(), Test.EQ, lv.getValue());
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    try {
      LabelVote lv = LabelVote.parseWithEquals(v);
      parsed = new Parsed(lv.getLabel(), Test.EQ, lv.getValue());
    } catch (IllegalArgumentException e) {
      // Try next format.
    }

    if (parsed == null) {
      Matcher m = Pattern.compile("(>|>=|<|<=)([+-]?\\d+)$").matcher(v);
      if (m.find()) {
        parsed = new Parsed(v.substring(0, m.start()), Test.op(m.group(1)),
            value(m.group(2)));
      } else {
        parsed = new Parsed(v, Test.EQ, 1);
      }
    }

    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    int min, max;
    switch (parsed.test) {
      case EQ:
      default:
        min = max = parsed.expVal;
        break;
      case GT:
        min = parsed.expVal + 1;
        max = MAX_LABEL_VALUE;
        break;
      case GT_EQ:
        min = parsed.expVal;
        max = MAX_LABEL_VALUE;
        break;
      case LT:
        min = -MAX_LABEL_VALUE;
        max = parsed.expVal - 1;
        break;
      case LT_EQ:
        min = -MAX_LABEL_VALUE;
        max = parsed.expVal;
        break;
    }
    for (int i = min; i <= max; i++) {
      r.add(onePredicate(args, parsed.label, i));
    }
    return r;
  }

  private static Predicate<ChangeData> onePredicate(Args args, String label,
      int expVal) {
    if (expVal != 0) {
      return equalsLabelPredicate(args, label, expVal);
    } else {
      return noLabelQuery(args, label);
    }
  }

  private static int value(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Integer.parseInt(value);
  }

  private static Predicate<ChangeData> noLabelQuery(Args args, String label) {
    List<Predicate<ChangeData>> r =
        Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    for (int i = 1; i <= MAX_LABEL_VALUE; i++) {
      r.add(not(equalsLabelPredicate(args, label, i)));
      r.add(not(equalsLabelPredicate(args, label, -i)));
    }
    return and(r);
  }

  private static Predicate<ChangeData> equalsLabelPredicate(Args args,
      String label, int expVal) {
    if (args.accounts == null || args.accounts.isEmpty()) {
      return new EqualsLabelPredicate(args, label, expVal, null);
    } else {
      List<Predicate<ChangeData>> r = Lists.newArrayList();
      for (Account.Id a : args.accounts) {
        r.add(new EqualsLabelPredicate(args, label, expVal, a));
      }
      return or(r);
    }
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_LABEL + ":" + value;
  }
}
