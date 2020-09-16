// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import org.eclipse.jgit.transport.ReceiveCommand;

public class SubmoduleOp {

  @Singleton
  public static class Factory {
    private final SubscriptionGraph.Factory subscriptionGraphFactory;
    private final SubmoduleCommits.Factory submoduleCommitsFactory;

    @Inject
    Factory(
        SubscriptionGraph.Factory subscriptionGraphFactory,
        SubmoduleCommits.Factory submoduleCommitsFactory) {
      this.subscriptionGraphFactory = subscriptionGraphFactory;
      this.submoduleCommitsFactory = submoduleCommitsFactory;
    }

    public SubmoduleOp create(
        Map<BranchNameKey, ReceiveCommand> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      return new SubmoduleOp(
          updatedBranches,
          orm,
          subscriptionGraphFactory.compute(updatedBranches.keySet(), orm),
          submoduleCommitsFactory.create(orm));
    }
  }

  private final Map<BranchNameKey, ReceiveCommand> updatedBranches;
  private final MergeOpRepoManager orm;
  private final SubscriptionGraph subscriptionGraph;
  private final SubmoduleCommits submoduleCommits;
  private final UpdateOrderCalculator updateOrderCalculator;

  private SubmoduleOp(
      Map<BranchNameKey, ReceiveCommand> updatedBranches,
      MergeOpRepoManager orm,
      SubscriptionGraph subscriptionGraph,
      SubmoduleCommits submoduleCommits) {
    this.updatedBranches = updatedBranches;
    this.orm = orm;
    this.subscriptionGraph = subscriptionGraph;
    this.submoduleCommits = submoduleCommits;
    this.updateOrderCalculator = new UpdateOrderCalculator(subscriptionGraph);
  }

  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public boolean hasSuperproject(BranchNameKey branch) {
    return subscriptionGraph.hasSuperproject(branch);
  }

  public void updateSuperProjects(boolean dryrun) throws RestApiException {
    ImmutableSet<Project.NameKey> projects = updateOrderCalculator.getProjectsInOrder();
    if (projects == null) {
      return;
    }

    if (dryrun) {
      // On dryrun, the refs hasn't been updated.
      // force the new tips on submoduleCommits
      forceRefTips(updatedBranches, submoduleCommits);
    }

    LinkedHashSet<Project.NameKey> superProjects = new LinkedHashSet<>();
    try {
      GitlinkOp.Factory gitlinkOpFactory =
          new GitlinkOp.Factory(submoduleCommits, subscriptionGraph);
      for (Project.NameKey project : projects) {
        // only need superprojects
        if (subscriptionGraph.isAffectedSuperProject(project)) {
          superProjects.add(project);
          // get a new BatchUpdate for the super project
          OpenRepo or = orm.getRepo(project);
          for (BranchNameKey branch : subscriptionGraph.getAffectedSuperBranches(project)) {
            or.getUpdate().addRepoOnlyOp(gitlinkOpFactory.create(branch));
          }
        }
      }
      BatchUpdate.execute(orm.batchUpdates(superProjects), BatchUpdateListener.NONE, dryrun);
    } catch (UpdateException | IOException | NoSuchProjectException e) {
      throw new StorageException("Cannot update gitlinks", e);
    }
  }

  private void forceRefTips(
      Map<BranchNameKey, ReceiveCommand> updatedBranches, SubmoduleCommits submoduleCommits) {
    for (Map.Entry<BranchNameKey, ReceiveCommand> updateBranch : updatedBranches.entrySet()) {
      try {
        ReceiveCommand command = updateBranch.getValue();
        // This is dryrun, all commands succeeded
        if (command.getType() == ReceiveCommand.Type.DELETE) {
          continue;
        }

        BranchNameKey branchNameKey = updateBranch.getKey();
        OpenRepo openRepo = orm.getRepo(branchNameKey.project());
        CodeReviewCommit fakeTip = openRepo.rw.parseCommit(command.getNewId());
        submoduleCommits.addBranchTip(branchNameKey, fakeTip);
      } catch (NoSuchProjectException | IOException e) {
        throw new StorageException("Cannot update gitlinks", e);
      }
    }
  }
}
