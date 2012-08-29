// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.git;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class MergeSorter {
  private final RevWalk rw;
  private final RevFlag canMergeFlag;
  private final Set<RevCommit> accepted;

  MergeSorter(final RevWalk rw, final Set<RevCommit> alreadyAccepted,
      final RevFlag canMergeFlag) {
    this.rw = rw;
    this.canMergeFlag = canMergeFlag;
    this.accepted = alreadyAccepted;
  }

  Collection<CodeReviewCommit> sort(final Collection<CodeReviewCommit> incoming)
      throws IOException {
    final Set<CodeReviewCommit> heads = new HashSet<CodeReviewCommit>();
    final Set<CodeReviewCommit> sort = new HashSet<CodeReviewCommit>(incoming);
    while (!sort.isEmpty()) {
      final CodeReviewCommit n = removeOne(sort);

      rw.resetRetain(canMergeFlag);
      rw.markStart(n);
      for (RevCommit c : accepted) {
        rw.markUninteresting(c);
      }

      RevCommit c;
      final RevCommitList<RevCommit> contents = new RevCommitList<RevCommit>();
      while ((c = rw.next()) != null) {
        if (!c.has(canMergeFlag) || !incoming.contains(c)) {
          // We cannot merge n as it would bring something we
          // aren't permitted to merge at this time. Drop n.
          //
          if (n.missing == null) {
            n.statusCode = CommitMergeStatus.MISSING_DEPENDENCY;
            n.missing = new ArrayList<CodeReviewCommit>();
          }
          n.missing.add((CodeReviewCommit) c);
        } else {
          contents.add(c);
        }
      }

      if (n.statusCode == CommitMergeStatus.MISSING_DEPENDENCY) {
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

  private static <T> T removeOne(final Collection<T> c) {
    final Iterator<T> i = c.iterator();
    final T r = i.next();
    i.remove();
    return r;
  }
}
