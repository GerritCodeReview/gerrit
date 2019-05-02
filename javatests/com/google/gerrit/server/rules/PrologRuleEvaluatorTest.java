// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.rules;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PrologRuleEvaluatorTest {

  @Test
  public void validLabelNamesAreKept() {
    for (String labelName : new String[] {"Verified", "Code-Review"}) {
      assertThat(PrologRuleEvaluator.checkLabelName(labelName)).isEqualTo(labelName);
    }
  }

  @Test
  public void labelWithSpacesIsTransformed() {
    assertThat(PrologRuleEvaluator.checkLabelName("Label with spaces"))
        .isEqualTo("Invalid-Prolog-Rules-Label-Name-Labelwithspaces");
  }

  @Test
  public void labelStartingWithADashIsTransformed() {
    assertThat(PrologRuleEvaluator.checkLabelName("-dashed-label"))
        .isEqualTo("Invalid-Prolog-Rules-Label-Name-dashed-label");
  }

  @Test
  public void labelWithInvalidCharactersIsTransformed() {
    assertThat(PrologRuleEvaluator.checkLabelName("*urgent*"))
        .isEqualTo("Invalid-Prolog-Rules-Label-Name-urgent");
  }
}
