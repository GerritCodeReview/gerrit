// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.RequestId;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevSort;

/**
 * This is a helper class for MergeOp and not intended for general use.
 *
 * <p>Some database backends require to open a repository just once within a transaction of a
 * submission, this caches open repositories to satisfy that requirement.
 */
public class MergeOpRepoManager implements AutoCloseable {
  public class OpenRepo {
    final Repository repo;
    final CodeReviewRevWalk rw;
    final RevFlag canMergeFlag;
    final ObjectInserter ins;

    final ProjectState project;
    BatchUpdate update;

    private final ObjectReader reader;
    private final Map<Branch.NameKey, OpenBranch> branches;

    private OpenRepo(Repository repo, ProjectState project) {
      this.repo = repo;
      this.project = project;
      ins = repo.newObjectInserter();
      reader = ins.newReader();
      rw = CodeReviewCommit.newRevWalk(reader);
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.COMMIT_TIME_DESC, true);
      rw.setRetainBody(false);
      canMergeFlag = rw.newFlag("CAN_MERGE");
      rw.retainOnReset(canMergeFlag);

      branches = Maps.newHashMapWithExpectedSize(1);
    }

    OpenBranch getBranch(Branch.NameKey branch) throws IntegrationException {
      OpenBranch ob = branches.get(branch);
      if (ob == null) {
        ob = new OpenBranch(this, branch);
        branches.put(branch, ob);
      }
      return ob;
    }

    public Repository getRepo() {
      return repo;
    }

    Project.NameKey getProjectName() {
      return project.getProject().getNameKey();
    }

    public CodeReviewRevWalk getCodeReviewRevWalk() {
      return rw;
    }

    public BatchUpdate getUpdate() {
      checkState(db != null, "call setContext before getUpdate");
      if (update == null) {
        update =
            batchUpdateFactory
                .create(db, getProjectName(), caller, ts)
                .setRepository(repo, rw, ins)
                .setRequestId(submissionId)
                .setOnSubmitValidators(onSubmitValidatorsFactory.create());
      }
      return update;
    }

    private void close() {
      if (update != null) {
        update.close();
      }
      rw.close();
      reader.close();
      ins.close();
      repo.close();
    }
  }

  public static class OpenBranch {
    final RefUpdate update;
    final CodeReviewCommit oldTip;
    MergeTip mergeTip;

    OpenBranch(OpenRepo or, Branch.NameKey name) throws IntegrationException {
      try {
        update = or.repo.updateRef(name.get());
        if (update.getOldObjectId() != null) {
          oldTip = or.rw.parseCommit(update.getOldObjectId());
        } else if (Objects.equals(or.repo.getFullBranch(), name.get())) {
          oldTip = null;
          update.setExpectedOldObjectId(ObjectId.zeroId());
        } else {
          throw new IntegrationException(
              "The destination branch " + name + " does not exist anymore.");
        }
      } catch (IOException e) {
        throw new IntegrationException("Cannot open branch " + name, e);
      }
    }
  }

  private final Map<Project.NameKey, OpenRepo> openRepos;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final OnSubmitValidators.Factory onSubmitValidatorsFactory;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;

  private ReviewDb db;
  private Timestamp ts;
  private IdentifiedUser caller;
  private RequestId submissionId;

  @Inject
  MergeOpRepoManager(
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      BatchUpdate.Factory batchUpdateFactory,
      OnSubmitValidators.Factory onSubmitValidatorsFactory) {
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.batchUpdateFactory = batchUpdateFactory;
    this.onSubmitValidatorsFactory = onSubmitValidatorsFactory;

    openRepos = new HashMap<>();
  }

  void setContext(ReviewDb db, Timestamp ts, IdentifiedUser caller, RequestId submissionId) {
    this.db = db;
    this.ts = ts;
    this.caller = caller;
    this.submissionId = submissionId;
  }

  public RequestId getSubmissionId() {
    return submissionId;
  }

  public OpenRepo getRepo(Project.NameKey project) throws NoSuchProjectException, IOException {
    if (openRepos.containsKey(project)) {
      return openRepos.get(project);
    }

    ProjectState projectState = projectCache.get(project);
    if (projectState == null) {
      throw new NoSuchProjectException(project);
    }
    try {
      OpenRepo or = new OpenRepo(repoManager.openRepository(project), projectState);
      openRepos.put(project, or);
      return or;
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchProjectException(project, e);
    }
  }

  public List<BatchUpdate> batchUpdates(Collection<Project.NameKey> projects)
      throws NoSuchProjectException, IOException {
    List<BatchUpdate> updates = new ArrayList<>(projects.size());
    for (Project.NameKey project : projects) {
      updates.add(getRepo(project).getUpdate().setRefLogMessage("merged"));
    }
    return updates;
  }

  @Override
  public void close() {
    for (OpenRepo repo : openRepos.values()) {
      repo.close();
    }
    openRepos.clear();
  }
}
