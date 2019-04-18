// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.index.query.Predicate.and;
import static com.google.gerrit.index.query.Predicate.or;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput.AutoCloseableChangesCheckInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo.AutoCloseableChangesCheckResult;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeIdPredicate;
import com.google.gerrit.server.query.change.CommitPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.query.change.ProjectPredicate;
import com.google.gerrit.server.query.change.RefPredicate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ProjectsConsistencyChecker {
  @VisibleForTesting public static final int AUTO_CLOSE_MAX_COMMITS_LIMIT = 10000;

  private final GitRepositoryManager repoManager;
  private final RetryHelper retryHelper;
  private final Provider<InternalChangeQuery> changeQueryProvider;
  private final ChangeJson.Factory changeJsonFactory;
  private final IndexConfig indexConfig;

  @Inject
  ProjectsConsistencyChecker(
      GitRepositoryManager repoManager,
      RetryHelper retryHelper,
      Provider<InternalChangeQuery> changeQueryProvider,
      ChangeJson.Factory changeJsonFactory,
      IndexConfig indexConfig) {
    this.repoManager = repoManager;
    this.retryHelper = retryHelper;
    this.changeQueryProvider = changeQueryProvider;
    this.changeJsonFactory = changeJsonFactory;
    this.indexConfig = indexConfig;
  }

  public CheckProjectResultInfo check(Project.NameKey projectName, CheckProjectInput input)
      throws IOException, RestApiException {
    CheckProjectResultInfo r = new CheckProjectResultInfo();
    if (input.autoCloseableChangesCheck != null) {
      r.autoCloseableChangesCheckResult =
          checkForAutoCloseableChanges(projectName, input.autoCloseableChangesCheck);
    }
    return r;
  }

  private AutoCloseableChangesCheckResult checkForAutoCloseableChanges(
      Project.NameKey projectName, AutoCloseableChangesCheckInput input)
      throws IOException, RestApiException {
    AutoCloseableChangesCheckResult r = new AutoCloseableChangesCheckResult();
    if (Strings.isNullOrEmpty(input.branch)) {
      throw new BadRequestException("branch is required");
    }

    boolean fix = input.fix != null ? input.fix : false;

    if (input.maxCommits != null && input.maxCommits > AUTO_CLOSE_MAX_COMMITS_LIMIT) {
      throw new BadRequestException(
          "max commits can at most be set to " + AUTO_CLOSE_MAX_COMMITS_LIMIT);
    }
    int maxCommits = input.maxCommits != null ? input.maxCommits : AUTO_CLOSE_MAX_COMMITS_LIMIT;

    // Result that we want to return to the client.
    List<ChangeInfo> autoCloseableChanges = new ArrayList<>();

    // Remember the change IDs of all changes that we already included into the result, so that we
    // can avoid including the same change twice.
    Set<Change.Id> seenChanges = new HashSet<>();

    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      String branch = RefNames.fullName(input.branch);
      Ref ref = repo.exactRef(branch);
      if (ref == null) {
        throw new UnprocessableEntityException(
            String.format("branch '%s' not found", input.branch));
      }

      rw.reset();
      rw.markStart(rw.parseCommit(ref.getObjectId()));
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE);

      // Cache the SHA1's of all merged commits. We need this for knowing which commit merged the
      // change when auto-closing changes by commit.
      List<ObjectId> mergedSha1s = new ArrayList<>();

      // Cache the Change-Id to commit SHA1 mapping for all Change-Id's that we find in merged
      // commits. We need this for knowing which commit merged the change when auto-closing
      // changes by Change-Id.
      Map<Change.Key, ObjectId> changeIdToMergedSha1 = new HashMap<>();

      // Base predicate which is fixed for every change query.
      Predicate<ChangeData> basePredicate =
          and(new ProjectPredicate(projectName.get()), new RefPredicate(branch), open());

      int maxLeafPredicates = indexConfig.maxTerms() - basePredicate.getLeafCount();

      // List of predicates by which we want to find open changes for the branch. These predicates
      // will be combined with the 'or' operator.
      List<Predicate<ChangeData>> predicates = new ArrayList<>(maxLeafPredicates);

      RevCommit commit;
      int skippedCommits = 0;
      int walkedCommits = 0;
      while ((commit = rw.next()) != null) {
        if (input.skipCommits != null && skippedCommits < input.skipCommits) {
          skippedCommits++;
          continue;
        }

        if (walkedCommits >= maxCommits) {
          break;
        }
        walkedCommits++;

        ObjectId commitId = commit.copy();
        mergedSha1s.add(commitId);

        // Consider all Change-Id lines since this is what ReceiveCommits#autoCloseChanges does.
        List<String> changeIds = commit.getFooterLines(CHANGE_ID);

        // Number of predicates that we need to add for this commit, 1 per Change-Id plus one for
        // the commit.
        int newPredicatesCount = changeIds.size() + 1;

        // We accumulated the max number of query terms that can be used in one query, execute
        // the query and start a new one.
        if (predicates.size() + newPredicatesCount > maxLeafPredicates) {
          autoCloseableChanges.addAll(
              executeQueryAndAutoCloseChanges(
                  basePredicate, seenChanges, predicates, fix, changeIdToMergedSha1, mergedSha1s));
          mergedSha1s.clear();
          changeIdToMergedSha1.clear();
          predicates.clear();

          if (newPredicatesCount > maxLeafPredicates) {
            // Whee, a single commit generates more than maxLeafPredicates predicates. Give up.
            throw new ResourceConflictException(
                String.format(
                    "commit %s contains more Change-Ids than we can handle", commit.name()));
          }
        }

        changeIds.forEach(
            changeId -> {
              // It can happen that there are multiple merged commits with the same Change-Id
              // footer (e.g. if a change was cherry-picked to a stable branch stable branch which
              // then got merged back into master, or just by directly pushing several commits
              // with the same Change-Id). In this case it is hard to say which of the commits
              // should be used to auto-close an open change with the same Change-Id (and branch).
              // Possible approaches are:
              // 1. use the oldest commit with that Change-Id to auto-close the change
              // 2. use the newest commit with that Change-Id to auto-close the change
              // Possibility 1. has the disadvantage that the commit may have been merged before
              // the change was created in which case it is strange how it could auto-close the
              // change. Also this strategy would require to walk all commits since otherwise we
              // cannot be sure that we have seen the oldest commit with that Change-Id.
              // Possibility 2 has the disadvantage that it doesn't produce the same result as if
              // auto-closing on push would have worked, since on direct push the first commit with
              // a Change-Id of an open change would have closed that change. Also for this we
              // would need to consider all commits that are skipped.
              // Since both possibilities are not perfect and require extra effort we choose the
              // easiest approach, which is use the newest commit with that Change-Id that we have
              // seen (this means we ignore skipped commits). This should be okay since the
              // important thing for callers is that auto-closable changes are closed. Which of the
              // commits is used to auto-close a change if there are several candidates is of minor
              // importance and hence can be non-deterministic.
              Change.Key changeKey = new Change.Key(changeId);
              if (!changeIdToMergedSha1.containsKey(changeKey)) {
                changeIdToMergedSha1.put(changeKey, commitId);
              }

              // Find changes that have a matching Change-Id.
              predicates.add(new ChangeIdPredicate(changeId));
            });

        // Find changes that have a matching commit.
        predicates.add(new CommitPredicate(commit.name()));
      }

      if (predicates.size() > 0) {
        // Execute the query with the remaining predicates that were collected.
        autoCloseableChanges.addAll(
            executeQueryAndAutoCloseChanges(
                basePredicate, seenChanges, predicates, fix, changeIdToMergedSha1, mergedSha1s));
      }
    }

    r.autoCloseableChanges = autoCloseableChanges;
    return r;
  }

  private List<ChangeInfo> executeQueryAndAutoCloseChanges(
      Predicate<ChangeData> basePredicate,
      Set<Change.Id> seenChanges,
      List<Predicate<ChangeData>> predicates,
      boolean fix,
      Map<Change.Key, ObjectId> changeIdToMergedSha1,
      List<ObjectId> mergedSha1s) {
    if (predicates.isEmpty()) {
      return ImmutableList.of();
    }

    try {
      List<ChangeData> queryResult =
          retryHelper.execute(
              ActionType.INDEX_QUERY,
              () -> {
                // Execute the query.
                return changeQueryProvider
                    .get()
                    .setRequestedFields(ChangeField.CHANGE, ChangeField.PATCH_SET)
                    .query(and(basePredicate, or(predicates)));
              },
              StorageException.class::isInstance);

      // Result for this query that we want to return to the client.
      List<ChangeInfo> autoCloseableChangesByBranch = new ArrayList<>();

      for (ChangeData autoCloseableChange : queryResult) {
        // Skip changes that we have already processed, either by this query or by
        // earlier queries.
        if (seenChanges.add(autoCloseableChange.getId())) {
          retryHelper.execute(
              ActionType.CHANGE_UPDATE,
              () -> {
                // Auto-close by change
                if (changeIdToMergedSha1.containsKey(autoCloseableChange.change().getKey())) {
                  autoCloseableChangesByBranch.add(
                      changeJson(
                              fix, changeIdToMergedSha1.get(autoCloseableChange.change().getKey()))
                          .format(autoCloseableChange));
                  return null;
                }

                // Auto-close by commit
                for (ObjectId patchSetSha1 :
                    autoCloseableChange.patchSets().stream()
                        .map(ps -> ObjectId.fromString(ps.getRevision().get()))
                        .collect(toSet())) {
                  if (mergedSha1s.contains(patchSetSha1)) {
                    autoCloseableChangesByBranch.add(
                        changeJson(fix, patchSetSha1).format(autoCloseableChange));
                    break;
                  }
                }
                return null;
              },
              StorageException.class::isInstance);
        }
      }

      return autoCloseableChangesByBranch;
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, StorageException.class);
      throw new StorageException(e);
    }
  }

  private ChangeJson changeJson(Boolean fix, ObjectId mergedAs) {
    ChangeJson changeJson = changeJsonFactory.create(ListChangesOption.CHECK);
    if (fix != null && fix.booleanValue()) {
      FixInput fixInput = new FixInput();
      fixInput.expectMergedAs = mergedAs.name();
      changeJson.fix(fixInput);
    }
    return changeJson;
  }
}
