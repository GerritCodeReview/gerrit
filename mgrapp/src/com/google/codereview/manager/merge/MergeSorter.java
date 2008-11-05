// Copyright 2008 Google Inc.
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

package com.google.codereview.manager.merge;

import com.google.codereview.internal.PostMergeResult.MergeResultItem;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevCommitList;
import org.spearce.jgit.revwalk.RevFlag;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class MergeSorter {
  private final RevWalk rw;
  private final RevCommit base;
  private final RevFlag CAN_MERGE;
  private final Set<RevCommit> accepted;

  MergeSorter(final RevWalk walk, final RevCommit branchHead)
      throws IOException {
    rw = walk;
    CAN_MERGE = rw.newFlag("CAN_MERGE");
    base = branchHead;

    accepted = new HashSet<RevCommit>();
    for (final Ref r : rw.getRepository().getAllRefs().values()) {
      if (r.getName().startsWith(Constants.R_HEADS)
          || r.getName().startsWith(Constants.R_TAGS)) {
        try {
          accepted.add(rw.parseCommit(r.getObjectId()));
        } catch (IncorrectObjectTypeException iote) {
          // Not a commit? Skip over it.
        }
      }
    }
  }

  Collection<CodeReviewCommit> sort(final Collection<CodeReviewCommit> incoming)
      throws IOException {
    final Set<CodeReviewCommit> heads = new HashSet<CodeReviewCommit>();
    final Set<CodeReviewCommit> sort = prepareList(incoming);
    while (!sort.isEmpty()) {
      final CodeReviewCommit n = removeOne(sort);

      rw.resetRetain(CAN_MERGE);
      rw.markStart(n);
      if (base != null) {
        rw.markUninteresting(base);
      }
      for (RevCommit c : accepted) {
        rw.markUninteresting(c);
      }

      RevCommit c;
      final RevCommitList<RevCommit> contents = new RevCommitList<RevCommit>();
      while ((c = rw.next()) != null) {
        if (!c.has(CAN_MERGE)) {
          // We cannot merge n as it would bring something we
          // aren't permitted to merge at this time. Drop n.
          //
          if (n.missing == null) {
            n.statusCode = MergeResultItem.CodeType.MISSING_DEPENDENCY;
            n.missing = new ArrayList<CodeReviewCommit>();
          }
          n.missing.add((CodeReviewCommit) c);
        } else {
          contents.add(c);
        }
      }

      if (n.statusCode == MergeResultItem.CodeType.MISSING_DEPENDENCY) {
        continue;
      }

      // Anything reachable through us is better merged by just
      // merging us directly. So prune our ancestors out and let
      // us merge instead.
      //
      sort.removeAll(contents);
      heads.removeAll(contents);
      heads.add(n);
    }
    return heads;
  }

  private Set<CodeReviewCommit> prepareList(
      final Collection<CodeReviewCommit> in) {
    final HashSet<CodeReviewCommit> sort = new HashSet<CodeReviewCommit>();
    for (final CodeReviewCommit c : in) {
      if (!c.has(CAN_MERGE)) {
        c.add(CAN_MERGE);
        sort.add(c);
      }
    }
    return sort;
  }

  private static <T> T removeOne(final Collection<T> c) {
    final Iterator<T> i = c.iterator();
    final T r = i.next();
    i.remove();
    return r;
  }
}
