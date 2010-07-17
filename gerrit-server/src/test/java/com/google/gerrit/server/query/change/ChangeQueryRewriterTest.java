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

package com.google.gerrit.server.query.change;

import static com.google.gerrit.server.query.Predicate.or;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;

import junit.framework.TestCase;

public class ChangeQueryRewriterTest extends TestCase {
  private static OperatorPredicate<ChangeData> f(final String name,
      final String value) {
    return new OperatorPredicate<ChangeData>(name, value) {
      @Override
      public boolean match(ChangeData object) {
        // TODO Auto-generated method stub
        return false;
      }
    };
  }

  private static Predicate<ChangeData> p(final String str) throws QueryParseException {
    return new ChangeQueryBuilder().parse(str);
  }

  private static Predicate<ChangeData> r(final Predicate<ChangeData> in) {
    return new ChangeQueryRewriter().rewrite(in);
  }

  private static Predicate<ChangeData> status(final Change.Status status) {
    return new ChangeStatusPredicate(status);
  }

  public void testOpenNav() throws QueryParseException {
    Predicate<ChangeData> q;

    q = r(p("status:open beforesortkey:z limit:20"));
    assertTrue(q instanceof ChangeSource);

    q = r(p("limit:5 status:open beforesortkey:0006ed4500002b85 "));
    assertTrue(q instanceof ChangeSource);

    q = r(p("status:open aftersortkey:z limit:20"));
    assertTrue(q instanceof ChangeSource);

    q = r(p("limit:5 status:open aftersortkey:0006ed4500002b85 "));
    assertTrue(q instanceof ChangeSource);
  }

  public void testDummyOwnerOpen() throws QueryParseException {
    assertEquals(f("owneropen", "bob"), r(p("owner:bob status:open")));
    assertEquals(f("owneropen", "bob"), r(p("status:open owner:bob")));

    assertEquals(or(f("owneropen", "bob"), status(Change.Status.MERGED)),
        r(p("owner:bob status:open OR status:merged")));

    assertEquals(or(f("owneropen", "alice"), f("owneropen", "bob")),
        r(p("owner:(alice OR bob) status:open")));
  }
}
