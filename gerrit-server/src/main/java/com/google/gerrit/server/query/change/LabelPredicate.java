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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.inject.Provider;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LabelPredicate extends OrPredicate<ChangeData> {
  private static final int MAX_LABEL_VALUE = 4;

  private static enum Test {
    EQ, GT_EQ, LT_EQ;

    boolean isEq() {
      return EQ.equals(this);
    }

    boolean isGtEq() {
      return GT_EQ.equals(this);
    }

    static Test op(String op) {
      if ("=".equals(op)) {
        return EQ;

      } else if (">=".equals(op)) {
        return GT_EQ;

      } else if ("<=".equals(op)) {
        return LT_EQ;

      } else {
        throw new IllegalArgumentException("Unsupported operation " + op);
      }
    }
  }

  private final ProjectCache projectCache;
  private final ChangeControl.GenericFactory ccFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<ReviewDb> dbProvider;
  private final String label;
  private final int expVal;
  private final Test test;

  LabelPredicate(ProjectCache projectCache,
      ChangeControl.GenericFactory ccFactory,
      IdentifiedUser.GenericFactory userFactory,
      Provider<ReviewDb> dbProvider,
      String value) {
    super();
    this.ccFactory = ccFactory;
    this.projectCache = projectCache;
    this.userFactory = userFactory;
    this.dbProvider = dbProvider;

    Matcher m1 = Pattern.compile("(=|>=|<=)([+-]?\\d+)$").matcher(value);
    Matcher m2 = Pattern.compile("([+-]\\d+)$").matcher(value);
    if (m1.find()) {
      label = value.substring(0, m1.start());
      test = Test.op(m1.group(1));
      expVal = value(m1.group(2));

    } else if (m2.find()) {
      label = value.substring(0, m2.start());
      test = Test.EQ;
      expVal = value(m2.group(1));

    } else {
      label = value;
      test = Test.EQ;
      expVal = 1;
    }

    List<Predicate<ChangeData>> r =
        Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    if (test.isEq()) {
      if (expVal != 0) {
        r.add(equalsLabelPredicate(expVal));
      } else {
        r.add(noLabelQuery());
      }
    } else {
      for (int i = test.isGtEq() ? expVal : neg(expVal); i <= MAX_LABEL_VALUE; i++) {
        if (i != 0) {
          r.add(equalsLabelPredicate(test.isGtEq() ? i : neg(i)));
        } else {
          r.add(noLabelQuery());
        }
      }
    }
    setChildren(r);
  }

  private static int value(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Integer.parseInt(value);
  }

  private static int neg(int value) {
    return -1 * value;
  }

  private Predicate<ChangeData> noLabelQuery() {
    List<Predicate<ChangeData>> r =
        Lists.newArrayListWithCapacity(2 * MAX_LABEL_VALUE);
    for (int i = 1; i <= MAX_LABEL_VALUE; i++) {
      r.add(not(equalsLabelPredicate(i)));
      r.add(not(equalsLabelPredicate(neg(i))));
    }
    return and(r);
  }

  private Predicate<ChangeData> equalsLabelPredicate(int expVal) {
    return new EqualsLabelPredicate(projectCache, ccFactory, userFactory,
        dbProvider, label, expVal);
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_CHANGE + ":"
        + ChangeField.formatLabel(label, expVal);
  }
}
