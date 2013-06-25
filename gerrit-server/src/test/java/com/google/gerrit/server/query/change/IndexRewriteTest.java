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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.query.change;

import static com.google.gerrit.reviewdb.client.Change.Status.ABANDONED;
import static com.google.gerrit.reviewdb.client.Change.Status.DRAFT;
import static com.google.gerrit.reviewdb.client.Change.Status.MERGED;
import static com.google.gerrit.reviewdb.client.Change.Status.NEW;
import static com.google.gerrit.reviewdb.client.Change.Status.SUBMITTED;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.PredicateWrapper;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

@SuppressWarnings("unchecked")
public class IndexRewriteTest extends TestCase {
  private static class DummyIndex implements ChangeIndex {
    @Override
    public void insert(ChangeData cd) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void replace(ChangeData cd) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(ChangeData cd) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeDataSource getSource(Predicate<ChangeData> p)
        throws QueryParseException {
      return new Source();
    }
  }

  private static class Source implements ChangeDataSource {
    @Override
    public int getCardinality() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasChange() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      throw new UnsupportedOperationException();
    }
  }

  private DummyIndex index;
  private ChangeQueryBuilder queryBuilder;
  private IndexRewrite rewrite;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    index = new DummyIndex();
    queryBuilder = new ChangeQueryBuilder(
        new ChangeQueryBuilder.Arguments(null, null, null, null, null, null,
            null, null, null, null, null, null, null),
        null);
    rewrite = new IndexRewriteImpl(index);
  }

  public void testIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("file:a");
    assertEquals(wrap(in), rewrite(in));
  }

  public void testNonIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("branch:a");
    assertSame(in, rewrite(in));
  }

  public void testIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("file:a file:b");
    assertEquals(wrap(in), rewrite(in));
  }

  public void testNonIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("branch:a OR branch:b");
    assertSame(in, rewrite(in));
  }

  public void testOneIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("branch:a file:b");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(in.getChild(0), wrap(in.getChild(1))),
        out.getChildren());
  }

  public void testThreeLevelTreeWithAllIndexPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("-status:abandoned (status:open OR status:merged)");
    assertEquals(wrap(in), rewrite.rewrite(in));
  }

  public void testThreeLevelTreeWithSomeIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("-branch:a (file:b OR file:c)");
    Predicate<ChangeData> out = rewrite(in);
    assertEquals(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(in.getChild(0), wrap(in.getChild(1))),
        out.getChildren());
  }

  public void testMultipleIndexPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("file:a OR branch:b OR file:c OR branch:d");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(OrPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(
          in.getChild(1), in.getChild(3),
          wrap(Predicate.or(in.getChild(0), in.getChild(2)))),
        out.getChildren());
  }

  public void testDuplicateSimpleNonIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in = parse("status:new project:p file:a");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(
          in.getChild(0), in.getChild(1),
          wrap(Predicate.and(in.getChild(0), in.getChild(2)))),
        out.getChildren());
  }

  public void testDuplicateCompoundNonIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("(status:new OR status:draft) project:p file:a");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(
          in.getChild(0), in.getChild(1),
          wrap(Predicate.and(in.getChild(0), in.getChild(2)))),
        out.getChildren());
  }

  public void testDuplicateCompoundIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in =
        parse("(status:new OR file:a) project:p file:b");
    Predicate<ChangeData> out = rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(
          in.getChild(1),
          wrap(Predicate.and(in.getChild(0), in.getChild(2)))),
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

  private Predicate<ChangeData> parse(String query) throws QueryParseException {
    return queryBuilder.parse(query);
  }

  private Predicate<ChangeData> rewrite(Predicate<ChangeData> in) {
    return rewrite.rewrite(in);
  }

  private PredicateWrapper wrap(Predicate<ChangeData> p)
      throws QueryParseException {
    return new PredicateWrapper(index, p);
  }

  private Set<Change.Status> status(String query) throws QueryParseException {
    return IndexRewriteImpl.getPossibleStatus(parse(query));
  }
}
