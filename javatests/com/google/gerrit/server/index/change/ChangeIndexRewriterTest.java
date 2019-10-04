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

package com.google.gerrit.server.index.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.common.data.GlobalCapability.DEFAULT_MAX_QUERY_LIMIT;
import static com.google.gerrit.entities.Change.Status.MERGED;
import static com.google.gerrit.entities.Change.Status.NEW;
import static com.google.gerrit.index.query.Predicate.and;
import static com.google.gerrit.index.query.Predicate.or;
import static com.google.gerrit.server.index.change.IndexedChangeQuery.convertOptions;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Change;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.AndChangeSource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.OrSource;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class ChangeIndexRewriterTest {
  private static final IndexConfig CONFIG = IndexConfig.createDefault();

  private FakeChangeIndex index;
  private ChangeIndexCollection indexes;
  private ChangeQueryBuilder queryBuilder;
  private ChangeIndexRewriter rewrite;

  @Before
  public void setUp() throws Exception {
    index = new FakeChangeIndex(FakeChangeIndex.V2);
    indexes = new ChangeIndexCollection();
    indexes.setSearchIndex(index);
    queryBuilder = new FakeQueryBuilder(indexes);
    rewrite = new ChangeIndexRewriter(indexes, IndexConfig.builder().maxTerms(3).build());
  }

  @Test
  public void indexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("file:a");
    assertThat(rewrite(in)).isEqualTo(query(in));
  }

  @Test
  public void nonIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("foo:a");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(AndChangeSource.class).isSameInstanceAs(out.getClass());
    assertThat(out.getChildren())
        .containsExactly(
            query(Predicate.or(ChangeStatusPredicate.open(), ChangeStatusPredicate.closed())), in)
        .inOrder();
  }

  @Test
  public void indexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("file:a file:b");
    assertThat(rewrite(in)).isEqualTo(query(in));
  }

  @Test
  public void nonIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("foo:a OR foo:b");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(AndChangeSource.class).isSameInstanceAs(out.getClass());
    assertThat(out.getChildren())
        .containsExactly(
            query(Predicate.or(ChangeStatusPredicate.open(), ChangeStatusPredicate.closed())), in)
        .inOrder();
  }

  @Test
  public void oneIndexPredicate() throws Exception {
    Predicate<ChangeData> in = parse("foo:a file:b");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(AndChangeSource.class).isSameInstanceAs(out.getClass());
    assertThat(out.getChildren()).containsExactly(query(in.getChild(1)), in.getChild(0)).inOrder();
  }

  @Test
  public void threeLevelTreeWithAllIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("-status:abandoned (file:a OR file:b)");
    assertThat(rewrite.rewrite(in, options(0, DEFAULT_MAX_QUERY_LIMIT))).isEqualTo(query(in));
  }

  @Test
  public void threeLevelTreeWithSomeIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("-foo:a (file:b OR file:c)");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(out.getClass()).isSameInstanceAs(AndChangeSource.class);
    assertThat(out.getChildren()).containsExactly(query(in.getChild(1)), in.getChild(0)).inOrder();
  }

  @Test
  public void multipleIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("file:a OR foo:b OR file:c OR foo:d");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(out.getClass()).isSameInstanceAs(OrSource.class);
    assertThat(out.getChildren())
        .containsExactly(query(or(in.getChild(0), in.getChild(2))), in.getChild(1), in.getChild(3))
        .inOrder();
  }

  @Test
  public void indexAndNonIndexPredicates() throws Exception {
    Predicate<ChangeData> in = parse("status:new bar:p file:a");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(AndChangeSource.class).isSameInstanceAs(out.getClass());
    assertThat(out.getChildren())
        .containsExactly(query(and(in.getChild(0), in.getChild(2))), in.getChild(1))
        .inOrder();
  }

  @Test
  public void duplicateCompoundNonIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in = parse("status:new bar:p file:a");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(out.getClass()).isEqualTo(AndChangeSource.class);
    assertThat(out.getChildren())
        .containsExactly(query(and(in.getChild(0), in.getChild(2))), in.getChild(1))
        .inOrder();
  }

  @Test
  public void duplicateCompoundIndexOnlyPredicates() throws Exception {
    Predicate<ChangeData> in = parse("(status:new OR file:a) bar:p file:b");
    Predicate<ChangeData> out = rewrite(in);
    assertThat(out.getClass()).isEqualTo(AndChangeSource.class);
    assertThat(out.getChildren())
        .containsExactly(query(and(in.getChild(0), in.getChild(2))), in.getChild(1))
        .inOrder();
  }

  @Test
  public void optionsArgumentOverridesAllLimitPredicates() throws Exception {
    Predicate<ChangeData> in = parse("limit:1 file:a limit:3");
    Predicate<ChangeData> out = rewrite(in, options(0, 5));
    assertThat(out.getClass()).isEqualTo(AndChangeSource.class);
    assertThat(out.getChildren())
        .containsExactly(query(in.getChild(1), 5), parse("limit:5"), parse("limit:5"))
        .inOrder();
  }

  @Test
  public void startIncreasesLimitInQueryButNotPredicate() throws Exception {
    int n = 3;
    Predicate<ChangeData> f = parse("file:a");
    Predicate<ChangeData> l = parse("limit:" + n);
    Predicate<ChangeData> in = andSource(f, l);
    assertThat(rewrite.rewrite(in, options(0, n))).isEqualTo(andSource(query(f, 3), l));
    assertThat(rewrite.rewrite(in, options(1, n))).isEqualTo(andSource(query(f, 4), l));
    assertThat(rewrite.rewrite(in, options(2, n))).isEqualTo(andSource(query(f, 5), l));
  }

  @Test
  public void getPossibleStatus() throws Exception {
    Set<Change.Status> all = EnumSet.allOf(Change.Status.class);
    assertThat(status("file:a")).isEqualTo(all);
    assertThat(status("is:new")).containsExactly(NEW);
    assertThat(status("is:new OR is:merged")).containsExactly(NEW, MERGED);
    assertThat(status("is:new OR is:x")).isEqualTo(all);

    assertThat(status("is:new is:merged")).isEmpty();
    assertThat(status("(is:new) (is:merged)")).isEmpty();
    assertThat(status("(is:new) (is:merged)")).isEmpty();
    assertThat(status("is:new is:x")).containsExactly(NEW);
  }

  @Test
  public void unsupportedIndexOperator() throws Exception {
    Predicate<ChangeData> in = parse("status:merged file:a");
    assertThat(rewrite(in)).isEqualTo(query(in));

    indexes.setSearchIndex(new FakeChangeIndex(FakeChangeIndex.V1));

    QueryParseException thrown = assertThrows(QueryParseException.class, () -> rewrite(in));
    assertThat(thrown).hasMessageThat().contains("Unsupported index predicate: file:a");
  }

  @Test
  public void tooManyTerms() throws Exception {
    String q = "file:a OR file:b OR file:c";
    Predicate<ChangeData> in = parse(q);
    assertEquals(query(in), rewrite(in));

    QueryParseException thrown =
        assertThrows(QueryParseException.class, () -> rewrite(parse(q + " OR file:d")));
    assertThat(thrown).hasMessageThat().contains("too many terms in query");
  }

  @Test
  public void testConvertOptions() throws Exception {
    assertEquals(options(0, 3), convertOptions(options(0, 3)));
    assertEquals(options(0, 4), convertOptions(options(1, 3)));
    assertEquals(options(0, 5), convertOptions(options(2, 3)));
  }

  @Test
  public void addingStartToLimitDoesNotExceedBackendLimit() throws Exception {
    int max = CONFIG.maxLimit();
    assertEquals(options(0, max), convertOptions(options(0, max)));
    assertEquals(options(0, max), convertOptions(options(1, max)));
    assertEquals(options(0, max), convertOptions(options(1, max - 1)));
    assertEquals(options(0, max), convertOptions(options(2, max - 1)));
  }

  private Predicate<ChangeData> parse(String query) throws QueryParseException {
    return queryBuilder.parse(query);
  }

  @SafeVarargs
  private static AndChangeSource andSource(Predicate<ChangeData>... preds) {
    return new AndChangeSource(Arrays.asList(preds));
  }

  private Predicate<ChangeData> rewrite(Predicate<ChangeData> in) throws QueryParseException {
    return rewrite.rewrite(in, options(0, DEFAULT_MAX_QUERY_LIMIT));
  }

  private Predicate<ChangeData> rewrite(Predicate<ChangeData> in, QueryOptions opts)
      throws QueryParseException {
    return rewrite.rewrite(in, opts);
  }

  private IndexedChangeQuery query(Predicate<ChangeData> p) throws QueryParseException {
    return query(p, DEFAULT_MAX_QUERY_LIMIT);
  }

  private IndexedChangeQuery query(Predicate<ChangeData> p, int limit) throws QueryParseException {
    return new IndexedChangeQuery(index, p, options(0, limit));
  }

  private static QueryOptions options(int start, int limit) {
    return IndexedChangeQuery.createOptions(CONFIG, start, limit, ImmutableSet.of());
  }

  private Set<Change.Status> status(String query) throws QueryParseException {
    return ChangeIndexRewriter.getPossibleStatus(parse(query));
  }
}
