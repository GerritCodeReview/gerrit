// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.server.git.SubtreeSplitCommand.SplitContext;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * This RevFilter will stop the walker from visiting commits that are
 * determined to be part of a sub tree.
 */
public class SubtreeFilter extends RevFilter {

  private HashMap<String, ObjectId> footerBuffer = new HashMap<String, ObjectId>();
  private HashSet<RevCommit> subTrees;
  private Repository repo;
  private boolean includeBoundarySubtrees;
  private Set<SplitContext> splitters;

  public SubtreeFilter(Repository repo, boolean includeBoundarySubtrees) {
    this(repo);
    this.includeBoundarySubtrees = includeBoundarySubtrees;
    if (includeBoundarySubtrees) {
      subTrees = new HashSet<RevCommit>();
    }
  }

  public SubtreeFilter(Repository repo) {
    this.repo = repo;
  }

  protected void setSplitters(Set<SplitContext> splitters) {
    this.splitters = splitters;
  }

  @Override
  public boolean include(RevWalk walker, RevCommit cmit)
      throws StopWalkException, MissingObjectException,
      IncorrectObjectTypeException, IOException {

    walker.parseBody(cmit);

    if (includeBoundarySubtrees && subTrees.contains(cmit)) {
      for (RevCommit parent : cmit.getParents()) {
        walker.markUninteresting(parent);
      }
    } else {
      footerBuffer.clear();
      SubtreeSplitCommand.parseSubtreeFooters(repo, cmit, footerBuffer);
      for (String subtreeName : footerBuffer.keySet()) {

        RevCommit subtreeCommit = walker.parseCommit(footerBuffer.get(subtreeName));

        if (includeBoundarySubtrees) {
          subTrees.add(subtreeCommit);
        } else {
          walker.markUninteresting(subtreeCommit);
        }

        // If splitters have been supplied, update them.
        if (splitters != null) {
          for (SplitContext splitter : splitters) {
            splitter.mNewParents.put(subtreeCommit, SubtreeSplitCommand.NO_SUBTREE);
          }
          for (SplitContext splitter : splitters) {
            if (splitter.getId().equals(subtreeName)) {
              splitter.mNewParents.put(subtreeCommit, subtreeCommit);
            }
          }
        }

      }
    }

    return true;
  }

  @Override
  public RevFilter clone() {
    return new SubtreeFilter(repo);
  }

}