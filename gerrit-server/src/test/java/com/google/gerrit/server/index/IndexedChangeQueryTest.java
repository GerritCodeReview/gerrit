// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index;

import static com.google.gerrit.server.index.IndexedChangeQuery.replaceSortKeyPredicates;

import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;

import junit.framework.TestCase;

public class IndexedChangeQueryTest extends TestCase {
  private FakeIndex index;
  private ChangeQueryBuilder queryBuilder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    index = new FakeIndex(FakeIndex.V2);
    IndexCollection indexes = new IndexCollection();
    indexes.setSearchIndex(index);
    queryBuilder = new FakeQueryBuilder(indexes);
  }

  public void testReplaceSortKeyPredicate_NoSortKey() throws Exception {
    Predicate<ChangeData> p = parse("foo:a bar:b OR (foo:b bar:a)");
    assertSame(p, replaceSortKeyPredicates(p, "1234"));
  }

  public void testReplaceSortKeyPredicate_TopLevelSortKey() throws Exception {
    Predicate<ChangeData> p;
    p = parse("foo:a bar:b sortkey_before:1234 OR (foo:b bar:a)");
    assertEquals(parse("foo:a bar:b sortkey_before:5678 OR (foo:b bar:a)"),
        replaceSortKeyPredicates(p, "5678"));
    p = parse("foo:a bar:b sortkey_after:1234 OR (foo:b bar:a)");
    assertEquals(parse("foo:a bar:b sortkey_after:5678 OR (foo:b bar:a)"),
        replaceSortKeyPredicates(p, "5678"));
  }

  public void testReplaceSortKeyPredicate_NestedSortKey() throws Exception {
    Predicate<ChangeData> p;
    p = parse("foo:a bar:b OR (foo:b bar:a AND sortkey_before:1234)");
    assertEquals(parse("foo:a bar:b OR (foo:b bar:a sortkey_before:5678)"),
        replaceSortKeyPredicates(p, "5678"));
    p = parse("foo:a bar:b OR (foo:b bar:a AND sortkey_after:1234)");
    assertEquals(parse("foo:a bar:b OR (foo:b bar:a sortkey_after:5678)"),
        replaceSortKeyPredicates(p, "5678"));
  }

  private Predicate<ChangeData> parse(String query) throws QueryParseException {
    return queryBuilder.parse(query);
  }
}
