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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.index.query.Predicate.and;
import static com.google.gerrit.index.query.Predicate.or;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput.AutoCloseableChangesCheckInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo.AutoCloseableChangesCheckResult;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeIdPredicate;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.CommitPredicate;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ProjectsConsistencyChecker {
  private static final int MAX_QUERY_BATCH = 100;

  private final GitRepositoryManager repoManager;
  private final RetryHelper retryHelper;
  private final Provider<ChangeQueryProcessor> queryProcessor;
  private final ChangeJson.Factory changeJsonFactory;

  @Inject
  ProjectsConsistencyChecker(
      GitRepositoryManager repoManager,
      RetryHelper retryHelper,
      Provider<ChangeQueryProcessor> queryProcessor,
      ChangeJson.Factory changeJsonFactory) {
    this.repoManager = repoManager;
    this.retryHelper = retryHelper;
    this.queryProcessor = queryProcessor;
    this.changeJsonFactory = changeJsonFactory;
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

        List<Queries> queryBatch = new ArrayList<>();
        RevCommit commit;
        while ((commit = rw.next()) != null) {
          // Construct a query for all open changes of the project and branch that have a matching
          // commit or Change-Id.
          queryBatch.add(
              Queries.create(
                  branch,
                  commit,
                  and(
                      new ProjectPredicate(projectName.get()),
                      new RefPredicate(branch),
                      open(),
                      or(
                          Streams.concat(
                                  Stream.of(new CommitPredicate(commit.name())),
                                  commit
                                      .getFooterLines(CHANGE_ID)
                                      .stream()
                                      .map(ChangeIdPredicate::new))
                              .collect(toList())))));

          // We accumulated the max number of change queries than fit into one batch, execute all
          // queries and start a new batch.
          if (queryBatch.size() >= MAX_QUERY_BATCH) {
            autoCloseableChangesByBranch.putAll(
                executeBatchOfQueries(seenChanges, queryBatch, input.fix));
            queryBatch.clear();
          }
        }

        // Execute all change queries that were collected for the last batch.
        autoCloseableChangesByBranch.putAll(
            executeBatchOfQueries(seenChanges, queryBatch, input.fix != null ? input.fix : false));
      }
    }

    r.autoCloseableChanges = autoCloseableChangesByBranch.asMap();
    return r;
  }

  private Multimap<String, ChangeInfo> executeBatchOfQueries(
      Set<Change.Id> seenChanges, List<Queries> batchOfQueries, boolean fix) throws OrmException {
    if (batchOfQueries.isEmpty()) {
      return MultimapBuilder.hashKeys().arrayListValues().build();
    }

    // Remember the change IDs of all changes that we are included into the result by executing this
    // batch, so that we
    // can avoid including the same change twice.
    Set<Change.Id> newlySeenChanges = new HashSet<>();
    try {
      Multimap<String, ChangeInfo> r =
          retryHelper.execute(
              ActionType.INDEX_QUERY,
              () -> {
                // If we retry we must discard all results that we have collected so far.
                newlySeenChanges.clear();

                // Result for this batch that we want to return to the client.
                ListMultimap<String, ChangeInfo> autoCloseableChangesByBranch =
                    MultimapBuilder.hashKeys().arrayListValues().build();

                // Execute the batch of queries.
                List<QueryResult<ChangeData>> results =
                    queryProcessor
                        .get()
                        .query(batchOfQueries.stream().map(Queries::query).collect(toList()));
                checkState(
                    results.size() == batchOfQueries.size(),
                    "unexpected number of query results: executed %s queries, got %s results",
                    batchOfQueries.size(),
                    results.size());

                Iterator<QueryResult<ChangeData>> resultIt = results.iterator();
                for (Queries queryForAutoClosableChange : batchOfQueries) {
                  for (ChangeData autoCloseableChange : resultIt.next().entities()) {
                    // Skip changes that we have already processed, either by this batch or by
                    // earlier batches.
                    if (!seenChanges.contains(autoCloseableChange.getId())
                        && newlySeenChanges.add(autoCloseableChange.getId())) {
                      autoCloseableChangesByBranch.put(
                          queryForAutoClosableChange.branch(),
                          changeJson(fix, queryForAutoClosableChange.mergedAs())
                              .format(autoCloseableChange));
                    }
                  }
                }
                return autoCloseableChangesByBranch;
              },
              OrmException.class::isInstance);

      // The query batch was successfully executed. We can now mark all changes as seen that were
      // processed by this batch.
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

  @AutoValue
  abstract static class Queries {
    abstract String branch();

    abstract ObjectId mergedAs();

    abstract Predicate<ChangeData> query();

    static Queries create(String branch, ObjectId mergedAs, Predicate<ChangeData> query) {
      return new AutoValue_ProjectsConsistencyChecker_Queries(branch, mergedAs, query);
    }
  }
}
