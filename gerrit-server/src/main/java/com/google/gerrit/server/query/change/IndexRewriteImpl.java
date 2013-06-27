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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.PredicateWrapper;
import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.NotPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Rewriter that pushes boolean logic into the secondary index. */
public class IndexRewriteImpl implements IndexRewrite {
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
    if (in instanceof ChangeStatusPredicate) {
      return EnumSet.of(((ChangeStatusPredicate) in).getStatus());
    } else if (in.getClass() == NotPredicate.class) {
      return EnumSet.complementOf(getPossibleStatus(in.getChild(0)));
    } else if (in.getClass() == OrPredicate.class) {
      EnumSet<Change.Status> s = EnumSet.noneOf(Change.Status.class);
      for (int i = 0; i < in.getChildCount(); i++) {
        s.addAll(getPossibleStatus(in.getChild(i)));
      }
      return s;
    } else if (in.getClass() == AndPredicate.class) {
      EnumSet<Change.Status> s = EnumSet.allOf(Change.Status.class);
      for (int i = 0; i < in.getChildCount(); i++) {
        s.retainAll(getPossibleStatus(in.getChild(i)));
      }
      return s;
    } else if (in.getChildCount() == 0) {
      return EnumSet.allOf(Change.Status.class);
    } else {
      throw new IllegalStateException(
          "Invalid predicate type in change index query: " + in.getClass());
    }
  }

  private final IndexCollection indexes;

  @Inject
  IndexRewriteImpl(IndexCollection indexes) {
    this.indexes = indexes;
  }

  @Override
  public Predicate<ChangeData> rewrite(Predicate<ChangeData> in) {
    ChangeIndex index = indexes.getSearchIndex();
    Predicate<ChangeData> out = rewriteImpl(in, index);
    if (out == null) {
      return in;
    } else if (out == in) {
      return wrap(out, index);
    } else {
      return out;
    }
  }

  /**
   * Rewrite a single predicate subtree.
   *
   * @param in predicate to rewrite.
   * @param schema index whose schema determines which fields are indexed.
   * @return {@code null} if no part of this subtree can be queried in the
   *     index directly. {@code in} if this subtree and all its children can be
   *     queried directly in the index. Otherwise, a predicate that is
   *     semantically equivalent, with some of its subtrees wrapped to query the
   *     index directly.
   */
  private Predicate<ChangeData> rewriteImpl(Predicate<ChangeData> in,
      ChangeIndex index) {
    if (isIndexPredicate(in, index)) {
      return in;
    }
    if (!isRewritePossible(in)) {
      return null;
    }
    int n = in.getChildCount();
    BitSet toKeep = new BitSet(n);
    BitSet toWrap = new BitSet(n);
    BitSet rewritten = new BitSet(n);
    List<Predicate<ChangeData>> newChildren = Lists.newArrayListWithCapacity(n);
    for (int i = 0; i < n; i++) {
      Predicate<ChangeData> c = in.getChild(i);
      Predicate<ChangeData> nc = rewriteImpl(c, index);
      if (nc == null) {
        toKeep.set(i);
        newChildren.add(c);
      } else if (nc == c) {
        toWrap.set(i);
        newChildren.add(nc);
      } else {
        rewritten.set(i);
        newChildren.add(nc);
      }
    }
    if (toKeep.cardinality() == n) {
      return null; // Can't rewrite any children.
    }
    if (rewritten.cardinality() == n) {
      // All children were partially, but not fully, rewritten.
      return in.copy(newChildren);
    }
    if (toWrap.cardinality() == n) {
      // All children can be fully rewritten, push work to parent.
      return in;
    }
    return partitionChildren(in, newChildren, toWrap, index);
  }

  private boolean isIndexPredicate(Predicate<ChangeData> in, ChangeIndex index) {
    if (!(in instanceof IndexPredicate)) {
      return false;
    }
    IndexPredicate<ChangeData> p = (IndexPredicate<ChangeData>) in;
    return index.getSchema().getFields().containsKey(p.getField().getName());
  }

  private Predicate<ChangeData> partitionChildren(Predicate<ChangeData> in,
      List<Predicate<ChangeData>> newChildren, BitSet toWrap,
      ChangeIndex index) {
    if (toWrap.cardinality() == 1) {
      int i = toWrap.nextSetBit(0);
      newChildren.set(i, wrap(newChildren.get(i), index));
      return in.copy(newChildren);
    }

    // Group all toWrap predicates into a wrapped subtree and place it as a
    // sibling of the non-/partially-wrapped predicates. Assumes partitioning
    // the children into arbitrary subtrees of the same type is logically
    // equivalent to having them as siblings.
    List<Predicate<ChangeData>> wrapped = Lists.newArrayListWithCapacity(
        toWrap.cardinality());
    List<Predicate<ChangeData>> all = Lists.newArrayListWithCapacity(
        newChildren.size() - toWrap.cardinality() + 1);
    for (int i = 0; i < newChildren.size(); i++) {
      Predicate<ChangeData> child = newChildren.get(i);
      if (toWrap.get(i)) {
        wrapped.add(child);
        if (allNonIndexOnly(child)) {
          // Duplicate non-index-only predicate subtrees alongside the wrapped
          // subtrees so they can provide index hints to the DB-based rewriter.
          all.add(child);
        }
      } else {
        all.add(child);
      }
    }
    all.add(wrap(in.copy(wrapped), index));
    return in.copy(all);
  }

  private static boolean allNonIndexOnly(Predicate<ChangeData> p) {
    if (p instanceof IndexPredicate) {
      return !((IndexPredicate<ChangeData>) p).isIndexOnly();
    }
    if (p instanceof AndPredicate
        || p instanceof OrPredicate
        || p instanceof NotPredicate) {
      for (int i = 0; i < p.getChildCount(); i++) {
        if (!allNonIndexOnly(p.getChild(i))) {
          return false;
        }
      }
      return true;
    } else {
      return true;
    }
  }

  private PredicateWrapper wrap(Predicate<ChangeData> p, ChangeIndex index) {
    try {
      return new PredicateWrapper(index, p);
    } catch (QueryParseException e) {
      throw new IllegalStateException(
          "Failed to convert " + p + " to index predicate", e);
    }
  }

  private static boolean isRewritePossible(Predicate<ChangeData> p) {
    if (p.getClass() != AndPredicate.class
        && p.getClass() != OrPredicate.class
        && p.getClass() != NotPredicate.class) {
      return false;
    }
    return p.getChildCount() > 0;
  }
}
