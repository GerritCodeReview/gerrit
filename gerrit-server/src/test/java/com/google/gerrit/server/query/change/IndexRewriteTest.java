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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.PredicateWrapper;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import junit.framework.TestCase;

import java.io.IOException;

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

  private static class FieldPredicate extends IndexPredicate<ChangeData> {
    private FieldPredicate(String value) {
      super(ChangeField.STATUS, value);
    }

    @Override
    public boolean match(ChangeData cd) throws OrmException {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCost() {
      return 1;
    }
  }

  private static class OpPredicate extends OperatorPredicate<ChangeData> {
    public OpPredicate(String name, String value) {
      super(name, value);
    }

    @Override
    public boolean match(ChangeData object) throws OrmException {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getCost() {
      return 1;
    }
  }

  private DummyIndex index;
  private IndexRewrite rewrite;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    index = new DummyIndex();
    rewrite = new IndexRewrite(index);
  }

  public void testIndexPredicate() throws Exception {
    Predicate<ChangeData> in = new FieldPredicate("in");
    assertEquals(wrap(in), rewrite.rewrite(in));
  }

  public void testIndexPredicates() throws Exception {
    Predicate<ChangeData> f1 = new FieldPredicate("f1");
    Predicate<ChangeData> f2 = new FieldPredicate("f2");
    Predicate<ChangeData> in = Predicate.and(f1, f2);

    assertEquals(wrap(in), rewrite.rewrite(in));
  }

  public void testNonIndexPredicates() throws Exception {
    Predicate<ChangeData> o1 = new OpPredicate("o1", "o1");
    Predicate<ChangeData> o2 = new OpPredicate("o2", "o2");
    Predicate<ChangeData> in = Predicate.and(o1, o2);

    Predicate<ChangeData> out = rewrite.rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(o1, o2), out.getChildren());
  }

  public void testOneIndexPredicate() throws Exception {
    Predicate<ChangeData> o1 = new OpPredicate("o1", "o1");
    Predicate<ChangeData> f2 = new FieldPredicate("f2");
    Predicate<ChangeData> in = Predicate.and(o1, f2);

    Predicate<ChangeData> out = rewrite.rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(o1, wrap(f2)), out.getChildren());
  }

  public void testThreeLevelTreeWithAllIndexPredicates() throws Exception {
    Predicate<ChangeData> f1 = new FieldPredicate("f1");
    Predicate<ChangeData> f21 = new FieldPredicate("f21");
    Predicate<ChangeData> f22 = new FieldPredicate("f22");
    Predicate<ChangeData> n1 = Predicate.not(f1);
    Predicate<ChangeData> o2 = Predicate.or(f21, f22);
    Predicate<ChangeData> in = Predicate.and(n1, o2);
    assertEquals(2, in.getChildCount());

    assertEquals(wrap(in), rewrite.rewrite(in));
  }

  public void testThreeLevelTreeWithSomeIndexPredicates() throws Exception {
    Predicate<ChangeData> op1 = new OpPredicate("o1", "o1");
    Predicate<ChangeData> f21 = new FieldPredicate("f21");
    Predicate<ChangeData> f22 = new FieldPredicate("f22");
    Predicate<ChangeData> n1 = Predicate.not(op1);
    Predicate<ChangeData> o2 = Predicate.or(f21, f22);
    Predicate<ChangeData> in = Predicate.and(n1, o2);
    assertEquals(2, in.getChildCount());

    Predicate<ChangeData> out = rewrite.rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(n1, wrap(o2)), out.getChildren());
  }

  public void testMultipleIndexPredicates() throws Exception {
    Predicate<ChangeData> f1 = new FieldPredicate("f1");
    Predicate<ChangeData> o2 = new OpPredicate("o2", "o2");
    Predicate<ChangeData> f3 = new FieldPredicate("f3");
    Predicate<ChangeData> o4 = new OpPredicate("o4", "o4");
    Predicate<ChangeData> in = Predicate.and(f1, o2, f3, o4);
    assertEquals(4, in.getChildCount());

    Predicate<ChangeData> out = rewrite.rewrite(in);
    assertSame(AndPredicate.class, out.getClass());
    assertEquals(ImmutableList.of(o2, o4, wrap(Predicate.and(f1, f3))),
        out.getChildren());
  }

  private PredicateWrapper wrap(Predicate<ChangeData> p)
      throws QueryParseException {
    return new PredicateWrapper(index, p);
  }
}
