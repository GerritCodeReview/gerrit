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

import com.google.common.base.Throwables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput.AutoCloseableChangesCheckInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo.AutoCloseableChangesCheckResult;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
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
import com.google.gwtorm.server.OrmException;
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
      throws IOException, OrmException {
    CheckProjectResultInfo r = new CheckProjectResultInfo();
    if (input.autoCloseableChangesCheck != null) {
      r.autoCloseableChangesCheckResult =
          checkForAutoCloseableChanges(projectName, input.autoCloseableChangesCheck);
    }
    return r;
  }

  private AutoCloseableChangesCheckResult checkForAutoCloseableChanges(
      Project.NameKey projectName, AutoCloseableChangesCheckInput input)
      throws IOException, OrmException {
    AutoCloseableChangesCheckResult r = new AutoCloseableChangesCheckResult();
    if (input.branches == null || input.branches.isEmpty()) {
      return r;
    }

    boolean fix = input.fix != null ? input.fix : false;

    // Result that we want to return to the client.
    Multimap<String, ChangeInfo> autoCloseableChangesByBranch =
        MultimapBuilder.hashKeys().arrayListValues().build();

    // Remember the change IDs of all changes that we already included into the result, so that we
    // can avoid including the same change twice.
    Set<Change.Id> seenChanges = new HashSet<>();

    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      for (String branch : input.branches) {
        branch = RefNames.fullName(branch);
        Ref ref = repo.exactRef(branch);
        if (ref == null) {
          continue;
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

        // List of predicates by which we want to find open changes for the branch. These predicates
        // will be combined with the 'or' operator.
        List<Predicate<ChangeData>> predicates = new ArrayList<>();

        RevCommit commit;
        int i = 1;
        while ((commit = rw.next()) != null
            && (input.maxCommits == null || i <= input.maxCommits)) {
          i++;

          ObjectId commitId = commit.copy();
          mergedSha1s.add(commitId);

          List<String> changeIds = commit.getFooterLines(CHANGE_ID);
          changeIds.forEach(
              changeId -> {
                changeIdToMergedSha1.put(new Change.Key(changeId), commitId);

                // Find changes that have a matching Change-Id.
                predicates.add(new ChangeIdPredicate(changeId));
              });

          // Find changes that have a matching commit.
          predicates.add(new CommitPredicate(commit.name()));

          // We accumulated the max number of query terms that can be used in one query, execute
          // the query and start a new one.
          // + 2 to account for constant query terms (one for the change status, one for the
          // branch).
          if (predicates.size() + 2 >= indexConfig.maxTerms()) {
            autoCloseableChangesByBranch.putAll(
                executeQueryAndAutoCloseChanges(
                    projectName,
                    branch,
                    seenChanges,
                    predicates,
                    fix,
                    changeIdToMergedSha1,
                    mergedSha1s));
            mergedSha1s.clear();
            changeIdToMergedSha1.clear();
            predicates.clear();
          }
        }

        if (predicates.size() > 0) {
          // Execute the query with the remaining predicates that were collected.
          autoCloseableChangesByBranch.putAll(
              executeQueryAndAutoCloseChanges(
                  projectName,
                  branch,
                  seenChanges,
                  predicates,
                  fix,
                  changeIdToMergedSha1,
                  mergedSha1s));
        }
      }
    }

    r.autoCloseableChanges = autoCloseableChangesByBranch.asMap();
    return r;
  }

  private Multimap<String, ChangeInfo> executeQueryAndAutoCloseChanges(
      Project.NameKey project,
      String branch,
      Set<Change.Id> seenChanges,
      List<Predicate<ChangeData>> predicates,
      boolean fix,
      Map<Change.Key, ObjectId> changeIdToMergedSha1,
      List<ObjectId> mergedSha1s)
      throws OrmException {
    if (predicates.isEmpty()) {
      return MultimapBuilder.hashKeys().arrayListValues().build();
    }

    // Remember the change IDs of all changes that we are included into the result by executing this
    // query, so that we can avoid including the same change twice.
    Set<Change.Id> newlySeenChanges = new HashSet<>();
    try {
      Multimap<String, ChangeInfo> r =
          retryHelper.execute(
              ActionType.INDEX_QUERY,
              () -> {
                // If we retry we must discard all results that we have collected so far.
                newlySeenChanges.clear();

                // Result for this query that we want to return to the client.
                ListMultimap<String, ChangeInfo> autoCloseableChangesByBranch =
                    MultimapBuilder.hashKeys().arrayListValues().build();

                // Execute the query.
                List<ChangeData> result =
                    changeQueryProvider
                        .get()
                        .setRequestedFields(ChangeField.CHANGE, ChangeField.PATCH_SET)
                        .query(
                            and(
                                new ProjectPredicate(project.get()),
                                new RefPredicate(branch),
                                open(),
                                or(predicates)));

                for (ChangeData autoCloseableChange : result) {
                  // Skip changes that we have already processed, either by this query or by
                  // earlier queries.
                  if (!seenChanges.contains(autoCloseableChange.getId())
                      && newlySeenChanges.add(autoCloseableChange.getId())) {
                    // Auto-close by change
                    if (changeIdToMergedSha1.containsKey(autoCloseableChange.change().getKey())) {
                      autoCloseableChangesByBranch.put(
                          branch,
                          changeJson(
                                  fix,
                                  changeIdToMergedSha1.get(autoCloseableChange.change().getKey()))
                              .format(autoCloseableChange));
                      continue;
                    }

                    // Auto-close by commit
                    for (ObjectId patchSetSha1 :
                        autoCloseableChange
                            .patchSets()
                            .stream()
                            .map(ps -> ObjectId.fromString(ps.getRevision().get()))
                            .collect(toSet())) {
                      if (mergedSha1s.contains(patchSetSha1)) {
                        autoCloseableChangesByBranch.put(
                            branch, changeJson(fix, patchSetSha1).format(autoCloseableChange));
                        break;
                      }
                    }
                  }
                }
                return autoCloseableChangesByBranch;
              },
              OrmException.class::isInstance);

      // The query was successfully executed. We can now mark all changes as seen that were
      // processed by this query.
      seenChanges.addAll(newlySeenChanges);

      return r;
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmException.class);
      throw new OrmException(e);
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
