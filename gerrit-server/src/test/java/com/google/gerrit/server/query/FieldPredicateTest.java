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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class FieldPredicateTest extends PredicateTest {
  @Test
  public void testToString() {
    assertEquals("author:bob", f("author", "bob").toString());
    assertEquals("author:\"\"", f("author", "").toString());
    assertEquals("owner:\"A U Thor\"", f("owner", "A U Thor").toString());
  }

  @SuppressWarnings("unlikely-arg-type")
  @Test
  public void testEquals() {
    assertTrue(f("author", "bob").equals(f("author", "bob")));
    assertFalse(f("author", "bob").equals(f("author", "alice")));
    assertFalse(f("owner", "bob").equals(f("author", "bob")));
    assertFalse(f("author", "bob").equals("author"));
  }

  @Test
  public void testHashCode() {
    assertTrue(f("a", "bob").hashCode() == f("a", "bob").hashCode());
    assertFalse(f("a", "bob").hashCode() == f("a", "alice").hashCode());
  }

  @Test
  public void nameValue() {
    final String name = "author";
    final String value = "alice";
    final OperatorPredicate<String> f = f(name, value);
    assertSame(name, f.getOperator());
    assertSame(value, f.getValue());
    assertEquals(0, f.getChildren().size());
  }

  @Test
  public void testCopy() {
    final OperatorPredicate<String> f = f("author", "alice");
    assertSame(f, f.copy(Collections.<Predicate<String>>emptyList()));
    assertSame(f, f.copy(f.getChildren()));

    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Expected 0 children");
    f.copy(Collections.singleton(f("owner", "bob")));
  }
}
