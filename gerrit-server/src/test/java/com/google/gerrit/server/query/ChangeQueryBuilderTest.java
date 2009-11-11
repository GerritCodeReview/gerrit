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

import static com.google.gerrit.server.query.ChangeQueryBuilder.FIELD_CHANGE;
import static com.google.gerrit.server.query.ChangeQueryBuilder.FIELD_COMMIT;
import static com.google.gerrit.server.query.ChangeQueryBuilder.FIELD_OWNER;
import static com.google.gerrit.server.query.ChangeQueryBuilder.FIELD_REVIEWER;
import static com.google.gerrit.server.query.Predicate.and;
import static com.google.gerrit.server.query.Predicate.not;
import static com.google.gerrit.server.query.Predicate.or;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

public class ChangeQueryBuilderTest extends TestCase {
  private static OperatorPredicate f(final String name, final String value) {
    return new OperatorPredicate(name, value);
  }

  private static Predicate owner(final String who) {
    return f(FIELD_OWNER, who);
  }

  private static Predicate reviewer(final String who) {
    return f(FIELD_REVIEWER, who);
  }

  private static Predicate commit(final String idstr) {
    final AbbreviatedObjectId id = AbbreviatedObjectId.fromString(idstr);
    return new ObjectIdPredicate(FIELD_COMMIT, id);
  }

  private static Predicate p(final String str) throws QueryParseException {
    return new ChangeQueryBuilder().parse(str);
  }

  public void testEmptyQuery() {
    try {
      p("");
      fail("expected exception");
    } catch (QueryParseException e) {
      assertEquals("line 0:-1 no viable alternative at input '<EOF>'", e
          .getMessage());
    }
  }

  public void testFailInvalidOperator() {
    final String op = "thiswillneverbeaqueryoperatoritistoolongtotype";
    final String val = "true";
    try {
      p(op + ":" + val);
      fail("expected exception");
    } catch (QueryParseException e) {
      assertEquals("Unsupported operator " + op + ":" + val, e.getMessage());
    }
  }

  public void testFailNestedOperator() {
    try {
      p("commit:(foo:bar whiz:bang)");
      fail("expected exception");
    } catch (QueryParseException e) {
      assertEquals("Nested operator not expected: foo", e.getMessage());
    }
  }

  // commit:

  public void testDefaultSHA1() throws QueryParseException {
    assertEquals(commit("6ea15"), p("6ea15"));
    assertEquals(commit("6ea15"), p("6EA15"));
    assertEquals(commit("6ea15b73668073fd9f70b2635efcb8cf8aabda22"),
        p("6ea15b73668073fd9f70b2635efcb8cf8aabda22"));
  }

  public void testCommitSHA1() throws QueryParseException {
    assertEquals(commit("6ea15"), p("commit:6ea15"));
    assertEquals(commit("6ea15"), p("commit:6EA15")); // note: forces lowercase
    assertEquals(commit("6ea15b73668073fd9f70b2635efcb8cf8aabda22"),
        p("commit:6ea15b73668073fd9f70b2635efcb8cf8aabda22"));

    try {
      p("commit:yonothash");
    } catch (QueryParseException e) {
      assertEquals("Error in operator commit:yonothash", e.getMessage());
    }
  }

  // change:

  public void testDefaultChangeID() throws QueryParseException {
    assertEquals(f(FIELD_CHANGE, "1234"), p("1234"));
  }

  public void testChangeID() throws QueryParseException {
    assertEquals(f(FIELD_CHANGE, "1234"), p("change:1234"));
  }

  // owner:

  public void testOwnerBare() throws QueryParseException {
    assertEquals(owner("bob"), p("owner:bob"));
    assertEquals(owner("Bob"), p("owner:Bob"));
    assertEquals(owner("bob@example.com"), p("owner:bob@example.com"));

    assertEquals(owner("bob"), p("owner: bob"));
    assertEquals(owner("Bob"), p("owner: Bob"));
    assertEquals(owner("bob@example.com"), p("owner: bob@example.com"));

    assertEquals(owner("bob"), p("owner:\tbob"));
    assertEquals(owner("Bob"), p("owner:\tBob"));
    assertEquals(owner("bob@example.com"), p("owner:\tbob@example.com"));
  }

