// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/** Computes the relation type between two git revisions in a diff. */
@Singleton
public class RevisionsComparator {
  public enum RelationType {
    /** Both revisions are the same. */
    IDENTICAL,

    /** both revisions have the same parent. */
    SAME_PARENT,

    /** LHS revision is the direct parent of the RHS revision. */
    LHS_PARENT_OF_RHS,

    /** The parent of LHS is an ancestor of the parent of the RHS. */
    LHS_PARENT_ANCESTOR_OF_RHS_PARENT,

    /** The parent of RHS is an ancestor of the parent of the LHS. */
    RHS_PARENT_ANCESTOR_OF_LHS_PARENT,

    /** Both LHS and RHS revisions have a common base that's reachable from both revisions. */
    COMMON_BASE,

    /** Any of the LHS or RHS is a merge commit. */
    MERGE_COMMIT,

    /** Comparison is not in any of the above types. */
    OTHER
  }

  private final GitRepositoryManager repoManager;

  @Inject
  RevisionsComparator(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  public RelationType getRelationType(Project.NameKey project, ObjectId lhs, ObjectId rhs)
      throws IOException {
    if (lhs.equals(rhs)) {
      return RelationType.IDENTICAL;
    }
    try (Repository repo = repoManager.openRepository(project);
        ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      RevCommit lhc = rw.parseCommit(lhs);
      RevCommit rhc = rw.parseCommit(rhs);
      if (lhc.getParentCount() > 1 || rhc.getParentCount() > 1) {
        return RelationType.MERGE_COMMIT;
      } else if (lhc.getParent(0).equals(rhc.getParent(0))) {
        return RelationType.SAME_PARENT;
      } else if (isParent(lhc, rhc)) {
        return RelationType.LHS_PARENT_OF_RHS;
      }
      Optional<RevCommit> maybeMergeBase = getMergeBase(rw, lhc, rhc);
      if (maybeMergeBase.isEmpty()) {
        return RelationType.OTHER;
      }
      RevCommit mergeBase = maybeMergeBase.get();
      if (mergeBase.equals(lhc.getParent(0))) {
        return RelationType.LHS_PARENT_ANCESTOR_OF_RHS_PARENT;
      } else if (mergeBase.equals(rhc.getParent(0))) {
        return RelationType.RHS_PARENT_ANCESTOR_OF_LHS_PARENT;
      }

      return RelationType.COMMON_BASE;
    }
  }

  public int getNumParents(Project.NameKey project, ObjectId revision) throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      return rw.parseCommit(revision).getParentCount();
    }
  }

  /** Return true if {@code lhc} commit is the direct parent of {@code rhc} commit. */
  private boolean isParent(RevCommit lhc, RevCommit rhc) {
    return lhc.getParentCount() > 0 && rhc.getParent(0).equals(lhc);
  }

  /**
   * Computes the merge base for the two commits {@code c1} and {@code c2}.
   *
   * @return an Optional containing the merge base commit, or empty if there is no merge base.
   */
  private Optional<RevCommit> getMergeBase(RevWalk rw, RevCommit c1, RevCommit c2)
      throws IOException {
    rw.setRevFilter(RevFilter.MERGE_BASE);
    rw.markStart(c1);
    rw.markStart(c2);
    RevCommit base = rw.next();
    if (base == null) {
      return Optional.empty();
    }
    return Optional.of(base);
  }
}
