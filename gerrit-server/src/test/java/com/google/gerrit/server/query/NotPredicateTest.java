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
import static com.google.gerrit.server.query.Predicate.not;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;

public class NotPredicateTest extends TestCase {
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

  public void testNotNot() {
    final TestPredicate p = f("author", "bob");
    final Predicate<String> n = not(p);
    assertTrue(n instanceof NotPredicate);
    assertNotSame(p, n);
    assertSame(p, not(n));
  }

  public void testChildren() {
    final TestPredicate p = f("author", "bob");
    final Predicate<String> n = not(p);
    assertEquals(1, n.getChildCount());
    assertSame(p, n.getChild(0));
  }

  public void testChildrenUnmodifiable() {
    final TestPredicate p = f("author", "bob");
    final Predicate<String> n = not(p);

    try {
      n.getChildren().clear();
    } catch (RuntimeException e) {
    }
    assertOnlyChild("clear", p, n);

    try {
      n.getChildren().remove(0);
    } catch (RuntimeException e) {
    }
    assertOnlyChild("remove(0)", p, n);

    try {
      n.getChildren().iterator().remove();
    } catch (RuntimeException e) {
    }
    assertOnlyChild("remove(0)", p, n);
  }

  private static void assertOnlyChild(String o, Predicate<String> c,
      Predicate<String> p) {
    assertEquals(o + " did not affect child", 1, p.getChildCount());
    assertSame(o + " did not affect child", c, p.getChild(0));
  }

  public void testToString() {
    assertEquals("-author:bob", not(f("author", "bob")).toString());
  }

  public void testEquals() {
    assertTrue(not(f("author", "bob")).equals(not(f("author", "bob"))));
    assertFalse(not(f("author", "bob")).equals(not(f("author", "alice"))));
    assertFalse(not(f("author", "bob")).equals(f("author", "bob")));
    assertFalse(not(f("author", "bob")).equals("author"));
  }

  public void testHashCode() {
    assertTrue(not(f("a", "b")).hashCode() == not(f("a", "b")).hashCode());
    assertFalse(not(f("a", "b")).hashCode() == not(f("a", "a")).hashCode());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void testCopy() {
    final TestPredicate a = f("author", "alice");
    final TestPredicate b = f("author", "bob");
    final List<TestPredicate> sa = Collections.singletonList(a);
    final List<TestPredicate> sb = Collections.singletonList(b);
    final Predicate n = not(a);

    assertNotSame(n, n.copy(sa));
    assertEquals(sa, n.copy(sa).getChildren());

    assertNotSame(n, n.copy(sb));
    assertEquals(sb, n.copy(sb).getChildren());

    try {
      n.copy(Collections.<Predicate> emptyList());
    } catch (IllegalArgumentException e) {
      assertEquals("Expected exactly one child", e.getMessage());
    }

    try {
      n.copy(and(a, b).getChildren());
    } catch (IllegalArgumentException e) {
      assertEquals("Expected exactly one child", e.getMessage());
    }
  }
}
