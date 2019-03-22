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

import static com.google.gerrit.index.query.QueryParser.FIELD_NAME;
import static com.google.gerrit.index.query.QueryParser.SINGLE_WORD;
import static com.google.gerrit.index.query.QueryParser.parse;
import static com.google.gerrit.index.query.testing.TreeSubject.assertThat;

import com.google.gerrit.testing.GerritBaseTests;
import org.antlr.runtime.tree.Tree;
import org.junit.Test;

public class QueryParserTest extends GerritBaseTests {
  @Test
  public void fieldNameAndValue() throws Exception {
    Tree r = parse("project:tools/gerrit");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("project");
    assertThat(r).hasChildCount(1);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("tools/gerrit");
    assertThat(r).child(0).hasNoChildren();
  }
}
