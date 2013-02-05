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
import com.google.gerrit.common.data.LabelValue;
import com.google.inject.AbstractModule;

import java.util.Arrays;

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
    String name = "Code-Review";
    return category(0, "CRVW", name,
        value(name, 2, "Looks good to me, approved"),
        value(name, 1, "Looks good to me, but someone else must approve"),
        value(name, 0, "No score"),
        value(name, -1, "I would prefer that you didn't submit this"),
        value(name, -2, "Do not submit"));
  }

  private static ApprovalType verifiedCategory() {
    String name = "Verified";
    return category(1, "VRIF", name,
        value(name, 1, "Verified"),
        value(name, 0, "No score"),
        value(name, -1, "Fails"));
  }

  private static LabelValue value(String labelName, int value, String text) {
    return new LabelValue(labelName, (short) value, text);
  }

  private static ApprovalType category(int pos, String id, String name,
      LabelValue... values) {
    ApprovalType type = new ApprovalType(name, Arrays.asList(values));
    type.setId(id);
    type.setPosition((short) pos);
    return type;
  }
}
