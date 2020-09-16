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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

public class SubmoduleOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class Factory {
    private final SubscriptionGraph.Factory subscriptionGraphFactory;
    private final Config cfg;
    private final SubmoduleCommits.Factory submoduleCommitsFactory;

    @Inject
    Factory(
        SubscriptionGraph.Factory subscriptionGraphFactory,
        SubmoduleCommits.Factory submoduleCommitsFactory,
        @GerritServerConfig Config cfg) {
      this.subscriptionGraphFactory = subscriptionGraphFactory;
      this.submoduleCommitsFactory = submoduleCommitsFactory;
      this.cfg = cfg;
    }

    public SubmoduleOp create(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      SubscriptionGraph subscriptionGraph;
      if (cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true)) {
        subscriptionGraph = subscriptionGraphFactory.compute(updatedBranches, orm);
      } else {
        logger.atFine().log("Updating superprojects disabled");
        subscriptionGraph =
            SubscriptionGraph.createEmptyGraph(ImmutableSet.copyOf(updatedBranches));
      }
      return new SubmoduleOp(orm, subscriptionGraph, submoduleCommitsFactory.create(orm));
    }
  }

  private final MergeOpRepoManager orm;
  private final SubscriptionGraph subscriptionGraph;
  private final SubmoduleCommits submoduleCommits;

  private SubmoduleOp(
      MergeOpRepoManager orm,
      SubscriptionGraph subscriptionGraph,
      SubmoduleCommits submoduleCommits) {
    this.orm = orm;
    this.subscriptionGraph = subscriptionGraph;
    this.submoduleCommits = submoduleCommits;
  }

  // TODO(ifrade): subscription graph should be instantiated somewhere else and passed to
  // SubmoduleOp
  SubscriptionGraph getSubscriptionGraph() {
    return subscriptionGraph;
  }

  SubmoduleCommits getSubmoduleCommits() {
    return submoduleCommits;
  }

  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public boolean hasSuperproject(BranchNameKey branch) {
    return subscriptionGraph.hasSuperproject(branch);
  }

  public void updateSuperProjects() throws RestApiException {
    ImmutableSet<Project.NameKey> projects = getProjectsInOrder();
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

  ImmutableSet<Project.NameKey> getProjectsInOrder() throws SubmoduleConflictException {
    LinkedHashSet<Project.NameKey> projects = new LinkedHashSet<>();
    for (Project.NameKey project : subscriptionGraph.getAffectedSuperProjects()) {
      addAllSubmoduleProjects(project, new LinkedHashSet<>(), projects);
    }

    for (BranchNameKey branch : subscriptionGraph.getUpdatedBranches()) {
      projects.add(branch.project());
    }
    return ImmutableSet.copyOf(projects);
  }

  private void addAllSubmoduleProjects(
      Project.NameKey project,
      LinkedHashSet<Project.NameKey> current,
      LinkedHashSet<Project.NameKey> projects)
      throws SubmoduleConflictException {
    if (current.contains(project)) {
      throw new SubmoduleConflictException(
          "Project level circular subscriptions detected:  "
              + CircularPathFinder.printCircularPath(current, project));
    }

    if (projects.contains(project)) {
      return;
    }

    current.add(project);
    Set<Project.NameKey> subprojects = new HashSet<>();
    for (BranchNameKey branch : subscriptionGraph.getAffectedSuperBranches(project)) {
      Collection<SubmoduleSubscription> subscriptions = subscriptionGraph.getSubscriptions(branch);
      for (SubmoduleSubscription s : subscriptions) {
        subprojects.add(s.getSubmodule().project());
      }
    }

    for (Project.NameKey p : subprojects) {
      addAllSubmoduleProjects(p, current, projects);
    }

    current.remove(project);
    projects.add(project);
  }

  ImmutableSet<BranchNameKey> getBranchesInOrder() {
    LinkedHashSet<BranchNameKey> branches = new LinkedHashSet<>();
    branches.addAll(subscriptionGraph.getSortedSuperprojectAndSubmoduleBranches());
    branches.addAll(subscriptionGraph.getUpdatedBranches());
    return ImmutableSet.copyOf(branches);
  }
}
