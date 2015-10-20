// Copyright (C) 2015 The Android Open Source Project
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
package com.google.gerrit.extensions.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public class DiffInfoTest {

  private enum ContentType {
    A, B, AB
  }
  private static DiffInfo.ContentEntry createContent(ContentType type,
    List<String> lines) {

    DiffInfo.ContentEntry entry = new DiffInfo.ContentEntry();
    switch (type) {
      case A:
        entry.a = lines;
        break;
      case B:
        entry.b = lines;
        break;
      case AB:
        entry.ab = lines;
        break;
      default: throw new IllegalStateException("unknown content type " + type);
    }
    return entry;
  }

  private static void testOnly(ContentType type) {
    List<DiffInfo.ContentEntry> testData = new ArrayList<>();
    List<String> lines =
      ImmutableList.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9");
    for (Iterable<String> group : Iterables.partition(lines, 3)) {
      testData.add(createContent(type, Lists.newArrayList(group)));
    }
    DiffInfo diff = new DiffInfo();
    diff.content = testData;
    Assert.assertEquals(lines.size(), Iterables.size(diff.getDiffLines()));
  }

  @Test
  public void testDiffLinesEmpty() {
    DiffInfo diff = new DiffInfo();
    Assert.assertEquals(0, Iterables.size(diff.getDiffLines()));
    DiffInfo diff2 = new DiffInfo();
    diff2.content = Collections.emptyList();
    Assert.assertEquals(0, Iterables.size(diff.getDiffLines()));
  }

  @Test(expected = NoSuchElementException.class)
  public void testOverflow() {
    DiffInfo diff = new DiffInfo();
    diff.getDiffLines().iterator().next();
  }

  @Test
  public void testDiffLinesIteration() {
    List<DiffInfo.ContentEntry> testData = new ArrayList<>();
    testData.add(createContent(ContentType.A, ImmutableList.of("a1", "a2", "a3")));
    testData.add(createContent(ContentType.AB, ImmutableList.of("ab1", "ab2", "ab3")));
    testData.add(createContent(ContentType.A, ImmutableList.of("a4", "a5", "a6")));
    testData.add(createContent(ContentType.B, ImmutableList.of("b1", "b2", "b3")));
    DiffInfo diff = new DiffInfo();
    diff.content = testData;
    Assert.assertEquals(12, Iterables.size(diff.getDiffLines()));
  }

  @Test
  public void testOnlyAs() {
    testOnly(ContentType.A);
  }

  @Test
  public void testOnlyBs() {
    testOnly(ContentType.A);
  }

  @Test
  public void testOnlyABs() {
    testOnly(ContentType.A);
  }
}
