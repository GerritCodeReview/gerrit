// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.inject.AbstractModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GerritCommonTest extends PrologTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    final ApprovalTypes types = new ApprovalTypes(Arrays.asList(
        codeReviewCategory(),
        verifiedCategory()
    ));

    load("gerrit", "gerrit_common_test.pl", new AbstractModule() {
      @Override
      protected void configure() {
        bind(ApprovalTypes.class).toInstance(types);
      }
    });
  }

  private static ApprovalType codeReviewCategory() {
    ApprovalCategory cat = category(0, "CRVW", "Code Review");
    List<ApprovalCategoryValue> vals = newList();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    return new ApprovalType(cat, vals);
  }

  private static ApprovalType verifiedCategory() {
    ApprovalCategory cat = category(1, "VRIF", "Verified");
    List<ApprovalCategoryValue> vals = newList();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    return new ApprovalType(cat, vals);
  }

  private static ApprovalCategory category(int pos, String id, String name) {
    ApprovalCategory cat;
    cat = new ApprovalCategory(new ApprovalCategory.Id(id), name);
    cat.setPosition((short) pos);
    return cat;
  }

  private static ArrayList<ApprovalCategoryValue> newList() {
    return new ArrayList<ApprovalCategoryValue>();
  }

  private static ApprovalCategoryValue value(ApprovalCategory c, int v, String n) {
    return new ApprovalCategoryValue(
        new ApprovalCategoryValue.Id(c.getId(), (short) v),
        n);
  }
}
