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

package com.google.gerrit.common.data;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class ParameterizedStringTest extends TestCase {
  public void testEmptyString() {
    final ParameterizedString p = new ParameterizedString("");
    assertEquals("", p.getPattern());
    assertEquals("", p.getRawPattern());
    assertTrue(p.getParameterNames().isEmpty());

    final Map<String, String> a = new HashMap<String, String>();
    assertNotNull(p.bind(a));
    assertEquals(0, p.bind(a).length);
    assertEquals("", p.replace(a));
  }

  public void testAsis1() {
    final ParameterizedString p = ParameterizedString.asis("${bar}c");
    assertEquals("${bar}c", p.getPattern());
    assertEquals("${bar}c", p.getRawPattern());
    assertTrue(p.getParameterNames().isEmpty());

    final Map<String, String> a = new HashMap<String, String>();
    a.put("bar", "frobinator");
    assertNotNull(p.bind(a));
    assertEquals(0, p.bind(a).length);
    assertEquals("${bar}c", p.replace(a));
  }

  public void testReplace1() {
    final ParameterizedString p = new ParameterizedString("${bar}c");
    assertEquals("${bar}c", p.getPattern());
    assertEquals("{0}c", p.getRawPattern());
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("bar"));

    final Map<String, String> a = new HashMap<String, String>();
    a.put("bar", "frobinator");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("frobinator", p.bind(a)[0]);
    assertEquals("frobinatorc", p.replace(a));
  }

  public void testReplace2() {
    final ParameterizedString p = new ParameterizedString("a${bar}c");
    assertEquals("a${bar}c", p.getPattern());
    assertEquals("a{0}c", p.getRawPattern());
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("bar"));

    final Map<String, String> a = new HashMap<String, String>();
    a.put("bar", "frobinator");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("frobinator", p.bind(a)[0]);
    assertEquals("afrobinatorc", p.replace(a));
  }

  public void testReplace3() {
    final ParameterizedString p = new ParameterizedString("a${bar}");
    assertEquals("a${bar}", p.getPattern());
    assertEquals("a{0}", p.getRawPattern());
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("bar"));

    final Map<String, String> a = new HashMap<String, String>();
    a.put("bar", "frobinator");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("frobinator", p.bind(a)[0]);
    assertEquals("afrobinator", p.replace(a));
  }

  public void testReplace4() {
    final ParameterizedString p = new ParameterizedString("a${bar}c");
    assertEquals("a${bar}c", p.getPattern());
    assertEquals("a{0}c", p.getRawPattern());
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("bar"));

    final Map<String, String> a = new HashMap<String, String>();
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("", p.bind(a)[0]);
    assertEquals("ac", p.replace(a));
  }

  public void testReplaceToLowerCase() {
    final ParameterizedString p = new ParameterizedString("${a.toLowerCase}");
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("a"));

    final Map<String, String> a = new HashMap<String, String>();

    a.put("a", "foo");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("foo", p.bind(a)[0]);
    assertEquals("foo", p.replace(a));

    a.put("a", "FOO");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("foo", p.bind(a)[0]);
    assertEquals("foo", p.replace(a));
  }

  public void testReplaceToUpperCase() {
    final ParameterizedString p = new ParameterizedString("${a.toUpperCase}");
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("a"));

    final Map<String, String> a = new HashMap<String, String>();

    a.put("a", "foo");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("FOO", p.bind(a)[0]);
    assertEquals("FOO", p.replace(a));

    a.put("a", "FOO");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("FOO", p.bind(a)[0]);
    assertEquals("FOO", p.replace(a));
  }

  public void testReplaceLocalName() {
    final ParameterizedString p = new ParameterizedString("${a.localPart}");
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("a"));

    final Map<String, String> a = new HashMap<String, String>();

    a.put("a", "foo@example.com");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("foo", p.bind(a)[0]);
    assertEquals("foo", p.replace(a));

    a.put("a", "foo");
    assertNotNull(p.bind(a));
    assertEquals(1, p.bind(a).length);
    assertEquals("foo", p.bind(a)[0]);
    assertEquals("foo", p.replace(a));
  }

  public void testUndefinedFunctionName() {
    ParameterizedString p = new ParameterizedString("${a.anUndefinedMethod}");
    assertEquals(1, p.getParameterNames().size());
    assertTrue(p.getParameterNames().contains("a.anUndefinedMethod"));
  }
}
