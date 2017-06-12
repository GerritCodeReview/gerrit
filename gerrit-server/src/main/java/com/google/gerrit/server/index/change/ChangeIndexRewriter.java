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

import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.LimitPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.AndChangeSource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.OrSource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.util.MutableInteger;

/** Rewriter that pushes boolean logic into the secondary index. */
@Singleton
public class ChangeIndexRewriter implements IndexRewriter<ChangeData> {
  /** Set of all open change statuses. */
  public static final Set<Change.Status> OPEN_STATUSES;

  /** Set of all closed change statuses. */
  public static final Set<Change.Status> CLOSED_STATUSES;

  static {
    EnumSet<Change.Status> open = EnumSet.noneOf(Change.Status.class);
    EnumSet<Change.Status> closed = EnumSet.noneOf(Change.Status.class);
    for (Change.Status s : Change.Status.values()) {
      if (s.isOpen()) {
        open.add(s);
      } else {
        closed.add(s);
      }
    }
    OPEN_STATUSES = Sets.immutableEnumSet(open);
    CLOSED_STATUSES = Sets.immutableEnumSet(closed);
  }

  /**
   * Get the set of statuses that changes matching the given predicate may have.
   *
   * @param in predicate
   * @return the maximal set of statuses that any changes matching the input predicates may have,
   *     based on examining boolean and {@link ChangeStatusPredicate}s.
   */
  public static EnumSet<Change.Status> getPossibleStatus(Predicate<ChangeData> in) {
    EnumSet<Change.Status> s = extractStatus(in);
    return s != null ? s : EnumSet.allOf(Change.Status.class);
  }

  private static EnumSet<Change.Status> extractStatus(Predicate<ChangeData> in) {
    if (in instanceof ChangeStatusPredicate) {
      return EnumSet.of(((ChangeStatusPredicate) in).getStatus());
    } else if (in instanceof NotPredicate) {
      EnumSet<Status> s = extractStatus(in.getChild(0));
      return s != null ? EnumSet.complementOf(s) : null;
    } else if (in instanceof OrPredicate) {
      EnumSet<Change.Status> r = null;
      int childrenWithStatus = 0;
      for (int i = 0; i < in.getChildCount(); i++) {
        EnumSet<Status> c = extractStatus(in.getChild(i));
        if (c != null) {
          if (r == null) {
            r = EnumSet.noneOf(Change.Status.class);
          }
          r.addAll(c);
          childrenWithStatus++;
        }
      }
      if (r != null && childrenWithStatus < in.getChildCount()) {
        // At least one child supplied a status but another did not.
        // Assume all statuses for the children that did not feed a
        // status at this part of the tree. This matches behavior if
        // the child was used at the root of a query.
        return EnumSet.allOf(Change.Status.class);
      }
      return r;
    } else if (in instanceof AndPredicate) {
      EnumSet<Change.Status> r = null;
      for (int i = 0; i < in.getChildCount(); i++) {
        EnumSet<Change.Status> c = extractStatus(in.getChild(i));
        if (c != null) {
          if (r == null) {
            r = EnumSet.allOf(Change.Status.class);
          }
          r.retainAll(c);
        }
      }
      return r;
    }
    return null;
  }

  private final ChangeIndexCollection indexes;
  private final IndexConfig config;

  @Inject
  ChangeIndexRewriter(ChangeIndexCollection indexes, IndexConfig config) {
    this.indexes = indexes;
    this.config = config;
  }

  @Override
  public Predicate<ChangeData> rewrite(Predicate<ChangeData> in, QueryOptions opts)
      throws QueryParseException {
    Predicate<ChangeData> s = rewriteImpl(in, opts);
    if (!(s instanceof ChangeDataSource)) {
      in = Predicate.and(open(), in);
      s = rewriteImpl(in, opts);
    }
    if (!(s instanceof ChangeDataSource)) {
      throw new QueryParseException("invalid query: " + s);
    }
    return s;
  }

  private Predicate<ChangeData> rewriteImpl(Predicate<ChangeData> in, QueryOptions opts)
      throws QueryParseException {
    ChangeIndex index = indexes.getSearchIndex();

    MutableInteger leafTerms = new MutableInteger();
    Predicate<ChangeData> out = rewriteImpl(in, index, opts, leafTerms);
    if (in == out || out instanceof IndexPredicate) {
      return new IndexedChangeQuery(index, out, opts);
    } else if (out == null /* cannot rewrite */) {
      return in;
    } else {
      return out;
    }
  }

