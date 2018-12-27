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
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.git.strategy.SubmitStrategy;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;
import java.util.Set;

class ConflictsPredicate extends OrPredicate<ChangeData> {
  private final String value;

  ConflictsPredicate(Arguments args, String value, List<Change> changes)
      throws OrmException {
    super(predicates(args, value, changes));
    this.value = value;
  }

  private static List<Predicate<ChangeData>> predicates(final Arguments args,
      String value, List<Change> changes) throws OrmException {
    List<Predicate<ChangeData>> changePredicates =
        Lists.newArrayListWithCapacity(changes.size());
    final Provider<ReviewDb> db = args.db;
    for (final Change c : changes) {
      final ChangeDataCache changeDataCache = new ChangeDataCache(
          c, db, args.changeDataFactory, args.projectCache);
      List<String> files = args.changeDataFactory.create(db.get(), c)
          .currentFilePaths();
      List<Predicate<ChangeData>> filePredicates =
          Lists.newArrayListWithCapacity(files.size());
      for (String file : files) {
        filePredicates.add(
            new EqualsPathPredicate(ChangeQueryBuilder.FIELD_PATH, file));
      }

      List<Predicate<ChangeData>> predicatesForOneChange =
          Lists.newArrayListWithCapacity(5);
      predicatesForOneChange.add(
          not(new LegacyChangeIdPredicate(args, c.getId())));
      predicatesForOneChange.add(
          new ProjectPredicate(c.getProject().get()));
      predicatesForOneChange.add(
          new RefPredicate(c.getDest().get()));
      predicatesForOneChange.add(or(filePredicates));
      predicatesForOneChange.add(new OperatorPredicate<ChangeData>(
          ChangeQueryBuilder.FIELD_CONFLICTS, value) {

        @Override
        public boolean match(ChangeData object) throws OrmException {
          Change otherChange = object.change();
          if (otherChange == null) {
            return false;
          }
          if (!otherChange.getDest().equals(c.getDest())) {
            return false;
          }
          SubmitType submitType = getSubmitType(otherChange, object);
          if (submitType == null) {
            return false;
          }
          ObjectId other = ObjectId.fromString(
              object.currentPatchSet().getRevision().get());
          ConflictKey conflictsKey =
              new ConflictKey(changeDataCache.getTestAgainst(), other, submitType,
                  changeDataCache.getProjectState().isUseContentMerge());
          Boolean conflicts = args.conflictsCache.getIfPresent(conflictsKey);
          if (conflicts != null) {
            return conflicts;
          }
          try {
            Repository repo =
                args.repoManager.openRepository(otherChange.getProject());
            try {
              RevWalk rw = new RevWalk(repo) {
                @Override
                protected RevCommit createCommit(AnyObjectId id) {
                  return new CodeReviewCommit(id);
                }
              };
              try {
                RevFlag canMergeFlag = rw.newFlag("CAN_MERGE");
                CodeReviewCommit commit =
                    (CodeReviewCommit) rw.parseCommit(changeDataCache.getTestAgainst());
                SubmitStrategy strategy =
                    args.submitStrategyFactory.create(submitType,
                        db.get(), repo, rw, null, canMergeFlag,
                        getAlreadyAccepted(repo, rw, commit),
                        otherChange.getDest());
                CodeReviewCommit otherCommit =
                    (CodeReviewCommit) rw.parseCommit(other);
                otherCommit.add(canMergeFlag);
                conflicts = !strategy.dryRun(commit, otherCommit);
                args.conflictsCache.put(conflictsKey, conflicts);
                return conflicts;
              } catch (MergeException e) {
                throw new IllegalStateException(e);
              } catch (NoSuchProjectException e) {
                throw new IllegalStateException(e);
              } finally {
                rw.close();
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
                args.changeControlGenericFactory.controlFor(change,
                    args.userFactory.create(change.getOwner()))
                    .getSubmitTypeRecord(db.get(), cd.currentPatchSet(), cd);
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
          Set<RevCommit> alreadyAccepted = Sets.newHashSet();

          if (tip != null) {
            alreadyAccepted.add(tip);
          }

          try {
            for (ObjectId id : changeDataCache.getAlreadyAccepted(repo)) {
              try {
                alreadyAccepted.add(rw.parseCommit(id));
              } catch (IncorrectObjectTypeException iote) {
                // Not a commit? Skip over it.
              }
            }
          } catch (IOException e) {
            throw new MergeException(
                "Failed to determine already accepted commits.", e);
          }

          return alreadyAccepted;
        }
      });
      changePredicates.add(and(predicatesForOneChange));
    }
    return changePredicates;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_CONFLICTS + ":" + value;
  }

  private static class ChangeDataCache {
    private final Change change;
    private final Provider<ReviewDb> db;
    private final ChangeData.Factory changeDataFactory;
    private final ProjectCache projectCache;

    private ObjectId testAgainst;
    private ProjectState projectState;
    private Set<ObjectId> alreadyAccepted;

    ChangeDataCache(Change change, Provider<ReviewDb> db,
        ChangeData.Factory changeDataFactory, ProjectCache projectCache) {
      this.change = change;
      this.db = db;
      this.changeDataFactory = changeDataFactory;
      this.projectCache = projectCache;
    }

    ObjectId getTestAgainst()
        throws OrmException {
      if (testAgainst == null) {
        testAgainst = ObjectId.fromString(
            changeDataFactory.create(db.get(), change)
                .currentPatchSet().getRevision().get());
      }
      return testAgainst;
    }

    ProjectState getProjectState() {
      if (projectState == null) {
        projectState = projectCache.get(change.getProject());
        if (projectState == null) {
          throw new IllegalStateException(
              new NoSuchProjectException(change.getProject()));
        }
      }
      return projectState;
    }

    Set<ObjectId> getAlreadyAccepted(Repository repo) {
      if (alreadyAccepted == null) {
        alreadyAccepted = Sets.newHashSet();
        for (Ref r : repo.getAllRefs().values()) {
          if (r.getName().startsWith(Constants.R_HEADS)
              || r.getName().startsWith(Constants.R_TAGS)) {
            if (r.getObjectId() != null) {
              alreadyAccepted.add(r.getObjectId());
            }
          }
        }
      }
      return alreadyAccepted;
    }
  }
}
