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

import com.google.common.base.Throwables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.api.changes.FixInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectInput.AutoClosableChangesCheckInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo.AutoClosableChangesCheckResult;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
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
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeJson.Factory changeJsonFactory;

  @Inject
  ProjectsConsistencyChecker(
      GitRepositoryManager repoManager,
      RetryHelper retryHelper,
      Provider<InternalChangeQuery> queryProvider,
      ChangeJson.Factory changeJsonFactory) {
    this.repoManager = repoManager;
    this.retryHelper = retryHelper;
    this.queryProvider = queryProvider;
    this.changeJsonFactory = changeJsonFactory;
  }

  public CheckProjectResultInfo check(Project.NameKey projectName, CheckProjectInput input)
      throws IOException, OrmException {
    CheckProjectResultInfo r = new CheckProjectResultInfo();
    if (input.autoClosableChangesCheck != null) {
      r.autoClosableChangesCheckResult =
          checkForAutoClosableChanges(projectName, input.autoClosableChangesCheck);
    }
    return r;
  }

  private AutoClosableChangesCheckResult checkForAutoClosableChanges(
      Project.NameKey projectName, AutoClosableChangesCheckInput input)
      throws IOException, OrmException {
    AutoClosableChangesCheckResult r = new AutoClosableChangesCheckResult();
    if (input.branches == null || input.branches.isEmpty()) {
      return r;
    }

    ListMultimap<String, ChangeInfo> autoClosableChangesByBranch =
        MultimapBuilder.hashKeys().arrayListValues().build();
    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      for (String branch : input.branches) {
        if (!branch.startsWith(Constants.R_REFS)) {
          branch = Constants.R_HEADS + branch;
        }

        Ref ref = repo.exactRef(branch);
        if (ref == null) {
          continue;
        }

        rw.reset();
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        rw.sort(RevSort.TOPO);
        rw.sort(RevSort.REVERSE);

        RevCommit c;
        while ((c = rw.next()) != null) {
          Optional<ChangeData> autoCloseableChange =
              byBranchCommitOpen(projectName, branch, c.name());
          if (autoCloseableChange.isPresent()) {
            autoClosableChangesByBranch.put(
                branch, changeJson(input.fix, c).format(autoCloseableChange.get()));
            continue;
          }

          for (String changeId : c.getFooterLines(CHANGE_ID)) {
            autoCloseableChange =
                byBranchChangeIdOpen(projectName, branch, new Change.Key(changeId));
            if (autoCloseableChange.isPresent()) {
              autoClosableChangesByBranch.put(
                  branch, changeJson(input.fix, c).format(autoCloseableChange.get()));
              continue;
            }
          }
        }
      }
    }

    r.autoClosableChanges = autoClosableChangesByBranch.asMap();
    return r;
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

  private Optional<ChangeData> byBranchCommitOpen(
      Project.NameKey project, String branch, String commit) throws OrmException {
    return queryChange(q -> q.byBranchCommitOpen(project.get(), branch, commit));
  }

  private Optional<ChangeData> byBranchChangeIdOpen(
      Project.NameKey project, String branch, Change.Key changeKey) throws OrmException {
    return queryChange(q -> q.byBranchKeyOpen(project, branch, changeKey));
  }

  private Optional<ChangeData> queryChange(ChangeQuery query) throws OrmException {
    try {
      return retryHelper.execute(
          ActionType.INDEX_QUERY,
          () -> {
            List<ChangeData> res = query.run(queryProvider.get());
            if (res.isEmpty()) {
              return Optional.empty();
            }
            return Optional.of(res.get(0));
          },
          OrmException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmException.class);
      throw new OrmException(e);
    }
  }

  @FunctionalInterface
  private static interface ChangeQuery {
    List<ChangeData> run(InternalChangeQuery internalChangeQuery) throws OrmException;
  }
}
