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

import static com.google.gerrit.server.query.Predicate.and;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AndPredicateTest extends TestCase {
  private static final class TestPredicate extends OperatorPredicate<String> {
    private TestPredicate(String name, String value) {
      super(name, value);
    }

    @Override
    public boolean match(String object) {
      return false;
    }

    @Override
    public int getCost() {
      return 0;
    }
  }

  private static TestPredicate f(final String name, final String value) {
    return new TestPredicate(name, value);
  }

  @SuppressWarnings("unchecked")
  public void testChildren() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final Predicate<String> n = and(a, b);
    assertEquals(2, n.getChildCount());
    assertSame(a, n.getChild(0));
    assertSame(b, n.getChild(1));
  }

  @SuppressWarnings("unchecked")
  public void testChildrenUnmodifiable() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final Predicate<String> n = and(a, b);

    try {
      n.getChildren().clear();
    } catch (RuntimeException e) {
    }
    assertChildren("clear", n, list(a, b));

    try {
      n.getChildren().remove(0);
    } catch (RuntimeException e) {
    }
    assertChildren("remove(0)", n, list(a, b));

    try {
      n.getChildren().iterator().remove();
    } catch (RuntimeException e) {
    }
    assertChildren("remove(0)", n, list(a, b));
  }

  private static void assertChildren(String o, Predicate<String> p,
      final List<Predicate<String>> l) {
    assertEquals(o + " did not affect child", l, p.getChildren());
  }

  @SuppressWarnings("unchecked")
  public void testToString() {
    final TestPredicate a = f("q", "alice");
    final TestPredicate b = f("q", "bob");
    final TestPredicate c = f("q", "charlie");
    assertEquals("(q:alice q:bob)", and(a, b).toString());
    assertEquals("(q:alice q:bob q:charlie)", and(a, b, c).toString());
  }

  @SuppressWarnings("unchecked")
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

  @SuppressWarnings("unchecked")
  public void testHashCode() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final TestPredicate c = f("author", "charlie");

    assertTrue(and(a, b).hashCode() == and(a, b).hashCode());
    assertTrue(and(a, b, c).hashCode() == and(a, b, c).hashCode());
    assertFalse(and(a, c).hashCode() == and(a, b).hashCode());
  }

  @SuppressWarnings("unchecked")
  public void testCopy() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final TestPredicate c = f("author", "charlie");
    final List<Predicate<String>> s2 = list(a, b);
    final List<Predicate<String>> s3 = list(a, b, c);
    final Predicate<String> n2 = and(a, b);

    assertNotSame(n2, n2.copy(s2));
    assertEquals(s2, n2.copy(s2).getChildren());
    assertEquals(s3, n2.copy(s3).getChildren());

    try {
      n2.copy(Collections.<Predicate<String>> emptyList());
    } catch (IllegalArgumentException e) {
      assertEquals("Need at least two predicates", e.getMessage());
    }
  }

  private static List<Predicate<String>> list(final Predicate<String>... predicates) {
    return Arrays.asList(predicates);
  }
}
