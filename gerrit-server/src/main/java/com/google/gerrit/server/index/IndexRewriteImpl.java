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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.QueryRewriter;
import com.google.gerrit.server.query.change.AndSource;
import com.google.gerrit.server.query.change.BasicChangeRewrites;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.OrSource;
import com.google.gerrit.server.query.change.SqlRewriterImpl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Rewriter that pushes boolean logic into the secondary index. */
public class IndexRewriteImpl implements ChangeQueryRewriter {
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
   * @return the maximal set of statuses that any changes matching the input
   *     predicates may have, based on examining boolean and
   *     {@link ChangeStatusPredicate}s.
   */
  public static EnumSet<Change.Status> getPossibleStatus(Predicate<ChangeData> in) {
    EnumSet<Change.Status> s = extractStatus(in);
    return !s.isEmpty() ? s : EnumSet.allOf(Change.Status.class);
  }

  private static EnumSet<Change.Status> extractStatus(Predicate<ChangeData> in) {
    if (in instanceof ChangeStatusPredicate) {
      return EnumSet.of(((ChangeStatusPredicate) in).getStatus());
    } else if (in instanceof NotPredicate) {
      EnumSet<Status> s = extractStatus(in.getChild(0));
      return !s.isEmpty()
          ? EnumSet.complementOf(s)
          : EnumSet.noneOf(Change.Status.class);
    } else if (in instanceof OrPredicate) {
      EnumSet<Change.Status> s = EnumSet.noneOf(Change.Status.class);
      for (int i = 0; i < in.getChildCount(); i++) {
        s.addAll(extractStatus(in.getChild(i)));
      }
      return s;
    } else if (in instanceof AndPredicate) {
      EnumSet<Change.Status> s = null;
      for (int i = 0; i < in.getChildCount(); i++) {
        EnumSet<Change.Status> c = extractStatus(in.getChild(i));
        if (!c.isEmpty()) {
          if (s == null) {
            s = c;
          } else {
            s.retainAll(c);
          }
        }
      }
      return s != null ? s : EnumSet.noneOf(Change.Status.class);
    } else {
      return EnumSet.noneOf(Change.Status.class);
    }
  }

  private final ChangeIndex index;
  private final Provider<ReviewDb> db;
  private final BasicRewritesImpl basicRewrites;

  @Inject
  IndexRewriteImpl(ChangeIndex index,
      Provider<ReviewDb> db,
      BasicRewritesImpl basicRewrites) {
    this.index = index;
    this.db = db;
    this.basicRewrites = basicRewrites;
  }

  @Override
  public Predicate<ChangeData> rewrite(Predicate<ChangeData> in) {
    in = basicRewrites.rewrite(in);

    Predicate<ChangeData> out = rewriteImpl(in);
    if (out instanceof IndexPredicate) {
      return query(out);
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
   * @return {@code null} if no part of this subtree can be queried in the
   *     index directly. {@code in} if this subtree and all its children can be
   *     queried directly in the index. Otherwise, a predicate that is
   *     semantically equivalent, with some of its subtrees wrapped to query the
   *     index directly.
   */
  private Predicate<ChangeData> rewriteImpl(Predicate<ChangeData> in) {
    if (in instanceof IndexPredicate) {
      return in;
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
      Predicate<ChangeData> nc = rewriteImpl(c);
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
    return partitionChildren(in, newChildren, isIndexed);
  }

  private Predicate<ChangeData> partitionChildren(
      Predicate<ChangeData> in,
      List<Predicate<ChangeData>> newChildren,
      BitSet isIndexed) {
    if (isIndexed.cardinality() == 1) {
      int i = isIndexed.nextSetBit(0);
      newChildren.add(0, query(newChildren.remove(i)));
      return copy(in, newChildren);
    }

    // Group all indexed predicates into a wrapped subtree.
    List<Predicate<ChangeData>> indexed =
        Lists.newArrayListWithCapacity(isIndexed.cardinality());

    List<Predicate<ChangeData>> all =
        Lists.newArrayListWithCapacity(
            newChildren.size() - isIndexed.cardinality() + 1);

    for (int i = 0; i < newChildren.size(); i++) {
      Predicate<ChangeData> c = newChildren.get(i);
      if (isIndexed.get(i)) {
        indexed.add(c);
      } else {
        all.add(c);
      }
    }
    all.add(0, query(in.copy(indexed)));
    return copy(in, all);
  }

  private Predicate<ChangeData> copy(
      Predicate<ChangeData> in,
      List<Predicate<ChangeData>> all) {
    if (in instanceof AndPredicate) {
      return new AndSource(db, all);
    } else if (in instanceof OrPredicate) {
      return new OrSource(all);
    }
    return in.copy(all);
  }

  private IndexedChangeQuery query(Predicate<ChangeData> p) {
    try {
      return new IndexedChangeQuery(index, p);
    } catch (QueryParseException e) {
      throw new IllegalStateException(
          "Failed to convert " + p + " to index predicate", e);
    }
  }

  private static boolean isRewritePossible(Predicate<ChangeData> p) {
    return p.getChildCount() > 0 && (
           p instanceof AndPredicate
        || p instanceof OrPredicate
        || p instanceof NotPredicate);
  }

  static class BasicRewritesImpl extends BasicChangeRewrites {
    private static final QueryRewriter.Definition<ChangeData, BasicRewritesImpl> mydef =
        new QueryRewriter.Definition<ChangeData, BasicRewritesImpl>(
            BasicRewritesImpl.class, SqlRewriterImpl.BUILDER);
    @Inject
    BasicRewritesImpl(Provider<ReviewDb> db) {
      super(mydef, db);
    }
  }
}
