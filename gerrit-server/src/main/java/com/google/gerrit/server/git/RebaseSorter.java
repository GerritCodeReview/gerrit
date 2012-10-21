// Copyright (C) 2012 The Android Open Source Project
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
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

class RebaseSorter {

  private final RevWalk rw;
  private final RevFlag canMergeFlag;
  private final Set<RevCommit> accepted;

  RebaseSorter(final RevWalk rw, final Set<RevCommit> alreadyAccepted,
      final RevFlag canMergeFlag) {
    this.rw = rw;
    this.canMergeFlag = canMergeFlag;
    this.accepted = alreadyAccepted;
  }

  List<CodeReviewCommit> sort(Collection<CodeReviewCommit> incoming)
      throws IOException {
    final List<CodeReviewCommit> sorted = new ArrayList<CodeReviewCommit>();
    final Set<CodeReviewCommit> sort = new HashSet<CodeReviewCommit>(incoming);
    while (!sort.isEmpty()) {
      final CodeReviewCommit n = removeOne(sort);

      rw.resetRetain(canMergeFlag);
      rw.markStart(n);
      for (RevCommit c : accepted) {
        rw.markUninteresting(c);
      }

      CodeReviewCommit c;
      final List<CodeReviewCommit> contents = new ArrayList<CodeReviewCommit>();
      while ((c = (CodeReviewCommit) rw.next()) != null) {
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

      sort.removeAll(contents);
      Collections.reverse(contents);
      sorted.removeAll(contents);
      sorted.addAll(contents);
    }
    return sorted;
  }

  private static <T> T removeOne(final Collection<T> c) {
    final Iterator<T> i = c.iterator();
    final T r = i.next();
    i.remove();
    return r;
  }
}
