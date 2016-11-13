// Copyright (C) 2014 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.util.List;
import org.junit.Test;

public class RegexListSearcherTest {
  private static final List<String> EMPTY = ImmutableList.of();

  @Test
  public void emptyList() {
    assertSearchReturns(EMPTY, "pat", EMPTY);
  }

  @Test
  public void hasMatch() {
    List<String> list = ImmutableList.of("bar", "foo", "quux");
    assertTrue(RegexListSearcher.ofStrings("foo").hasMatch(list));
    assertFalse(RegexListSearcher.ofStrings("xyz").hasMatch(list));
  }

  @Test
  public void anchors() {
    List<String> list = ImmutableList.of("foo");
    assertSearchReturns(list, "^f.*", list);
    assertSearchReturns(list, "^f.*o$", list);
    assertSearchReturns(list, "f.*o$", list);
    assertSearchReturns(list, "f.*o$", list);
    assertSearchReturns(EMPTY, "^.*\\$", list);
  }

  @Test
  public void noCommonPrefix() {
    List<String> list = ImmutableList.of("bar", "foo", "quux");
    assertSearchReturns(ImmutableList.of("foo"), "f.*", list);
    assertSearchReturns(ImmutableList.of("foo"), ".*o.*", list);
    assertSearchReturns(ImmutableList.of("bar", "foo", "quux"), ".*[aou].*", list);
  }

  @Test
  public void commonPrefix() {
    List<String> list = ImmutableList.of("bar", "baz", "foo1", "foo2", "foo3", "quux");
    assertSearchReturns(ImmutableList.of("bar", "baz"), "b.*", list);
    assertSearchReturns(ImmutableList.of("foo1", "foo2"), "foo[12]", list);
    assertSearchReturns(ImmutableList.of("foo1", "foo2", "foo3"), "foo.*", list);
    assertSearchReturns(ImmutableList.of("quux"), "q.*", list);
  }

  private void assertSearchReturns(List<?> expected, String re, List<String> inputs) {
    assertTrue(Ordering.natural().isOrdered(inputs));
    assertEquals(expected, ImmutableList.copyOf(RegexListSearcher.ofStrings(re).search(inputs)));
  }
}
