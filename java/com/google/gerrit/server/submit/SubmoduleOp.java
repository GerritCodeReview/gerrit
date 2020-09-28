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
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public SubmoduleOp create(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      return new SubmoduleOp(
          orm,
          subscriptionGraphFactory.compute(updatedBranches, orm),
          submoduleCommitsFactory.create(orm));
    }
  }

  private final MergeOpRepoManager orm;
  private final SubscriptionGraph subscriptionGraph;
  private final SubmoduleCommits submoduleCommits;
  private final UpdateOrderCalculator updateOrderCalculator;

  private SubmoduleOp(
      MergeOpRepoManager orm,
      SubscriptionGraph subscriptionGraph,
      SubmoduleCommits submoduleCommits) {
    this.orm = orm;
    this.subscriptionGraph = subscriptionGraph;
    this.submoduleCommits = submoduleCommits;
    this.updateOrderCalculator = new UpdateOrderCalculator(subscriptionGraph);
  }

  public void updateSuperProjects() throws RestApiException {
    ImmutableSet<Project.NameKey> projects = updateOrderCalculator.getProjectsInOrder();
    if (projects == null) {
      return;
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
      BatchUpdate.execute(orm.batchUpdates(superProjects), BatchUpdateListener.NONE, false);
    } catch (UpdateException | IOException | NoSuchProjectException e) {
      throw new StorageException("Cannot update gitlinks", e);
    }
  }
}
