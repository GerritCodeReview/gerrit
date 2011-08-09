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
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LabelPredicate extends OperatorPredicate<ChangeData> {
  private static enum Test {
    EQ {
      @Override
      public boolean match(int psValue, int expValue) {
        return psValue == expValue;
      }
    },
    GT_EQ {
      @Override
      public boolean match(int psValue, int expValue) {
        return psValue >= expValue;
      }
    },
    LT_EQ {
      @Override
      public boolean match(int psValue, int expValue) {
        return psValue <= expValue;
      }
    };

    abstract boolean match(int psValue, int expValue);
  }

  private static ApprovalCategory category(ApprovalTypes types, String toFind) {
    if (types.byLabel(toFind) != null) {
      return types.byLabel(toFind).getCategory();
    }

    if (types.byId(new ApprovalCategory.Id(toFind)) != null) {
      return types.byId(new ApprovalCategory.Id(toFind)).getCategory();
    }

    for (ApprovalType at : types.getApprovalTypes()) {
      ApprovalCategory category = at.getCategory();

      if (toFind.equalsIgnoreCase(category.getName())) {
        return category;

      } else if (toFind.equalsIgnoreCase(category.getName().replace(" ", ""))) {
        return category;
      }
    }

    for (ApprovalType at : types.getApprovalTypes()) {
      ApprovalCategory category = at.getCategory();
      if (toFind.equalsIgnoreCase(category.getAbbreviatedName())) {
        return category;
      }
    }

    return new ApprovalCategory(new ApprovalCategory.Id(toFind), toFind);
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

  private static int value(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Integer.parseInt(value);
  }

  private final ChangeControl.GenericFactory ccFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<ReviewDb> dbProvider;
  private final Test test;
  private final ApprovalCategory category;
  private final String permissionName;
  private final int expVal;

  LabelPredicate(ChangeControl.GenericFactory ccFactory,
      IdentifiedUser.GenericFactory userFactory, Provider<ReviewDb> dbProvider,
      ApprovalTypes types, String value) {
    super(ChangeQueryBuilder.FIELD_LABEL, value);
    this.ccFactory = ccFactory;
    this.userFactory = userFactory;
    this.dbProvider = dbProvider;

    Matcher m1 = Pattern.compile("(=|>=|<=)([+-]?\\d+)$").matcher(value);
    Matcher m2 = Pattern.compile("([+-]\\d+)$").matcher(value);
    if (m1.find()) {
      category = category(types, value.substring(0, m1.start()));
      test = op(m1.group(1));
      expVal = value(m1.group(2));

    } else if (m2.find()) {
      category = category(types, value.substring(0, m2.start()));
      test = Test.EQ;
      expVal = value(m2.group(1));

    } else {
      category = category(types, value);
      test = Test.EQ;
      expVal = 1;
    }

    this.permissionName = Permission.forLabel(category.getLabelName());
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    for (PatchSetApproval p : object.currentApprovals(dbProvider)) {
      if (p.getCategoryId().equals(category.getId())) {
        int psVal = p.getValue();
        if (test.match(psVal, expVal)) {
          // Double check the value is still permitted for the user.
          //
          try {
            ChangeControl cc = ccFactory.controlFor(object.change(dbProvider), //
                userFactory.create(dbProvider, p.getAccountId()));
            if (!cc.isVisible(dbProvider.get())) {
              // The user can't see the change anymore.
              //
              continue;
            }
            psVal = cc.getRange(permissionName).squash(psVal);
          } catch (NoSuchChangeException e) {
            // The project has disappeared.
            //
            continue;
          }

          if (test.match(psVal, expVal)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 2;
  }
}
