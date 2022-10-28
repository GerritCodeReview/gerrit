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

package com.google.gerrit.index.query;

import static com.google.gerrit.index.query.Predicate.and;
import static com.google.gerrit.index.query.Predicate.not;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NotPredicateTest extends PredicateTest {
  @Test
  public void notNot() {
    final TestPredicate<String> p = f("author", "bob");
    final Predicate<String> n = not(p);
    assertTrue(n instanceof NotPredicate);
    assertNotSame(p, n);
    assertSame(p, not(n));
  }

  @Test
  public void children() {
    final TestPredicate<String> p = f("author", "bob");
    final Predicate<String> n = not(p);
    assertEquals(1, n.getChildCount());
    assertSame(p, n.getChild(0));
  }

  @Test
  public void childrenUnmodifiable() {
    final TestPredicate<String> p = f("author", "bob");
    final Predicate<String> n = not(p);

    assertThrows(UnsupportedOperationException.class, () -> n.getChildren().clear());
    assertOnlyChild("clear", p, n);

    assertThrows(UnsupportedOperationException.class, () -> n.getChildren().remove(0));
    assertOnlyChild("remove(0)", p, n);

    assertThrows(UnsupportedOperationException.class, () -> n.getChildren().iterator().remove());
    assertOnlyChild("remove()", p, n);
  }

  private static void assertOnlyChild(String o, Predicate<String> c, Predicate<String> p) {
    assertEquals(o + " did not affect child", 1, p.getChildCount());
    assertSame(o + " did not affect child", c, p.getChild(0));
  }

  @Test
  public void testToString() {
    assertEquals("-author:bob", not(f("author", "bob")).toString());
  }

  @SuppressWarnings("unlikely-arg-type")
  @Test
  public void testEquals() {
    assertTrue(not(f("author", "bob")).equals(not(f("author", "bob"))));
    assertFalse(not(f("author", "bob")).equals(not(f("author", "alice"))));
    assertFalse(not(f("author", "bob")).equals(f("author", "bob")));
    assertFalse(not(f("author", "bob")).equals("author"));
  }

  @Test
  public void testHashCode() {
    assertTrue(not(f("a", "b")).hashCode() == not(f("a", "b")).hashCode());
    assertFalse(not(f("a", "b")).hashCode() == not(f("a", "a")).hashCode());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void testCopy() {
    final TestPredicate<String> a = f("author", "alice");
    final TestPredicate<String> b = f("author", "bob");
    final List<TestPredicate<String>> sa = Collections.singletonList(a);
    final List<TestPredicate<String>> sb = Collections.singletonList(b);
    final Predicate n = not(a);

    assertNotSame(n, n.copy(sa));
    assertEquals(sa, n.copy(sa).getChildren());

    assertNotSame(n, n.copy(sb));
    assertEquals(sb, n.copy(sb).getChildren());

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> n.copy(Collections.emptyList()));
    assertEquals("Expected exactly one child", e.getMessage());

    e = assertThrows(IllegalArgumentException.class, () -> n.copy(and(a, b).getChildren()));
    assertEquals("Expected exactly one child", e.getMessage());
  }
}
