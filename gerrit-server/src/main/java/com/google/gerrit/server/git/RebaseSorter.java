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

import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.strategy.CommitMergeStatus;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RebaseSorter {
  private static final Logger log = LoggerFactory.getLogger(RebaseSorter.class);

  private final CodeReviewRevWalk rw;
  private final RevFlag canMergeFlag;
  private final RevCommit initialTip;
  private final Set<RevCommit> alreadyAccepted;
  private final InternalChangeQuery internalChangeQuery;
  private final ChangeKindCache changeKindCache;
  private final Repository repo;

  public RebaseSorter(
      CodeReviewRevWalk rw,
      RevCommit initialTip,
      Set<RevCommit> alreadyAccepted,
      RevFlag canMergeFlag,
      InternalChangeQuery internalChangeQuery,
      ChangeKindCache changeKindCache,
      Repository repo) {
    this.rw = rw;
    this.canMergeFlag = canMergeFlag;
    this.initialTip = initialTip;
    this.alreadyAccepted = alreadyAccepted;
    this.internalChangeQuery = internalChangeQuery;
    this.changeKindCache = changeKindCache;
    this.repo = repo;
  }

  public List<CodeReviewCommit> sort(Collection<CodeReviewCommit> incoming) throws IOException {
    final List<CodeReviewCommit> sorted = new ArrayList<>();
    final Set<CodeReviewCommit> sort = new HashSet<>(incoming);
    while (!sort.isEmpty()) {
      final CodeReviewCommit n = removeOne(sort);

      rw.resetRetain(canMergeFlag);
      rw.markStart(n);
      if (initialTip != null) {
        rw.markUninteresting(initialTip);
      }

      CodeReviewCommit c;
      final List<CodeReviewCommit> contents = new ArrayList<>();
      while ((c = rw.next()) != null) {
        if (!c.has(canMergeFlag) || !incoming.contains(c)) {
          if (isAlreadyMerged(c, n.change().getDest())) {
            rw.markUninteresting(c);
          } else {
            // We cannot merge n as it would bring something we
            // aren't permitted to merge at this time. Drop n.
            //
            n.setStatusCode(CommitMergeStatus.MISSING_DEPENDENCY);
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
    return sorted;
  }

  private boolean isAlreadyMerged(CodeReviewCommit commit, Branch.NameKey dest) throws IOException {
    try (CodeReviewRevWalk mirw = CodeReviewCommit.newRevWalk(rw.getObjectReader())) {
      mirw.reset();
      mirw.markStart(commit);
      // check if the commit is merged in other branches
      for (RevCommit accepted : alreadyAccepted) {
        if (mirw.isMergedInto(mirw.parseCommit(accepted), mirw.parseCommit(commit))) {
          log.debug("Dependency {} merged into branch head {}.", commit.getName(),
              accepted.getName());
          return true;
        }
      }

      // check if the commit associated change is merged in the same branch
      List<ChangeData> changes = internalChangeQuery.byCommit(commit);
      for (ChangeData change : changes) {
        if (change.change().getStatus() == Status.MERGED
            && change.change().getDest().equals(dest)
            && !isRework(dest.getParentKey(), commit, change)) {
          log.debug("Dependency {} associated with merged change {}.", commit.getName(),
              change.getId());
          return true;
        }
      }
      return false;
    } catch (OrmException e) {
      throw new IOException(e);
    }
  }

  private boolean isRework(Project.NameKey project, RevCommit oldCommit, ChangeData change)
      throws OrmException, IOException {
    RevCommit currentCommit =
        rw.parseCommit(ObjectId.fromString(change.currentPatchSet().getRevision().get()));
    return ChangeKind.REWORK
        == changeKindCache.getChangeKind(project, repo, oldCommit, currentCommit);
  }

  private static <T> T removeOne(final Collection<T> c) {
    final Iterator<T> i = c.iterator();
    final T r = i.next();
    i.remove();
    return r;
  }
}
