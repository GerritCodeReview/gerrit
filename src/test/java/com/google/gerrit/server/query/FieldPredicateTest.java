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

import junit.framework.TestCase;

public class FieldPredicateTest extends TestCase {
  private static OperatorPredicate f(final String name, final String value) {
    return new OperatorPredicate(name, value);
  }

  public void testToString() {
    assertEquals("author:bob", f("author", "bob").toString());
    assertEquals("author:\"\"", f("author", "").toString());
    assertEquals("owner:\"A U Thor\"", f("owner", "A U Thor").toString());
  }

  public void testEquals() {
    assertTrue(f("author", "bob").equals(f("author", "bob")));
    assertFalse(f("author", "bob").equals(f("author", "alice")));
    assertFalse(f("owner", "bob").equals(f("author", "bob")));
    assertFalse(f("author", "bob").equals("author"));
  }

  public void testHashCode() {
    assertTrue(f("a", "bob").hashCode() == f("a", "bob").hashCode());
    assertFalse(f("a", "bob").hashCode() == f("a", "alice").hashCode());
  }

  public void testNameValue() {
    final String name = "author";
    final String value = "alice";
    final OperatorPredicate f = f(name, value);
    assertSame(name, f.getOperator());
    assertSame(value, f.getValue());
    assertEquals(0, f.getChildren().size());
  }
}
