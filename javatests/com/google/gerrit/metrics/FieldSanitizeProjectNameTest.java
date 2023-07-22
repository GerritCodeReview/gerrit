// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.metrics;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldSanitizeProjectNameTest {
  @Parameterized.Parameters
  public static Collection<Object[]> testData() {
    return Arrays.asList(
        new Object[][] {
          {"repoName", "repoName"},
          {"repo_name", "repo_name"},
          {"repo-name", "repo-name"},
          {"repo/name", "repo_0x2F_name"},
          {"repo+name", "repo_0x2B_name"},
          {"repo_0x2F_name", "repo_0x_0x2F_name"},
        });
  }

  private final String input;
  private final String expected;

  public FieldSanitizeProjectNameTest(String input, String expected) {
    this.input = input;
    this.expected = expected;
  }

  @Test
  public void shouldSanitizeProjectName() {
    Field<String> projectNameField = Field.ofProjectName("test_name").build();
    String result = projectNameField.formatter().apply(input);
    assertThat(result).isEqualTo(expected);
  }
}
