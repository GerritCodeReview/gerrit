// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.notedb.CheckState.FAILED;
import static com.google.gerrit.server.notedb.CheckState.NOT_RELEVANT;
import static com.google.gerrit.server.notedb.CheckState.RUNNING;
import static com.google.gerrit.server.notedb.CheckState.SUCCESSFUL;

import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class CheckStateTest extends GerritBaseTests {
  @Test
  public void parse() {
    assertThat(CheckState.parse("successful")).isEqualTo(SUCCESSFUL);
    assertThat(CheckState.parse("sUcCessful")).isEqualTo(SUCCESSFUL);
    assertThat(CheckState.parse("SUCCESSFUL")).isEqualTo(SUCCESSFUL);
    assertThat(CheckState.parse("FAILED")).isEqualTo(FAILED);

    assertParseInvalid(null);
    assertParseInvalid("");
    assertParseInvalid("SUCCESS");
    assertParseInvalid(" SUCCESSFUL");
  }

  @Test
  public void combine() {
    assertThat(CheckState.combine(CheckState.values())).isEqualTo(FAILED);
    assertThat(CheckState.combine(RUNNING, SUCCESSFUL)).isEqualTo(RUNNING);
    assertThat(CheckState.combine()).isEqualTo(NOT_RELEVANT);
  }

  private static void assertParseInvalid(String value) {
    try {
      CheckState.parse(value);
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
