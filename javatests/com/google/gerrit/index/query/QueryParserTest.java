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

import static com.google.gerrit.index.query.QueryParser.AND;
import static com.google.gerrit.index.query.QueryParser.COLON;
import static com.google.gerrit.index.query.QueryParser.DEFAULT_FIELD;
import static com.google.gerrit.index.query.QueryParser.EXACT_PHRASE;
import static com.google.gerrit.index.query.QueryParser.FIELD_NAME;
import static com.google.gerrit.index.query.QueryParser.SINGLE_WORD;
import static com.google.gerrit.index.query.QueryParser.parse;
import static com.google.gerrit.index.query.testing.TreeSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import org.antlr.runtime.tree.Tree;
import org.junit.Test;

public class QueryParserTest {
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

  @Test
  public void fieldNameAndValueThatLooksLikeFieldNameColon() throws Exception {
    // This should work, but doesn't due to a known issue.
    assertParseFails("project:foo:");
  }

  @Test
  public void fieldNameAndValueThatLooksLikeFieldNameColonValue() throws Exception {
    Tree r = parse("project:foo:bar");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("project");
    assertThat(r).hasChildCount(3);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("foo");
    assertThat(r).child(0).hasNoChildren();
    assertThat(r).child(1).hasType(COLON);
    assertThat(r).child(1).hasText(":");
    assertThat(r).child(1).hasNoChildren();
    assertThat(r).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(2).hasText("bar");
    assertThat(r).child(2).hasNoChildren();
  }

  @Test
  public void fieldNameAndValueThatLooksLikeWordColonValue() throws Exception {
    Tree r = parse("project:x*y:a*b");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("project");
    assertThat(r).hasChildCount(3);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("x*y");
    assertThat(r).child(0).hasNoChildren();
    assertThat(r).child(1).hasType(COLON);
    assertThat(r).child(1).hasText(":");
    assertThat(r).child(1).hasNoChildren();
    assertThat(r).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(2).hasText("a*b");
    assertThat(r).child(2).hasNoChildren();
  }

  @Test
  public void fieldNameAndValueThatLooksLikeWordColon() throws Exception {
    // This should work, but doesn't due to a known issue.
    assertParseFails("project:x*y:");
  }

  @Test
  public void fieldNameAndValueWithMultipleColons() throws Exception {
    Tree r = parse("project:*:*:*");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("project");
    assertThat(r).hasChildCount(5);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("*");
    assertThat(r).child(0).hasNoChildren();
    assertThat(r).child(1).hasType(COLON);
    assertThat(r).child(1).hasText(":");
    assertThat(r).child(1).hasNoChildren();
    assertThat(r).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(2).hasText("*");
    assertThat(r).child(2).hasNoChildren();
    assertThat(r).child(3).hasType(COLON);
    assertThat(r).child(3).hasText(":");
    assertThat(r).child(3).hasNoChildren();
    assertThat(r).child(4).hasType(SINGLE_WORD);
    assertThat(r).child(4).hasText("*");
    assertThat(r).child(4).hasNoChildren();
  }

  @Test
  public void fieldNameAndValueWithColonFollowedByAnotherField() throws Exception {
    Tree r = parse("project:foo:bar file:baz");
    assertThat(r).hasType(AND);
    assertThat(r).hasChildCount(2);

    assertThat(r).child(0).hasType(FIELD_NAME);
    assertThat(r).child(0).hasText("project");
    assertThat(r).child(0).hasChildCount(3);
    assertThat(r).child(0).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).child(0).hasText("foo");
    assertThat(r).child(0).child(0).hasNoChildren();
    assertThat(r).child(0).child(1).hasType(COLON);
    assertThat(r).child(0).child(1).hasText(":");
    assertThat(r).child(0).child(1).hasNoChildren();
    assertThat(r).child(0).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(0).child(2).hasText("bar");
    assertThat(r).child(0).child(2).hasNoChildren();

