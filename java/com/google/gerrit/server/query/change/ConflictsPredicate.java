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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.flogger.LazyArgs.lazy;
import static com.google.gerrit.server.project.ProjectCache.noSuchProject;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gerrit.server.submit.SubmitDryRun;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // UI code may depend on this string, so use caution when changing.
  protected static final String TOO_MANY_FILES = "too many files to find conflicts";

  private ConflictsPredicate() {}

  public static Predicate<ChangeData> create(Arguments args, String value, Change c)
      throws QueryParseException {
    ChangeData cd;
    List<String> files;
    try {
      cd = args.changeDataFactory.create(c);
      files = cd.currentFilePaths();
    } catch (StorageException e) {
      warnWithOccasionalStackTrace(
          e,
          "Error constructing conflicts predicates for change %s in %s",
          c.getId(),
          c.getProject());
      return ChangeIndexPredicate.none();
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
    and.add(new RefPredicate(c.getDest().branch()));
    and.add(
        Predicate.not(
            args.getSchema().useLegacyNumericFields()
                ? new LegacyChangeIdPredicate(c.getId())
                : new LegacyChangeIdStrPredicate(c.getId())));
    and.add(Predicate.or(filePredicates));

    ChangeDataCache changeDataCache = new ChangeDataCache(cd, args.projectCache);
    and.add(new CheckConflict(value, args, c, changeDataCache));
    return Predicate.and(and);
  }

  private static final class CheckConflict extends PostFilterPredicate<ChangeData> {
    private final Arguments args;
    private final BranchNameKey dest;
    private final ChangeDataCache changeDataCache;

    CheckConflict(String value, Arguments args, Change c, ChangeDataCache changeDataCache) {
      super(ChangeQueryBuilder.FIELD_CONFLICTS, value);
      this.args = args;
      this.dest = c.getDest();
      this.changeDataCache = changeDataCache;
    }

    @Override
    public boolean match(ChangeData object) {
      Change.Id id = object.getId();
      Project.NameKey otherProject = null;
      ObjectId other = null;
      try {
        Change otherChange = object.change();
        if (otherChange == null || !otherChange.getDest().equals(dest)) {
          return false;
        }
        otherProject = otherChange.getProject();

        SubmitTypeRecord str = object.submitTypeRecord();
        if (!str.isOk()) {
          return false;
        }

        ProjectState projectState;
        try {
          projectState = changeDataCache.getProjectState();
        } catch (NoSuchProjectException e) {
          return false;
        }

        other = object.currentPatchSet().commitId();
        ConflictKey conflictsKey =
            ConflictKey.create(
                changeDataCache.getTestAgainst(),
                other,
                str.type,
                projectState.is(BooleanProjectConfig.USE_CONTENT_MERGE));
        Boolean maybeConflicts = args.conflictsCache.getIfPresent(conflictsKey);
        if (maybeConflicts != null) {
          return maybeConflicts;
        }

        try (Repository repo = args.repoManager.openRepository(otherChange.getProject());
            CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
          boolean conflicts =
              !args.submitDryRun.run(
                  null,
                  str.type,
                  repo,
                  rw,
                  otherChange.getDest(),
                  changeDataCache.getTestAgainst(),
                  other,
                  getAlreadyAccepted(repo, rw));
          args.conflictsCache.put(conflictsKey, conflicts);
          return conflicts;
        }
      } catch (NoSuchProjectException | StorageException | IOException e) {
        ObjectId finalOther = other;
        warnWithOccasionalStackTrace(
            e,
            "Merge failure checking conflicts of change %s in %s (%s): %s",
            id,
            firstNonNull(otherProject, "unknown project"),
            lazy(() -> finalOther != null ? finalOther.name() : "unknown commit"),
            e.getMessage());
        return false;
      }
    }

    @Override
    public int getCost() {
      return 5;
    }

    private Set<RevCommit> getAlreadyAccepted(Repository repo, RevWalk rw) {
      try {
        Set<RevCommit> accepted = new HashSet<>();
        SubmitDryRun.addCommits(changeDataCache.getAlreadyAccepted(repo), rw, accepted);
        ObjectId tip = changeDataCache.getTestAgainst();
        if (tip != null) {
          accepted.add(rw.parseCommit(tip));
        }
        return accepted;
      } catch (StorageException | IOException e) {
        throw new StorageException("Failed to determine already accepted commits.", e);
      }
    }
  }

  private static class ChangeDataCache {
    private final ChangeData cd;
    private final ProjectCache projectCache;

    private ObjectId testAgainst;
    private ProjectState projectState;
    private Set<ObjectId> alreadyAccepted;

    ChangeDataCache(ChangeData cd, ProjectCache projectCache) {
      this.cd = cd;
      this.projectCache = projectCache;
    }

    ObjectId getTestAgainst() {
      if (testAgainst == null) {
        testAgainst = cd.currentPatchSet().commitId();
      }
      return testAgainst;
    }

    ProjectState getProjectState() throws NoSuchProjectException {
      if (projectState == null) {
        projectState = projectCache.get(cd.project()).orElseThrow(noSuchProject(cd.project()));
      }
      return projectState;
    }

    Set<ObjectId> getAlreadyAccepted(Repository repo) throws IOException {
      if (alreadyAccepted == null) {
        alreadyAccepted = SubmitDryRun.getAlreadyAccepted(repo);
      }
      return alreadyAccepted;
    }
  }

  private static void warnWithOccasionalStackTrace(Throwable cause, String format, Object... args) {
    logger.atWarning().logVarargs(format, args);
    logger
        .atWarning()
        .withCause(cause)
        .atMostEvery(1, MINUTES)
        .logVarargs("(Re-logging with stack trace) " + format, args);
  }
}
