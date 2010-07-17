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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LabelPredicate extends OperatorPredicate<ChangeData> {
  private static enum Test {
    EQ {
      @Override
      public boolean match(PatchSetApproval p, short value) {
        return p.getValue() == value;
      }
    },
    GT_EQ {
      @Override
      public boolean match(PatchSetApproval p, short value) {
        return p.getValue() >= value;
      }
    },
    LT_EQ {
      @Override
      public boolean match(PatchSetApproval p, short value) {
        return p.getValue() <= value;
      }
    };

    abstract boolean match(PatchSetApproval p, short value);
  }

  private static ApprovalCategory.Id category(ApprovalTypes types, String toFind) {
    if (types.getApprovalType(new ApprovalCategory.Id(toFind)) != null) {
      return new ApprovalCategory.Id(toFind);
    }

    for (ApprovalType at : types.getApprovalTypes()) {
      String name = at.getCategory().getName();
      if (toFind.equalsIgnoreCase(name)) {
        return at.getCategory().getId();

      } else if (toFind.equalsIgnoreCase(name.replace(" ", ""))) {
        return at.getCategory().getId();
      }
    }

    for (ApprovalType at : types.getApprovalTypes()) {
      if (toFind.equalsIgnoreCase(at.getCategory().getAbbreviatedName())) {
        return at.getCategory().getId();
      }
    }

    return new ApprovalCategory.Id(toFind);
  }

  private static Test op(String op) {
    if ("=".equals(op)) {
      return Test.EQ;

    } else if (">=".equals(op)) {
      return Test.GT_EQ;

    } else if ("<=".equals(op)) {
      return Test.LT_EQ;

    } else {
      throw new IllegalArgumentException("Unsupported operation " + op);
    }
  }

  private static short value(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Short.parseShort(value);
  }

  private final Provider<ReviewDb> dbProvider;
  private final Test test;
  private final ApprovalCategory.Id category;
  private final short val;

  LabelPredicate(Provider<ReviewDb> dbProvider, ApprovalTypes types,
      String value) {
    super(ChangeQueryBuilder.FIELD_LABEL, value);
    this.dbProvider = dbProvider;

    Matcher m1 = Pattern.compile("(=|>=|<=)([+-]?\\d+)$").matcher(value);
    Matcher m2 = Pattern.compile("([+-]\\d+)$").matcher(value);
    if (m1.find()) {
      category = category(types, value.substring(0, m1.start()));
      test = op(m1.group(1));
      val = value(m1.group(2));

    } else if (m2.find()) {
      category = category(types, value.substring(0, m2.start()));
      test = Test.EQ;
      val = value(m2.group(1));

    } else {
      category = category(types, value);
      test = Test.EQ;
      val = 1;
    }
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    Change c = object.change(dbProvider);
    if (c == null) {
      return false;
    }

    PatchSet.Id current = c.currentPatchSetId();
    for (PatchSetApproval p : object.approvals(dbProvider)) {
      if (p.getPatchSetId().equals(current)
          && p.getCategoryId().equals(category) //
          && test.match(p, val)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int getCost() {
    return 2;
  }
}
