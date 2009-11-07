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

import static com.google.gerrit.server.query.Predicate.not;

import junit.framework.TestCase;

public class NotPredicateTest extends TestCase {
  private static OperatorPredicate f(final String name, final String value) {
    return new OperatorPredicate(name, value);
  }

  public void testNotNot() {
    final OperatorPredicate p = f("author", "bob");
    final Predicate n = p.not();
    assertTrue(n instanceof NotPredicate);
    assertNotSame(p, n);
    assertSame(p, n.not());
  }

  public void testChildren() {
    final OperatorPredicate p = f("author", "bob");
    final Predicate n = p.not();
    assertEquals(1, n.getChildCount());
    assertSame(p, n.getChild(0));
  }

  public void testChildrenUnmodifiable() {
    final OperatorPredicate p = f("author", "bob");
    final Predicate n = p.not();

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

  private static void assertOnlyChild(String o, Predicate c, Predicate p) {
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
}
