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

package com.google.gerrit.rules.common;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.rules.PrologTestCase;
import com.google.inject.AbstractModule;

import java.util.ArrayList;
import java.util.List;

public class CommonRulesTest extends PrologTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    List<ApprovalType> typeList = new ArrayList<ApprovalType>();
    typeList.add(codeReviewCategory());
    typeList.add(verifiedCategory());
    final ApprovalTypes types = new ApprovalTypes(typeList);

    load("common_rules_test.pl", new AbstractModule() {
      @Override
      protected void configure() {
        bind(ApprovalTypes.class).toInstance(types);
      }
    });
  }

  private static ApprovalType codeReviewCategory() {
    ApprovalCategory cat;
    ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    cat.setPosition((short) 0);
    cat.setAbbreviatedName("R");
    cat.setCopyMinScore(true);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    return new ApprovalType(cat, vals);
  }

  private static ApprovalType verifiedCategory() {
    ApprovalCategory cat;
    ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 1);
    cat.setAbbreviatedName("V");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    return new ApprovalType(cat, vals);
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