    assertThat(r).child(1).hasType(FIELD_NAME);
    assertThat(r).child(1).hasText("file");
    assertThat(r).child(1).hasChildCount(1);
    assertThat(r).child(1).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(1).child(0).hasText("baz");
    assertThat(r).child(1).child(0).hasNoChildren();
  }

  @Test
  public void fieldNameAndValueWithColonFollowedByOpenParen() throws Exception {
    Tree r = parse("project:foo:bar (file:baz)");
    assertThat(r).hasType(AND);
    assertThat(r).hasChildCount(2);

    assertThat(r).child(0).hasType(FIELD_NAME);
    assertThat(r).child(0).hasText("project");
    assertThat(r).child(0).hasChildCount(3);
    assertThat(r).child(0).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).child(0).hasText("foo");
    assertThat(r).child(0).child(0).hasNoChildren();
    assertThat(r).child(0).child(1).hasType(COLON);
    assertThat(r).child(0).child(1).hasText(":");
    assertThat(r).child(0).child(1).hasNoChildren();
    assertThat(r).child(0).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(0).child(2).hasText("bar");
    assertThat(r).child(0).child(2).hasNoChildren();

    assertThat(r).child(1).hasType(FIELD_NAME);
    assertThat(r).child(1).hasText("file");
    assertThat(r).child(1).hasChildCount(1);
    assertThat(r).child(1).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(1).child(0).hasText("baz");
    assertThat(r).child(1).child(0).hasNoChildren();
  }

  @Test
  public void fieldNameAndValueWithColonFollowedByCloseParen() throws Exception {
    Tree r = parse("(project:foo:bar) file:baz");
    assertThat(r).hasType(AND);
    assertThat(r).hasChildCount(2);

    assertThat(r).child(0).hasType(FIELD_NAME);
    assertThat(r).child(0).hasText("project");
    assertThat(r).child(0).hasChildCount(3);
    assertThat(r).child(0).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).child(0).hasText("foo");
    assertThat(r).child(0).child(0).hasNoChildren();
    assertThat(r).child(0).child(1).hasType(COLON);
    assertThat(r).child(0).child(1).hasText(":");
    assertThat(r).child(0).child(1).hasNoChildren();
    assertThat(r).child(0).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(0).child(2).hasText("bar");
    assertThat(r).child(0).child(2).hasNoChildren();

    assertThat(r).child(1).hasType(FIELD_NAME);
    assertThat(r).child(1).hasText("file");
    assertThat(r).child(1).hasChildCount(1);
    assertThat(r).child(1).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(1).child(0).hasText("baz");
    assertThat(r).child(1).child(0).hasNoChildren();
  }

  @Test
  public void defaultFieldWithColon() throws Exception {
    Tree r = parse("CodeReview:+2");
    assertThat(r).hasType(DEFAULT_FIELD);
    assertThat(r).hasChildCount(3);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("CodeReview");
    assertThat(r).child(0).hasNoChildren();
    assertThat(r).child(1).hasType(COLON);
    assertThat(r).child(1).hasText(":");
    assertThat(r).child(1).hasNoChildren();
    assertThat(r).child(2).hasType(SINGLE_WORD);
    assertThat(r).child(2).hasText("+2");
    assertThat(r).child(2).hasNoChildren();
  }

  @Test
  public void fieldNameWithEscapedDoubleQuotesInValue() throws Exception {
    // Actual String: A \"special\" word
    String search = "message:\"A \\\"special\\\" word\"";
    Tree r = parse(search);
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasChildCount(1);
    assertThat(r).hasText("message");
    assertThat(r).child(0).hasType(EXACT_PHRASE);
    // Antlr escaped the double quotes in the phrase.
    assertThat(r).child(0).hasText("A \"special\" word");
  }

  @Test
  public void fieldNameWithEscapedTabCharacterIsPreserved() throws Exception {
    String[] searches = {"message:\"A \\t word\"", "message:{A \\t word}"};
    for (String search : searches) {
      Tree r = parse(search);
      assertThat(r).hasType(FIELD_NAME);
      assertThat(r).hasChildCount(1);
      assertThat(r).hasText("message");
      assertThat(r).child(0).hasType(EXACT_PHRASE);
      assertThat(r).child(0).hasText("A \t word");
    }
  }

  @Test
  public void fieldNameWithEscapedBackslashIsIncludedInOutput() throws Exception {
    String search = "message:\"A backslash \\\\ in phrase\"";
    Tree r = parse(search);
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasChildCount(1);
    assertThat(r).hasText("message");
    assertThat(r).child(0).hasType(EXACT_PHRASE);
    assertThat(r).child(0).hasText("A backslash \\ in phrase");
  }

  @Test
  public void fieldNameWithUnderscore() throws Exception {
    Tree r = parse("foo_bar:baz");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("foo_bar");
    assertThat(r).hasChildCount(1);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("baz");
    assertThat(r).child(0).hasNoChildren();
  }

  @Test
  public void fieldNameWithHyphen() throws Exception {
    Tree r = parse("foo-bar:baz");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("foo-bar");
    assertThat(r).hasChildCount(1);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("baz");
    assertThat(r).child(0).hasNoChildren();
  }

  @Test
  public void fieldNameWithUnderscoreAndHyphen() throws Exception {
    Tree r = parse("foo-bar_baz:qux");
    assertThat(r).hasType(FIELD_NAME);
    assertThat(r).hasText("foo-bar_baz");
    assertThat(r).hasChildCount(1);
    assertThat(r).child(0).hasType(SINGLE_WORD);
    assertThat(r).child(0).hasText("qux");
    assertThat(r).child(0).hasNoChildren();
  }

  private static void assertParseFails(String query) {
    assertThrows(QueryParseException.class, () -> parse(query));
  }
}
