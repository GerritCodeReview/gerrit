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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import java.util.HashSet;
import java.util.Set;
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

  private static LabelType type(LabelTypes types, String toFind) {
    if (types.byLabel(toFind) != null) {
      return types.byLabel(toFind);
    }

    if (types.byId(toFind) != null) {
      return types.byId(toFind);
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getName())) {
        return lt;
      }
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getAbbreviatedName())) {
        return lt;
      }
    }

    return LabelType.withDefaultValues(toFind.substring(0, 4), toFind);
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

  private final ProjectCache projectCache;
  private final ChangeControl.GenericFactory ccFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<ReviewDb> dbProvider;
  private final Test test;
  private final String type;
  private final int expVal;

  LabelPredicate(ProjectCache projectCache,
      ChangeControl.GenericFactory ccFactory,
      IdentifiedUser.GenericFactory userFactory,
      Provider<ReviewDb> dbProvider,
      String value) {
    super(ChangeQueryBuilder.FIELD_LABEL, value);
    this.ccFactory = ccFactory;
    this.projectCache = projectCache;
    this.userFactory = userFactory;
    this.dbProvider = dbProvider;

    Matcher m1 = Pattern.compile("(=|>=|<=)([+-]?\\d+)$").matcher(value);
    Matcher m2 = Pattern.compile("([+-]\\d+)$").matcher(value);
    if (m1.find()) {
      type = value.substring(0, m1.start());
      test = op(m1.group(1));
      expVal = value(m1.group(2));

    } else if (m2.find()) {
      type = value.substring(0, m2.start());
      test = Test.EQ;
      expVal = value(m2.group(1));

    } else {
      type = value;
      test = Test.EQ;
      expVal = 1;
    }
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
    final Change c = object.change(dbProvider);
    final ProjectState project = projectCache.get(c.getDest().getParentKey());
    if (project == null) {
      // The project has disappeared.
      //
      return false;
    }
    final LabelType labelType = type(project.getLabelTypes(), type);
    final Set<Account.Id> allApprovers = new HashSet<Account.Id>();
    final Set<Account.Id> approversThatVotedInCategory = new HashSet<Account.Id>();
    for (PatchSetApproval p : object.currentApprovals(dbProvider)) {
      allApprovers.add(p.getAccountId());
      if (p.getCategoryId().get().equals(labelType.getId())) {
        approversThatVotedInCategory.add(p.getAccountId());
        if (match(c, p.getValue(), p.getAccountId(), labelType)) {
          return true;
        }
      }
    }

    final Set<Account.Id> approversThatDidNotVoteInCategory = new HashSet<Account.Id>(allApprovers);
    approversThatDidNotVoteInCategory.removeAll(approversThatVotedInCategory);
    for (Account.Id a : approversThatDidNotVoteInCategory) {
      if (match(c, 0, a, labelType)) {
        return true;
      }
    }

    return false;
  }

  private boolean match(final Change change, final int value,
      final Account.Id approver, final LabelType type)
      throws OrmException {
    int psVal = value;
    if (test.match(psVal, expVal)) {
      // Double check the value is still permitted for the user.
      //
      try {
        ChangeControl cc = ccFactory.controlFor(change, //
            userFactory.create(dbProvider, approver));
        if (!cc.isVisible(dbProvider.get())) {
          // The user can't see the change anymore.
          //
          return false;
        }
        psVal = cc.getRange(Permission.forLabel(type.getName())).squash(psVal);
      } catch (NoSuchChangeException e) {
        // The project has disappeared.
        //
        return false;
      }

      if (test.match(psVal, expVal)) {
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
