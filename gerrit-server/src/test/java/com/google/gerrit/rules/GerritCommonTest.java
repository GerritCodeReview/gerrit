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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.inject.AbstractModule;

import java.util.Arrays;

public class GerritCommonTest extends PrologTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    final LabelTypes types = new LabelTypes(Arrays.asList(
        category(0, "CRVW", "Code-Review",
            value(2, "Looks good to me, approved"),
            value(1, "Looks good to me, but someone else must approve"),
            value(0, "No score"),
            value(-1, "I would prefer that you didn't submit this"),
            value(-2, "Do not submit")),
        category(1, "VRIF", "Verified",
            value(1, "Verified"),
            value(0, "No score"),
            value(-1, "Fails"))
    ));

    load("gerrit", "gerrit_common_test.pl", new AbstractModule() {
      @Override
      protected void configure() {
        bind(LabelTypes.class).toInstance(types);
      }
    });
  }

  private static LabelValue value(int value, String text) {
    return new LabelValue((short) value, text);
  }

  private static LabelType category(int pos, String id, String name,
      LabelValue... values) {
    LabelType type = new LabelType(id, name, Arrays.asList(values));
    type.setPosition((short) pos);
    return type;
  }
}
