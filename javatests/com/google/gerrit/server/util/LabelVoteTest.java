// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.util.LabelVote.parse;
import static com.google.gerrit.server.util.LabelVote.parseWithEquals;

import org.junit.Test;

public class LabelVoteTest {
  @Test
  public void labelVoteParse() {
    assertLabelVoteEquals(parse("Code-Review-2"), "Code-Review", -2);
    assertLabelVoteEquals(parse("Code-Review-1"), "Code-Review", -1);
    assertLabelVoteEquals(parse("-Code-Review"), "Code-Review", 0);
    assertLabelVoteEquals(parse("Code-Review"), "Code-Review", 1);
    assertLabelVoteEquals(parse("Code-Review+1"), "Code-Review", 1);
    assertLabelVoteEquals(parse("Code-Review+2"), "Code-Review", 2);
  }

  @Test
  public void labelVoteFormat() {
    assertThat(parse("Code-Review-2").format()).isEqualTo("Code-Review-2");
    assertThat(parse("Code-Review-1").format()).isEqualTo("Code-Review-1");
    assertThat(parse("-Code-Review").format()).isEqualTo("-Code-Review");
    assertThat(parse("Code-Review+1").format()).isEqualTo("Code-Review+1");
    assertThat(parse("Code-Review+2").format()).isEqualTo("Code-Review+2");
  }

  @Test
  public void labelVoteParseWithEquals() {
    assertLabelVoteEquals(parseWithEquals("Code-Review=-2"), "Code-Review", -2);
    assertLabelVoteEquals(parseWithEquals("Code-Review=-1"), "Code-Review", -1);
    assertLabelVoteEquals(parseWithEquals("Code-Review=0"), "Code-Review", 0);
    assertLabelVoteEquals(parseWithEquals("Code-Review=1"), "Code-Review", 1);
    assertLabelVoteEquals(parseWithEquals("Code-Review=+1"), "Code-Review", 1);
    assertLabelVoteEquals(parseWithEquals("Code-Review=2"), "Code-Review", 2);
    assertLabelVoteEquals(parseWithEquals("Code-Review=+2"), "Code-Review", 2);
    assertLabelVoteEquals(parseWithEquals("R=0"), "R", 0);

    String longName = "A-loooooooooooooooooooooooooooooooooooooooooooooooooong-label";
    // Regression test: an old bug passed the string length as a radix to Short#parseShort.
    assertThat(longName.length()).isGreaterThan(Character.MAX_RADIX);
    assertLabelVoteEquals(parseWithEquals(longName + "=+1"), longName, 1);

    assertParseWithEqualsFails(null);
    assertParseWithEqualsFails("");
    assertParseWithEqualsFails("Code-Review");
    assertParseWithEqualsFails("=1");
    assertParseWithEqualsFails("=.1");
    assertParseWithEqualsFails("=a1");
    assertParseWithEqualsFails("=1a");
    assertParseWithEqualsFails("=.");
  }

  @Test
  public void labelVoteFormatWithEquals() {
    assertThat(parseWithEquals("Code-Review=-2").formatWithEquals()).isEqualTo("Code-Review=-2");
    assertThat(parseWithEquals("Code-Review=-1").formatWithEquals()).isEqualTo("Code-Review=-1");
    assertThat(parseWithEquals("Code-Review=0").formatWithEquals()).isEqualTo("Code-Review=0");
    assertThat(parseWithEquals("Code-Review=+1").formatWithEquals()).isEqualTo("Code-Review=+1");
    assertThat(parseWithEquals("Code-Review=+2").formatWithEquals()).isEqualTo("Code-Review=+2");
  }

  private void assertLabelVoteEquals(LabelVote actual, String expectedLabel, int expectedValue) {
    assertThat(actual.label()).isEqualTo(expectedLabel);
    assertThat((int) actual.value()).isEqualTo(expectedValue);
  }

  private void assertParseWithEqualsFails(String value) {
    try {
      parseWithEquals(value);
      assert_().fail("expected IllegalArgumentException when parsing \"%s\"", value);
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }
}
