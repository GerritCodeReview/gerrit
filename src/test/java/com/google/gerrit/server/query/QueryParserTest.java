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

import org.antlr.runtime.tree.Tree;

public class QueryParserTest extends TestCase {
  public void testEmptyQuery() {
    try {
      parse("");
      fail("expected exception");
    } catch (QueryParseException e) {
      assertEquals("line 0:-1 no viable alternative at input '<EOF>'", e.getMessage());
    }
  }

  public void testDefaultSHA1() throws QueryParseException {
    Tree r;

    r = parse("6ea15");
    assertDefault("6ea15", r);

    r = parse("6ea15b73668073fd9f70b2635efcb8cf8aabda22");
    assertDefault("6ea15b73668073fd9f70b2635efcb8cf8aabda22", r);
  }

  public void testOwnerBare() throws QueryParseException {
    Tree r;

    r = parse("owner:bob");
    assertSingleWord("owner", "bob", r);

    r = parse("owner:Bob");
    assertSingleWord("owner", "Bob", r);

    r = parse("owner:bob@example.com");
    assertSingleWord("owner", "bob@example.com", r);
  }

  public void testOwnerExact() throws QueryParseException {
    Tree r;

    r = parse("owner:\"bob\"");
    assertExactPhrase("owner", "bob", r);

    r = parse("owner:\"bob@example.com\"");
    assertExactPhrase("owner", "bob@example.com", r);

    r = parse("owner:\"<bob@example.com>\"");
    assertExactPhrase("owner", "<bob@example.com>", r);

    r = parse("owner:\"A U Thor\"");
    assertExactPhrase("owner", "A U Thor", r);
  }

  public void testProjectBare() throws QueryParseException {
    Tree r;

    r = parse("project:tools/gerrit");
    assertSingleWord("project", "tools/gerrit", r);

    r = parse("project:tools/*");
    assertSingleWord("project", "tools/*", r);
  }

  public void testAnd1() throws QueryParseException {
    Tree r, a, b;

    r = parse(" \t\r\n 6ea15 \t   bob \n\t\r ");
    assertEquals(QueryParser.AND, r.getType());
    assertEquals(2, r.getChildCount());

    a = r.getChild(0);
    b = r.getChild(1);
    assertDefault("6ea15", a);
    assertDefault("bob", b);
  }

  public void testAnd2() throws QueryParseException {
    Tree r, a, b;

    r = parse("6ea15 AND bob");
    assertEquals(QueryParser.AND, r.getType());
    assertEquals(2, r.getChildCount());

    a = r.getChild(0);
    b = r.getChild(1);
    assertDefault("6ea15", a);
    assertDefault("bob", b);
  }

  public void testAnd3() throws QueryParseException {
    Tree r;

    r = parse("6ea15 bob doe");
    assertEquals(QueryParser.AND, r.getType());
    assertEquals(3, r.getChildCount());

    assertDefault("6ea15", r.getChild(0));
    assertDefault("bob", r.getChild(1));
    assertDefault("doe", r.getChild(2));
  }

  public void testAnd4() throws QueryParseException {
    Tree r;

    r = parse("6ea15 AND bob AND doe");
    assertEquals(QueryParser.AND, r.getType());
    assertEquals(3, r.getChildCount());

    assertDefault("6ea15", r.getChild(0));
    assertDefault("bob", r.getChild(1));
    assertDefault("doe", r.getChild(2));
  }

  public void testAnd5() throws QueryParseException {
    Tree r;

    r = parse("approval:(alice AND bob AND doe)");
    assertEquals(QueryParser.FIELD_NAME, r.getType());
    assertEquals("approval", r.getText());
    assertEquals(1, r.getChildCount());

    r = r.getChild(0);
    assertEquals(QueryParser.AND, r.getType());
    assertEquals(3, r.getChildCount());

    assertDefault("alice", r.getChild(0));
    assertDefault("bob", r.getChild(1));
    assertDefault("doe", r.getChild(2));
  }

  private static void assertSingleWord(final String name, final String value, final Tree r) {
    assertEquals(QueryParser.FIELD_NAME, r.getType());
    assertEquals(name, r.getText());
    assertEquals(1, r.getChildCount());
    final Tree c = r.getChild(0);
    assertEquals(QueryParser.SINGLE_WORD, c.getType());
    assertEquals(value, c.getText());
    assertEquals(0, c.getChildCount());
  }

  private static void assertExactPhrase(final String name, final String value, final Tree r) {
    assertEquals(QueryParser.FIELD_NAME, r.getType());
    assertEquals(name, r.getText());
    assertEquals(1, r.getChildCount());
    final Tree c = r.getChild(0);
    assertEquals(QueryParser.EXACT_PHRASE, c.getType());
    assertEquals(value, c.getText());
    assertEquals(0, c.getChildCount());
  }

  private static void assertDefault(final String value, final Tree r) {
    assertEquals(QueryParser.DEFAULT_FIELD, r.getType());
    assertEquals(1, r.getChildCount());
    final Tree c = r.getChild(0);
    assertEquals(QueryParser.SINGLE_WORD, c.getType());
    assertEquals(value, c.getText());
    assertEquals(0, c.getChildCount());
  }

  private static Tree parse(final String str) throws QueryParseException {
    return QueryParser.parse(str);
  }
}