  /**
   * Rewrite a single predicate subtree.
   *
   * @param in predicate to rewrite.
   * @param index index whose schema determines which fields are indexed.
   * @param opts other query options.
   * @param leafTerms number of leaf index query terms encountered so far.
   * @return {@code null} if no part of this subtree can be queried in the index directly. {@code
   *     in} if this subtree and all its children can be queried directly in the index. Otherwise, a
   *     predicate that is semantically equivalent, with some of its subtrees wrapped to query the
   *     index directly.
   * @throws QueryParseException if the underlying index implementation does not support this
   *     predicate.
   */
  private Predicate<ChangeData> rewriteImpl(
      Predicate<ChangeData> in, ChangeIndex index, QueryOptions opts, MutableInteger leafTerms)
      throws QueryParseException {
    if (isIndexPredicate(in, index)) {
      if (++leafTerms.value > config.maxTerms()) {
        throw new QueryParseException("too many terms in query");
      }
      return in;
    } else if (in instanceof LimitPredicate) {
      // Replace any limits with the limit provided by the caller. The caller
      // should have already searched the predicate tree for limit predicates
      // and included that in their limit computation.
      return new LimitPredicate<>(ChangeQueryBuilder.FIELD_LIMIT, opts.limit());
    } else if (!isRewritePossible(in)) {
      return null; // magic to indicate "in" cannot be rewritten
    }

    int n = in.getChildCount();
    BitSet isIndexed = new BitSet(n);
    BitSet notIndexed = new BitSet(n);
    BitSet rewritten = new BitSet(n);
    List<Predicate<ChangeData>> newChildren = Lists.newArrayListWithCapacity(n);
    for (int i = 0; i < n; i++) {
      Predicate<ChangeData> c = in.getChild(i);
      Predicate<ChangeData> nc = rewriteImpl(c, index, opts, leafTerms);
      if (nc == c) {
        isIndexed.set(i);
        newChildren.add(c);
      } else if (nc == null /* cannot rewrite c */) {
        notIndexed.set(i);
        newChildren.add(c);
      } else {
        rewritten.set(i);
        newChildren.add(nc);
      }
    }

    if (isIndexed.cardinality() == n) {
      return in; // All children are indexed, leave as-is for parent.
    } else if (notIndexed.cardinality() == n) {
      return null; // Can't rewrite any children, so cannot rewrite in.
    } else if (rewritten.cardinality() == n) {
      return in.copy(newChildren); // All children were rewritten.
    }
    return partitionChildren(in, newChildren, isIndexed, index, opts);
  }

  private boolean isIndexPredicate(Predicate<ChangeData> in, ChangeIndex index) {
    if (!(in instanceof IndexPredicate)) {
      return false;
    }
    IndexPredicate<ChangeData> p = (IndexPredicate<ChangeData>) in;
    return index.getSchema().hasField(p.getField());
  }

  private Predicate<ChangeData> partitionChildren(
      Predicate<ChangeData> in,
      List<Predicate<ChangeData>> newChildren,
      BitSet isIndexed,
      ChangeIndex index,
      QueryOptions opts)
      throws QueryParseException {
    if (isIndexed.cardinality() == 1) {
      int i = isIndexed.nextSetBit(0);
      newChildren.add(0, new IndexedChangeQuery(index, newChildren.remove(i), opts));
      return copy(in, newChildren);
    }

    // Group all indexed predicates into a wrapped subtree.
    List<Predicate<ChangeData>> indexed = Lists.newArrayListWithCapacity(isIndexed.cardinality());

    List<Predicate<ChangeData>> all =
        Lists.newArrayListWithCapacity(newChildren.size() - isIndexed.cardinality() + 1);

    for (int i = 0; i < newChildren.size(); i++) {
      Predicate<ChangeData> c = newChildren.get(i);
      if (isIndexed.get(i)) {
        indexed.add(c);
      } else {
        all.add(c);
      }
    }
    all.add(0, new IndexedChangeQuery(index, in.copy(indexed), opts));
    return copy(in, all);
  }

  private Predicate<ChangeData> copy(Predicate<ChangeData> in, List<Predicate<ChangeData>> all) {
    if (in instanceof AndPredicate) {
      return new AndChangeSource(all);
    } else if (in instanceof OrPredicate) {
      return new OrSource(all);
    }
    return in.copy(all);
  }

  private static boolean isRewritePossible(Predicate<ChangeData> p) {
    return p.getChildCount() > 0
        && (p instanceof AndPredicate || p instanceof OrPredicate || p instanceof NotPredicate);
  }
}
