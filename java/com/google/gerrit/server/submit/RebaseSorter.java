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

package com.google.gerrit.server.submit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

public class RebaseSorter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CurrentUser caller;
  private final CodeReviewRevWalk rw;
  private final RevFlag canMergeFlag;
  private final RevCommit initialTip;
  private final Set<RevCommit> alreadyAccepted;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Set<CodeReviewCommit> incoming;

  public RebaseSorter(
      CurrentUser caller,
      CodeReviewRevWalk rw,
      RevCommit initialTip,
      Set<RevCommit> alreadyAccepted,
      RevFlag canMergeFlag,
      Provider<InternalChangeQuery> queryProvider,
      Set<CodeReviewCommit> incoming) {
    this.caller = caller;
    this.rw = rw;
    this.canMergeFlag = canMergeFlag;
    this.initialTip = initialTip;
    this.alreadyAccepted = alreadyAccepted;
    this.queryProvider = queryProvider;
    this.incoming = incoming;
  }

  public List<CodeReviewCommit> sort(Collection<CodeReviewCommit> toSort) throws IOException {
    final List<CodeReviewCommit> sorted = new ArrayList<>();
    final Set<CodeReviewCommit> sort = new HashSet<>(toSort);
    final Set<ObjectId> uninterestingParents = new HashSet<>();
    while (!sort.isEmpty()) {
      final CodeReviewCommit n = removeOne(sort);
      collectUninterestingParents(n, uninterestingParents);

      rw.resetRetain(canMergeFlag);
      rw.markStart(n);
      if (initialTip != null) {
        rw.markUninteresting(initialTip);
      }

      CodeReviewCommit c;
      final List<CodeReviewCommit> contents = new ArrayList<>();
      while ((c = rw.next()) != null) {
        collectUninterestingParents(c, uninterestingParents);

        if (!c.has(canMergeFlag) || !incoming.contains(c)) {
          if (isMergedInBranchAsSubmittedChange(c, n.change().getDest())
              || isAlreadyMergedInAnyBranch(c)) {
            rw.markUninteresting(c);
          } else {
            // We cannot merge n as it would bring something we
            // aren't permitted to merge at this time. Drop n.
            //
            n.setStatusCode(CommitMergeStatus.MISSING_DEPENDENCY);
            n.setStatusMessage(
                CommitMergeStatus.createMissingDependencyMessage(
                    caller, queryProvider, n.name(), c.name()));
          }
          // Stop RevWalk because c is either a merged commit or a missing
          // dependency. Not need to walk further.
          break;
        }
        contents.add(c);
      }

      if (n.getStatusCode() == CommitMergeStatus.MISSING_DEPENDENCY) {
        continue;
      }

      sort.removeAll(contents);
      Collections.reverse(contents);
      sorted.removeAll(contents);
      sorted.addAll(contents);
    }
    sorted.removeAll(uninterestingParents);
    return sorted;
  }

  /**
   * Rebasing merge commits is done by rebasing their first parent commit, i.e. the first parent is
   * updated to the new base while the second parent stays intact. This means for chains of changes
   * we only need to rebase changes that are reachable via the first parents. Changes that are
   * reachable via second parents do not need to be rebased (since the second parent of merge
   * commits stays intact) which is why we filter them out here by marking them as uninteresting.
   */
  private void collectUninterestingParents(CodeReviewCommit c, Set<ObjectId> uninterestingParents)
      throws IOException {
    if (c.getParentCount() > 0) {
      for (int parent = 1; parent < c.getParentCount(); parent++) {
        uninterestingParents.add(c.getParent(parent));
        rw.markUninteresting(c.getParent(parent));
      }
    }
  }

  private boolean isAlreadyMergedInAnyBranch(CodeReviewCommit commit) throws IOException {
    try (CodeReviewRevWalk mirw = CodeReviewCommit.newRevWalk(rw.getObjectReader())) {
      mirw.reset();
      mirw.markStart(commit);
      Set<RevCommit> parsed = new HashSet<>();
      for (RevCommit accepted : alreadyAccepted) {
        parsed.add(mirw.parseCommit(accepted));
      }
      // check if the commit is merged in other branches
      return mirw.isMergedIntoAnyCommit(mirw.parseCommit(commit), parsed);
    } catch (StorageException e) {
      throw new IOException(e);
    }
  }

  private boolean isMergedInBranchAsSubmittedChange(CodeReviewCommit commit, BranchNameKey dest) {
    List<ChangeData> changes = queryProvider.get().byBranchCommit(dest, commit.getId().getName());
    for (ChangeData change : changes) {
      if (change.change().isMerged()) {
        logger.atFine().log(
            "Dependency %s associated with merged change %s.", commit.getName(), change.getId());
        return true;
      }
    }
    return false;
  }

  private static <T> T removeOne(Collection<T> c) {
    final Iterator<T> i = c.iterator();
    final T r = i.next();
    i.remove();
    return r;
  }
}
