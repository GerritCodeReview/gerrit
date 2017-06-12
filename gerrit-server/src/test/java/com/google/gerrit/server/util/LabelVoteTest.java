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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LabelVoteTest {
  @Test
  public void parse() {
    LabelVote l;
    l = LabelVote.parse("Code-Review-2");
    assertEquals("Code-Review", l.label());
    assertEquals((short) -2, l.value());
    l = LabelVote.parse("Code-Review-1");
    assertEquals("Code-Review", l.label());
    assertEquals((short) -1, l.value());
    l = LabelVote.parse("-Code-Review");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 0, l.value());
    l = LabelVote.parse("Code-Review");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 1, l.value());
    l = LabelVote.parse("Code-Review+1");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 1, l.value());
    l = LabelVote.parse("Code-Review+2");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 2, l.value());
  }

  @Test
  public void format() {
    assertEquals("Code-Review-2", LabelVote.parse("Code-Review-2").format());
    assertEquals("Code-Review-1", LabelVote.parse("Code-Review-1").format());
    assertEquals("-Code-Review", LabelVote.parse("-Code-Review").format());
    assertEquals("Code-Review+1", LabelVote.parse("Code-Review+1").format());
    assertEquals("Code-Review+2", LabelVote.parse("Code-Review+2").format());
  }

  @Test
  public void parseWithEquals() {
    LabelVote l;
    l = LabelVote.parseWithEquals("Code-Review=-2");
    assertEquals("Code-Review", l.label());
    assertEquals((short) -2, l.value());
    l = LabelVote.parseWithEquals("Code-Review=-1");
    assertEquals("Code-Review", l.label());
    assertEquals((short) -1, l.value());
    l = LabelVote.parseWithEquals("Code-Review=0");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 0, l.value());
    l = LabelVote.parseWithEquals("Code-Review=1");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 1, l.value());
    l = LabelVote.parseWithEquals("Code-Review=+1");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 1, l.value());
    l = LabelVote.parseWithEquals("Code-Review=2");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 2, l.value());
    l = LabelVote.parseWithEquals("Code-Review=+2");
    assertEquals("Code-Review", l.label());
    assertEquals((short) 2, l.value());
  }

  @Test
  public void formatWithEquals() {
    assertEquals("Code-Review=-2", LabelVote.parseWithEquals("Code-Review=-2").formatWithEquals());
    assertEquals("Code-Review=-1", LabelVote.parseWithEquals("Code-Review=-1").formatWithEquals());
    assertEquals("Code-Review=0", LabelVote.parseWithEquals("Code-Review=0").formatWithEquals());
    assertEquals("Code-Review=+1", LabelVote.parseWithEquals("Code-Review=+1").formatWithEquals());
    assertEquals("Code-Review=+2", LabelVote.parseWithEquals("Code-Review=+2").formatWithEquals());
  }
}
