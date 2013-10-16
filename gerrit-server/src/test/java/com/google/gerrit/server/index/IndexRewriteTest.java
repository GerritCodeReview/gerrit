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

import static com.google.gerrit.reviewdb.client.Change.Status.ABANDONED;
import static com.google.gerrit.reviewdb.client.Change.Status.DRAFT;
import static com.google.gerrit.reviewdb.client.Change.Status.MERGED;
import static com.google.gerrit.reviewdb.client.Change.Status.NEW;
import static com.google.gerrit.reviewdb.client.Change.Status.SUBMITTED;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.RewritePredicate;
import com.google.gerrit.server.query.change.AndSource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.OrSource;
import com.google.gerrit.server.query.change.SqlRewriterImpl;

import junit.framework.TestCase;

import java.util.EnumSet;
import java.util.Set;

@SuppressWarnings("unchecked")
public class IndexRewriteTest extends TestCase {
  private FakeIndex index;
  private IndexCollection indexes;
  private ChangeQueryBuilder queryBuilder;
  private IndexRewriteImpl rewrite;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    index = new FakeIndex(FakeIndex.V2);
    indexes = new IndexCollection();
    indexes.setSearchIndex(index);
    queryBuilder = new FakeQueryBuilder(indexes);
    rewrite = new IndexRewriteImpl(
        indexes,
        null,
        new IndexRewriteImpl.BasicRewritesImpl(null, indexes),
        new SqlRewriterImpl(null));
  }

  public void testIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("file:a");
    assertEquals(query(in), rewrite(in));
  }

  public void testNonIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("foo:a");
    assertSame(in, rewrite(in));
  }

  public void testIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("file:a file:b");
    assertEquals(query(in), rewrite(in));
  }

  public void testNonIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("foo:a OR foo:b");
    assertEquals(in, rewrite(in));
  }

  public void testOneIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("foo:a file:b");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndSource.class, out.getClass());
    assertEquals(
        ImmutableList.of(query(in.getChild(1)), in.getChild(0)),
        out.getChildren());
  }

  public void testThreeLevelTreeWithAllIndexPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("-status:abandoned (status:open OR status:merged)");
    assertEquals(
        query(parse("status:new OR status:submitted OR status:draft OR status:merged")),
        rewrite.rewrite(in));
  }

  public void testThreeLevelTreeWithSomeIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("-foo:a (file:b OR file:c)");
    Predicate<ChangeData> out = rewrite(in);
    assertEquals(AndSource.class, out.getClass());
    assertEquals(
        ImmutableList.of(query(in.getChild(1)), in.getChild(0)),
        out.getChildren());
  }

  public void testMultipleIndexPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("file:a OR foo:b OR file:c OR foo:d");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(OrSource.class, out.getClass());
    assertEquals(ImmutableList.of(
          query(Predicate.or(in.getChild(0), in.getChild(2))),
          in.getChild(1), in.getChild(3)),
        out.getChildren());
  }

  public void testIndexAndNonIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("status:new bar:p file:a");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndSource.class, out.getClass());
    assertEquals(ImmutableList.of(
          query(Predicate.and(in.getChild(0), in.getChild(2))),
          in.getChild(1)),
        out.getChildren());
  }

  public void testDuplicateCompoundNonIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("(status:new OR status:draft) bar:p file:a");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndSource.class, out.getClass());
    assertEquals(ImmutableList.of(
          query(Predicate.and(in.getChild(0), in.getChild(2))),
          in.getChild(1)),
        out.getChildren());
  }

  public void testDuplicateCompoundIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("(status:new OR file:a) bar:p file:b");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndSource.class, out.getClass());
    assertEquals(ImmutableList.of(
          query(Predicate.and(in.getChild(0), in.getChild(2))),
          in.getChild(1)),
        out.getChildren());
  }

  public void testLimit() throws Exception {
    Predicate<ChangeData> in = parse("file:a limit:3");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndSource.class, out.getClass());
    assertEquals(ImmutableList.of(
          query(in.getChild(0), 3),
          in.getChild(1)),
        out.getChildren());
  }

  public void testGetPossibleStatus() throws Exception {
    assertEquals(EnumSet.allOf(Change.Status.class), status("file:a"));
    assertEquals(EnumSet.of(NEW), status("is:new"));
    assertEquals(EnumSet.of(SUBMITTED, DRAFT, MERGED, ABANDONED),
        status("-is:new"));
    assertEquals(EnumSet.of(NEW, MERGED), status("is:new OR is:merged"));

    EnumSet<Change.Status> none = EnumSet.noneOf(Change.Status.class);
    assertEquals(none, status("is:new is:merged"));
    assertEquals(none, status("(is:new is:draft) (is:merged is:submitted)"));
    assertEquals(none, status("(is:new is:draft) (is:merged is:submitted)"));

    assertEquals(EnumSet.of(MERGED, SUBMITTED),
        status("(is:new is:draft) OR (is:merged OR is:submitted)"));
  }

  public void testUnsupportedIndexOperator() throws Exception {
    Predicate<ChangeData> in = parse("status:merged file:a");
    assertEquals(query(in), rewrite(in));

    indexes.setSearchIndex(new FakeIndex(FakeIndex.V1));
    Predicate<ChangeData> out = rewrite(in);
    assertTrue(out instanceof AndPredicate);
    assertEquals(ImmutableList.of(
          query(in.getChild(0)),
          in.getChild(1)),
        out.getChildren());
  }

  public void testNoChangeIndexUsesSqlRewrites() throws Exception {
    Predicate<ChangeData> in = parse("status:open project:p ref:b");
    Predicate<ChangeData> out;

    out = rewrite(in);
    assertTrue(out instanceof AndPredicate || out instanceof IndexedChangeQuery);

    indexes.setSearchIndex(null);
    out = rewrite(in);
    assertTrue(out instanceof RewritePredicate);
  }

  private Predicate<ChangeData> parse(String query) throws QueryParseException {
    return queryBuilder.parse(query);
  }

  private Predicate<ChangeData> rewrite(Predicate<ChangeData> in)
      throws QueryParseException {
    return rewrite.rewrite(in);
  }

  private IndexedChangeQuery query(Predicate<ChangeData> p)
      throws QueryParseException {
    return query(p, IndexRewriteImpl.MAX_LIMIT);
  }

  private IndexedChangeQuery query(Predicate<ChangeData> p, int limit)
      throws QueryParseException {
    return new IndexedChangeQuery(null, index, p, limit);
  }

  private Set<Change.Status> status(String query) throws QueryParseException {
    return IndexRewriteImpl.getPossibleStatus(parse(query));
  }
}
