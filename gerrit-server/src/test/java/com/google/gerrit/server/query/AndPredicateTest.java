// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.query;

import static com.google.common.collect.ImmutableList.of;
import static com.google.gerrit.server.query.Predicate.and;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;

public class AndPredicateTest extends PredicateTest {
  @Test
  public void testChildren() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final Predicate<String> n = and(a, b);
    assertEquals(2, n.getChildCount());
    assertSame(a, n.getChild(0));
    assertSame(b, n.getChild(1));
  }

  @Test
  public void testChildrenUnmodifiable() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final Predicate<String> n = and(a, b);

    try {
      n.getChildren().clear();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
    assertChildren("clear", n, of(a, b));

    try {
      n.getChildren().remove(0);
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
    assertChildren("remove(0)", n, of(a, b));

    try {
      n.getChildren().iterator().remove();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
    assertChildren("iterator().remove()", n, of(a, b));
  }

  private static void assertChildren(
      String o, Predicate<String> p, List<? extends Predicate<String>> l) {
    assertEquals(o + " did not affect child", l, p.getChildren());
  }

  @Test
  public void testToString() {
    final TestPredicate a = f("q", "alice");
    final TestPredicate b = f("q", "bob");
    final TestPredicate c = f("q", "charlie");
    assertEquals("(q:alice q:bob)", and(a, b).toString());
    assertEquals("(q:alice q:bob q:charlie)", and(a, b, c).toString());
  }

  @Test
  public void testEquals() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final TestPredicate c = f("author", "charlie");

    assertTrue(and(a, b).equals(and(a, b)));
    assertTrue(and(a, b, c).equals(and(a, b, c)));

    assertFalse(and(a, b).equals(and(b, a)));
    assertFalse(and(a, c).equals(and(a, b)));

    assertFalse(and(a, c).equals(a));
  }

  @Test
  public void testHashCode() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final TestPredicate c = f("author", "charlie");

    assertTrue(and(a, b).hashCode() == and(a, b).hashCode());
    assertTrue(and(a, b, c).hashCode() == and(a, b, c).hashCode());
    assertFalse(and(a, c).hashCode() == and(a, b).hashCode());
  }

  @Test
  public void testCopy() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final TestPredicate c = f("author", "charlie");
    final List<TestPredicate> s2 = of(a, b);
    final List<TestPredicate> s3 = of(a, b, c);
    final Predicate<String> n2 = and(a, b);

    assertNotSame(n2, n2.copy(s2));
    assertEquals(s2, n2.copy(s2).getChildren());
    assertEquals(s3, n2.copy(s3).getChildren());
  }
}