  public void testOwnerQuoted() throws QueryParseException {
    assertEquals(owner("bob"), p("owner:\"bob\""));
    assertEquals(owner("bob@example.com"), p("owner:\"bob@example.com\""));
    assertEquals(owner("<bob@example.com>"), p("owner:\"<bob@example.com>\""));
    assertEquals(owner("A U Thor"), p("owner:\"A U Thor\""));

    assertEquals(owner("bob"), p("owner: \"bob\""));
    assertEquals(owner("bob@example.com"), p("owner: \"bob@example.com\""));
    assertEquals(owner("<bob@example.com>"), p("owner: \"<bob@example.com>\""));
    assertEquals(owner("A U Thor"), p("owner: \"A U Thor\""));

    assertEquals(owner("bob"), p("owner:\t\"bob\""));
    assertEquals(owner("bob@example.com"), p("owner:\t\"bob@example.com\""));
    assertEquals(owner("<bob@example.com>"), p("owner:\t\"<bob@example.com>\""));
    assertEquals(owner("A U Thor"), p("owner:\t\"A U Thor\""));
  }

  public void testOwner_NOT() throws QueryParseException {
    assertEquals(not(owner("bob")), p("-owner:bob"));
    assertEquals(not(owner("Bob")), p("-owner:Bob"));
    assertEquals(not(owner("bob@example.com")), p("-owner:bob@example.com"));

    assertEquals(not(owner("bob")), p("NOT owner:bob"));
    assertEquals(not(owner("Bob")), p("NOT owner:Bob"));
    assertEquals(not(owner("bob@example.com")), p("NOT owner:bob@example.com"));
  }

  // AND

  public void testAND_Styles2() throws QueryParseException {
    final Predicate exp = and(commit("6ea15"), owner("bob"));
    assertEquals(exp, p("6ea15 owner:bob"));
    assertEquals(exp, p("6ea15 AND owner:bob"));
  }

  public void testAND_Styles3() throws QueryParseException {
    final Predicate exp = and(commit("6ea15"), owner("bob"), reviewer("alice"));
    assertEquals(exp, p("6ea15 owner:bob reviewer:alice"));
    assertEquals(exp, p("6ea15 AND owner:bob reviewer:alice"));
    assertEquals(exp, p("6ea15 owner:bob AND reviewer:alice"));
    assertEquals(exp, p("6ea15 AND owner:bob AND reviewer:alice"));
  }

  public void testAND_ManyValuesOneOperator() throws QueryParseException {
    final Predicate exp =
        and(reviewer("alice"), reviewer("bob"), reviewer("charlie"));
    assertEquals(exp, p("reviewer:(alice bob charlie)"));
    assertEquals(exp, p("reviewer:(alice AND bob charlie)"));
    assertEquals(exp, p("reviewer:(alice bob AND charlie)"));
    assertEquals(exp, p("reviewer:(alice AND bob AND charlie)"));
  }

  public void testAND_FlattensOperators() throws QueryParseException {
    final Predicate exp =
        and(reviewer("alice"), reviewer("bob"), reviewer("charlie"));
    assertEquals(exp, p("reviewer:alice reviewer:(bob charlie)"));
  }

  // OR

  public void testOR_2() throws QueryParseException {
    final Predicate exp = or(commit("6ea15"), owner("bob"));
    assertEquals(exp, p("6ea15 OR owner:bob"));
  }

  public void testOR_3() throws QueryParseException {
    final Predicate exp = or(commit("6ea15"), owner("bob"), reviewer("alice"));
    assertEquals(exp, p("6ea15 OR owner:bob OR reviewer:alice"));
  }

  public void testOR_ManyValuesOneOperator() throws QueryParseException {
    final Predicate exp =
        or(reviewer("alice"), reviewer("bob"), reviewer("charlie"));
    assertEquals(exp, p("reviewer:(alice OR bob OR charlie)"));
  }

  public void testOR_FlattensOperators() throws QueryParseException {
    final Predicate exp =
        or(reviewer("alice"), reviewer("bob"), reviewer("charlie"));
    assertEquals(exp, p("reviewer:alice OR reviewer:(bob OR charlie)"));
  }
}
