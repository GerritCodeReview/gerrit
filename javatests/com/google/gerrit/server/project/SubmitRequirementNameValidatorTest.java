// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link SubmitRequirementsUtil#validateName(String)}. */
@RunWith(JUnit4.class)
public class SubmitRequirementNameValidatorTest {
  @Test
  public void canStartWithSmallLetter() throws Exception {
    SubmitRequirementsUtil.validateName("abc");
  }

  @Test
  public void canStartWithCapitalLetter() throws Exception {
    SubmitRequirementsUtil.validateName("Abc");
  }

  @Test
  public void canBeEqualToOneLetter() throws Exception {
    SubmitRequirementsUtil.validateName("a");
  }

  @Test
  public void cannotStartWithNumber() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> SubmitRequirementsUtil.validateName("98abc"));
  }

  @Test
  public void cannotStartWithHyphen() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> SubmitRequirementsUtil.validateName("-abc"));
  }

  @Test
  public void cannotContainNonAlphanumericOrHyphen() throws Exception {
    assertThrows(
        IllegalArgumentException.class, () -> SubmitRequirementsUtil.validateName("a&^bc"));
  }
}
