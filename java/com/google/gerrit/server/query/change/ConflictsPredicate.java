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

import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ConflictsPredicate {
  // UI code may depend on this string, so use caution when changing.
  protected static final String TOO_MANY_FILES = "too many files to find conflicts";

  private ConflictsPredicate() {}

  public static Predicate<ChangeData> create(Arguments args, String value, Change c)
      throws QueryParseException, OrmException {
    ChangeData cd;
    List<String> files;
    try {
      cd = args.changeDataFactory.create(args.db.get(), c);
      files = cd.currentFilePaths();
    } catch (IOException e) {
      throw new OrmException(e);
    }

    if (3 + files.size() > args.indexConfig.maxTerms()) {
      // Short-circuit with a nice error message if we exceed the index
      // backend's term limit. This assumes that "conflicts:foo" is the entire
      // query; if there are more terms in the input, we might not
      // short-circuit here, which will result in a more generic error message
      // later on in the query parsing.
      throw new QueryParseException(TOO_MANY_FILES);
    }

    List<Predicate<ChangeData>> filePredicates = new ArrayList<>(files.size());
    for (String file : files) {
      filePredicates.add(new EqualsPathPredicate(ChangeQueryBuilder.FIELD_PATH, file));
    }

    List<Predicate<ChangeData>> and = new ArrayList<>(5);
    and.add(new ProjectPredicate(c.getProject().get()));
    and.add(new RefPredicate(c.getDest().get()));
    and.add(Predicate.not(new LegacyChangeIdPredicate(c.getId())));
    and.add(Predicate.or(filePredicates));

    ChangeDataCache changeDataCache = new ChangeDataCache(cd, args.projectAccessorFactory);
    and.add(new CheckConflict(ChangeQueryBuilder.FIELD_CONFLICTS, value, args, c, changeDataCache));
    return Predicate.and(and);
  }

  private static final class CheckConflict extends ChangeOperatorPredicate {
    private final Arguments args;
    private final Branch.NameKey dest;
    private final ChangeDataCache changeDataCache;

    CheckConflict(
        String field, String value, Arguments args, Change c, ChangeDataCache changeDataCache) {
      super(field, value);
      this.args = args;
      this.dest = c.getDest();
      this.changeDataCache = changeDataCache;
    }

    @Override
    public boolean match(ChangeData object) throws OrmException {
      Change otherChange = object.change();
      if (otherChange == null || !otherChange.getDest().equals(dest)) {
        return false;
      }

      SubmitTypeRecord str = object.submitTypeRecord();
      if (!str.isOk()) {
        return false;
      }

      ProjectAccessor projectAccessor;
      try {
        projectAccessor = changeDataCache.getProjectAccessor();
      } catch (NoSuchProjectException | IOException e) {
        return false;
      }

      ObjectId other = ObjectId.fromString(object.currentPatchSet().getRevision().get());
      ConflictKey conflictsKey =
          new ConflictKey(
              changeDataCache.getTestAgainst(),
              other,
              str.type,
              projectAccessor.is(BooleanProjectConfig.USE_CONTENT_MERGE));
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
  }

  private static class ChangeDataCache {
    private final ChangeData cd;
    private final ProjectAccessor.Factory projectAccessorFactory;

    private ObjectId testAgainst;
    private ProjectAccessor projectAccessor;
    private Set<ObjectId> alreadyAccepted;

    ChangeDataCache(ChangeData cd, ProjectAccessor.Factory projectAccessorFactory) {
      this.cd = cd;
      this.projectAccessorFactory = projectAccessorFactory;
    }

    ObjectId getTestAgainst() throws OrmException {
      if (testAgainst == null) {
        testAgainst = ObjectId.fromString(cd.currentPatchSet().getRevision().get());
      }
      return testAgainst;
    }

    ProjectAccessor getProjectAccessor() throws NoSuchProjectException, IOException {
      if (projectAccessor == null) {
        projectAccessor = projectAccessorFactory.create(cd.project());
      }
      return projectAccessor;
    }

    Set<ObjectId> getAlreadyAccepted(Repository repo) throws IOException {
      if (alreadyAccepted == null) {
        alreadyAccepted = SubmitDryRun.getAlreadyAccepted(repo);
      }
      return alreadyAccepted;
    }
  }
}
