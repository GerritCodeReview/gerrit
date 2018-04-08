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

package com.google.gerrit.config;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class GitwebConfigTest {
  private static final String VALID_CHARACTERS = "*()";
  private static final String SOME_INVALID_CHARACTERS = "09AZaz$-_.+!',";

  @Test
  public void validPathSeparator() {
    for (char c : VALID_CHARACTERS.toCharArray()) {
      assertWithMessage("valid character rejected: " + c)
          .that(GitwebConfig.isValidPathSeparator(c))
          .isTrue();
    }
  }

  @Test
  public void inalidPathSeparator() {
    for (char c : SOME_INVALID_CHARACTERS.toCharArray()) {
      assertWithMessage("invalid character accepted: " + c)
          .that(GitwebConfig.isValidPathSeparator(c))
          .isFalse();
    }
  }
}
