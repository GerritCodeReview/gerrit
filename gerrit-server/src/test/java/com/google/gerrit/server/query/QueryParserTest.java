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

import org.antlr.runtime.tree.Tree;
import org.junit.Test;

public class QueryParserTest {
  @Test
  public void testProjectBare() throws QueryParseException {
    Tree r;

    r = parse("project:tools/gerrit");
    assertSingleWord("project", "tools/gerrit", r);

    r = parse("project:tools/*");
    assertSingleWord("project", "tools/*", r);
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

  private static Tree parse(final String str) throws QueryParseException {
    return QueryParser.parse(str);
  }
}
