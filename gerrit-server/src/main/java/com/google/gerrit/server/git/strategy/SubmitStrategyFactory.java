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

package com.google.gerrit.server.git.strategy;

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/** Factory to create a {@link SubmitStrategy} for a {@link SubmitType}. */
@Singleton
public class SubmitStrategyFactory {
  private static final Logger log = LoggerFactory
      .getLogger(SubmitStrategyFactory.class);

  private final SubmitStrategy.Arguments.Factory argsFactory;

  @Inject
  SubmitStrategyFactory(SubmitStrategy.Arguments.Factory argsFactory) {
    this.argsFactory = argsFactory;
  }

  public SubmitStrategy create(SubmitType submitType, ReviewDb db,
      Repository repo, CodeReviewRevWalk rw, ObjectInserter inserter,
      RevFlag canMergeFlag, Set<RevCommit> alreadyAccepted,
      Branch.NameKey destBranch, IdentifiedUser caller)
      throws IntegrationException, NoSuchProjectException {
    SubmitStrategy.Arguments args = argsFactory.create(destBranch, rw, caller,
        inserter, repo, canMergeFlag, db, alreadyAccepted);
    if (args.project == null) {
      throw new NoSuchProjectException(destBranch.getParentKey());
    }
    switch (submitType) {
      case CHERRY_PICK:
        return new CherryPick(args);
      case FAST_FORWARD_ONLY:
        return new FastForwardOnly(args);
      case MERGE_ALWAYS:
        return new MergeAlways(args);
      case MERGE_IF_NECESSARY:
        return new MergeIfNecessary(args);
      case REBASE_IF_NECESSARY:
        return new RebaseIfNecessary(args);
      default:
        String errorMsg = "No submit strategy for: " + submitType;
        log.error(errorMsg);
        throw new IntegrationException(errorMsg);
    }
  }
}
