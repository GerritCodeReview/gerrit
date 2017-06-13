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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.strategy.SubmitDryRun;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

public class ConflictsPredicate extends OrPredicate<ChangeData> {
  // UI code may depend on this string, so use caution when changing.
  protected static final String TOO_MANY_FILES = "too many files to find conflicts";

  protected final String value;

  public ConflictsPredicate(Arguments args, String value, List<Change> changes)
      throws QueryParseException, OrmException {
    super(predicates(args, value, changes));
    this.value = value;
  }

  public static List<Predicate<ChangeData>> predicates(
      final Arguments args, String value, List<Change> changes)
      throws QueryParseException, OrmException {
    int indexTerms = 0;

    List<Predicate<ChangeData>> changePredicates = Lists.newArrayListWithCapacity(changes.size());
    final Provider<ReviewDb> db = args.db;
    for (Change c : changes) {
      final ChangeDataCache changeDataCache =
          new ChangeDataCache(c, db, args.changeDataFactory, args.projectCache);
      List<String> files = listFiles(c, args, changeDataCache);
      indexTerms += 3 + files.size();
      if (indexTerms > args.indexConfig.maxTerms()) {
        // Short-circuit with a nice error message if we exceed the index
        // backend's term limit. This assumes that "conflicts:foo" is the entire
        // query; if there are more terms in the input, we might not
        // short-circuit here, which will result in a more generic error message
        // later on in the query parsing.
        throw new QueryParseException(TOO_MANY_FILES);
      }

      List<Predicate<ChangeData>> filePredicates = Lists.newArrayListWithCapacity(files.size());
      for (String file : files) {
        filePredicates.add(new EqualsPathPredicate(ChangeQueryBuilder.FIELD_PATH, file));
      }

      List<Predicate<ChangeData>> predicatesForOneChange = Lists.newArrayListWithCapacity(5);
      predicatesForOneChange.add(not(new LegacyChangeIdPredicate(c.getId())));
      predicatesForOneChange.add(new ProjectPredicate(c.getProject().get()));
      predicatesForOneChange.add(new RefPredicate(c.getDest().get()));

      predicatesForOneChange.add(or(or(filePredicates), new IsMergePredicate(args, value)));

      predicatesForOneChange.add(
          new ChangeOperatorPredicate(ChangeQueryBuilder.FIELD_CONFLICTS, value) {

            @Override
            public boolean match(ChangeData object) throws OrmException {
              Change otherChange = object.change();
              if (otherChange == null) {
                return false;
              }
              if (!otherChange.getDest().equals(c.getDest())) {
                return false;
              }
              SubmitTypeRecord str = object.submitTypeRecord();
              if (!str.isOk()) {
                return false;
              }
              ObjectId other = ObjectId.fromString(object.currentPatchSet().getRevision().get());
              ConflictKey conflictsKey =
                  new ConflictKey(
                      changeDataCache.getTestAgainst(),
                      other,
                      str.type,
                      changeDataCache.getProjectState().isUseContentMerge());
              Boolean conflicts = args.conflictsCache.getIfPresent(conflictsKey);
              if (conflicts != null) {
                return conflicts;
              }
              try (Repository repo = args.repoManager.openRepository(otherChange.getProject());
                  CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
                conflicts =
                    !args.submitDryRun.run(
                        str.type,
                        repo,
                        rw,
                        otherChange.getDest(),
                        changeDataCache.getTestAgainst(),
                        other,
                        getAlreadyAccepted(repo, rw));
                args.conflictsCache.put(conflictsKey, conflicts);
                return conflicts;
              } catch (IntegrationException | NoSuchProjectException | IOException e) {
                throw new OrmException(e);
              }
            }

            @Override
            public int getCost() {
              return 5;
            }

            private Set<RevCommit> getAlreadyAccepted(Repository repo, RevWalk rw)
                throws IntegrationException {
              try {
                Set<RevCommit> accepted = new HashSet<>();
                SubmitDryRun.addCommits(changeDataCache.getAlreadyAccepted(repo), rw, accepted);
                ObjectId tip = changeDataCache.getTestAgainst();
                if (tip != null) {
                  accepted.add(rw.parseCommit(tip));
                }
                return accepted;
              } catch (OrmException | IOException e) {
                throw new IntegrationException("Failed to determine already accepted commits.", e);
              }
            }
          });
      changePredicates.add(and(predicatesForOneChange));
    }
    return changePredicates;
  }

  public static List<String> listFiles(Change c, Arguments args, ChangeDataCache changeDataCache)
      throws OrmException {
    try (Repository repo = args.repoManager.openRepository(c.getProject());
        RevWalk rw = new RevWalk(repo)) {
      RevCommit ps = rw.parseCommit(changeDataCache.getTestAgainst());
      if (ps.getParentCount() > 1) {
        String dest = c.getDest().get();
        Ref destBranch = repo.getRefDatabase().getRef(dest);
        destBranch.getObjectId();
        rw.setRevFilter(RevFilter.MERGE_BASE);
        rw.markStart(rw.parseCommit(destBranch.getObjectId()));
        rw.markStart(ps);
        RevCommit base = rw.next();
        // TODO(zivkov): handle the case with multiple merge bases

        List<String> files = new ArrayList<>();
        try (TreeWalk tw = new TreeWalk(repo)) {
          if (base != null) {
            tw.setFilter(TreeFilter.ANY_DIFF);
            tw.addTree(base.getTree());
          }
          tw.addTree(ps.getTree());
          tw.setRecursive(true);
          while (tw.next()) {
            files.add(tw.getPathString());
          }
        }
        return files;
      }
      return args.changeDataFactory.create(args.db.get(), c).currentFilePaths();
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_CONFLICTS + ":" + value;
  }

  public static class ChangeDataCache {
    protected final Change change;
    protected final Provider<ReviewDb> db;
    protected final ChangeData.Factory changeDataFactory;
    protected final ProjectCache projectCache;

    protected ObjectId testAgainst;
    protected ProjectState projectState;
    protected Iterable<ObjectId> alreadyAccepted;

    public ChangeDataCache(
        Change change,
        Provider<ReviewDb> db,
        ChangeData.Factory changeDataFactory,
        ProjectCache projectCache) {
      this.change = change;
      this.db = db;
      this.changeDataFactory = changeDataFactory;
      this.projectCache = projectCache;
    }

    protected ObjectId getTestAgainst() throws OrmException {
      if (testAgainst == null) {
        testAgainst =
            ObjectId.fromString(
                changeDataFactory.create(db.get(), change).currentPatchSet().getRevision().get());
      }
      return testAgainst;
    }

    protected ProjectState getProjectState() {
      if (projectState == null) {
        projectState = projectCache.get(change.getProject());
        if (projectState == null) {
          throw new IllegalStateException(new NoSuchProjectException(change.getProject()));
        }
      }
      return projectState;
    }

    protected Iterable<ObjectId> getAlreadyAccepted(Repository repo) throws IOException {
      if (alreadyAccepted == null) {
        alreadyAccepted = SubmitDryRun.getAlreadyAccepted(repo);
      }
      return alreadyAccepted;
    }
  }
}
