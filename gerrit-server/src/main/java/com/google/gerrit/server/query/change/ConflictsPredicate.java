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
// limitations under the License.

package com.google.gerrit.server.query.change;

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.SubmitStrategy;
import com.google.gerrit.server.git.SubmitStrategyFactory;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ConflictsPredicate extends OrPredicate<ChangeData> {
  private final String value;

  ConflictsPredicate(Provider<ReviewDb> db, PatchListCache plc,
      SubmitStrategyFactory submitStrategyFactory,
      ChangeControl.GenericFactory changeControlFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      GitRepositoryManager repoManager, String value, List<Change> changes)
      throws OrmException {
    super(predicates(db, plc, submitStrategyFactory, changeControlFactory,
        identifiedUserFactory, repoManager, value, changes));
    this.value = value;
  }

  private static List<Predicate<ChangeData>> predicates(
      final Provider<ReviewDb> db, final PatchListCache plc,
      final SubmitStrategyFactory submitStrategyFactory,
      final ChangeControl.GenericFactory changeControlFactory,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final GitRepositoryManager repoManager, final String value,
      List<Change> changes)
      throws OrmException {
    List<Predicate<ChangeData>> r = Lists.newArrayList();
    for (final Change c : changes) {
      List<Predicate<ChangeData>> r2 = Lists.newArrayList();
      for (String file : (new ChangeData(c)).currentFilePaths(db, plc)) {
        r2.add(new EqualsFilePredicate(db, plc, file));
      }

      List<Predicate<ChangeData>> r3 = Lists.newArrayList();
      r3.add(not(new LegacyChangeIdPredicate(db, c.getId())));
      r3.add(or(r2));
      r3.add(new OperatorPredicate<ChangeData>(ChangeQueryBuilder.FIELD_CONFLICTS,
          value) {

        @Override
        public boolean match(ChangeData object) throws OrmException {
          Change otherChange = object.change(db);
          try {
            Repository repo =
                repoManager.openRepository(otherChange.getProject());
            try {
              ObjectInserter inserter = repo.newObjectInserter();
              try {
                RevWalk rw = new RevWalk(repo) {
                  @Override
                  protected RevCommit createCommit(final AnyObjectId id) {
                    return new CodeReviewCommit(id);
                  }
                };
                try {
                  RevFlag canMergeFlag = rw.newFlag("CAN_MERGE");
                  CodeReviewCommit commit =
                      (CodeReviewCommit) rw.parseCommit(ObjectId.fromString(
                          new ChangeData(c).currentPatchSet(db).getRevision().get()));
                  SubmitStrategy strategy =
                      submitStrategyFactory.create(getSubmitType(otherChange, object),
                          db.get(), repo, rw, inserter, canMergeFlag,
                          getAlreadyAccepted(repo, rw, commit),
                          otherChange.getDest());
                  CodeReviewCommit otherCommit =
                      (CodeReviewCommit) rw.parseCommit(ObjectId.fromString(
                          object.currentPatchSet(db).getRevision().get()));
                  otherCommit.add(canMergeFlag);
                  return !strategy.dryRun(commit, otherCommit);
                } catch (MergeException e) {
                  throw new IllegalStateException();
                } catch (NoSuchProjectException e) {
                  throw new IllegalStateException();
                } finally {
                  rw.release();
                }
              } finally {
                inserter.release();
              }
            } finally {
              repo.close();
            }
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }

        @Override
        public int getCost() {
          return 5;
        }

        private SubmitType getSubmitType(Change change, ChangeData cd) throws OrmException {
          try {
            final SubmitTypeRecord r =
                changeControlFactory.controlFor(change,
                    identifiedUserFactory.create(change.getOwner()))
                    .getSubmitTypeRecord(db.get(), cd.currentPatchSet(db), cd);
            if (r.status != SubmitTypeRecord.Status.OK) {
              return null;
            }
            return r.type;
          } catch (NoSuchChangeException e) {
            return null;
          }
        }

        private Set<RevCommit> getAlreadyAccepted(Repository repo, RevWalk rw,
            CodeReviewCommit tip) throws MergeException {
          final Set<RevCommit> alreadyAccepted = new HashSet<RevCommit>();

          if (tip != null) {
            alreadyAccepted.add(tip);
          }

          try {
            for (final Ref r : repo.getAllRefs().values()) {
              if (r.getName().startsWith(Constants.R_HEADS)
                  || r.getName().startsWith(Constants.R_TAGS)) {
                try {
                  alreadyAccepted.add(rw.parseCommit(r.getObjectId()));
                } catch (IncorrectObjectTypeException iote) {
                  // Not a commit? Skip over it.
                }
              }
            }
          } catch (IOException e) {
            throw new MergeException(
                "Failed to determine already accepted commits.", e);
          }

          return alreadyAccepted;
        }
      });
      r.add(and(r3));
    }
    return r;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_CONFLICTS + ":" + value;
  }
}
