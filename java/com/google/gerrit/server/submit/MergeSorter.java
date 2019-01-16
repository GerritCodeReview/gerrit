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

package com.google.gerrit.server.submit;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevFlag;

public class MergeSorter {
  @Nullable private final CurrentUser caller;
  private final CodeReviewRevWalk rw;
  private final RevFlag canMergeFlag;
  private final Set<RevCommit> accepted;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Set<CodeReviewCommit> incoming;

  public MergeSorter(
      @Nullable CurrentUser caller,
      CodeReviewRevWalk rw,
      Set<RevCommit> alreadyAccepted,
      RevFlag canMergeFlag,
      Provider<InternalChangeQuery> queryProvider,
      Set<CodeReviewCommit> incoming) {
    this.caller = caller;
    this.rw = rw;
    this.canMergeFlag = canMergeFlag;
    this.accepted = alreadyAccepted;
    this.queryProvider = queryProvider;
    this.incoming = incoming;
  }

  public Collection<CodeReviewCommit> sort(Collection<CodeReviewCommit> toMerge)
      throws IOException, StorageException {
    final Set<CodeReviewCommit> heads = new HashSet<>();
    final Set<CodeReviewCommit> sort = new HashSet<>(toMerge);
    while (!sort.isEmpty()) {
      final CodeReviewCommit n = removeOne(sort);

      rw.resetRetain(canMergeFlag);
      rw.markStart(n);
      for (RevCommit c : accepted) {
        rw.markUninteresting(c);
      }

      CodeReviewCommit c;
      RevCommitList<RevCommit> contents = new RevCommitList<>();
      while ((c = rw.next()) != null) {
        if (!c.has(canMergeFlag) || !incoming.contains(c)) {
          // We cannot merge n as it would bring something we
          // aren't permitted to merge at this time. Drop n.
          //
          n.setStatusCode(CommitMergeStatus.MISSING_DEPENDENCY);
          n.setStatusMessage(
              CommitMergeStatus.createMissingDependencyMessage(
                  caller, queryProvider, n.name(), c.name()));
          break;
        }
        contents.add(c);
      }

      if (n.getStatusCode() == CommitMergeStatus.MISSING_DEPENDENCY) {
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

  private static <T> T removeOne(Collection<T> c) {
    final Iterator<T> i = c.iterator();
    final T r = i.next();
    i.remove();
    return r;
  }
}
