// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.entities.SubmoduleSubscription;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sorts the projects or branches affected by the update.
 *
 * <p>The subscription graph contains all branches (and projects) affected by the update, but the
 * updates must be executed in the right order, so no superproject reference is updated before its
 * target.
 */
class UpdateOrderCalculator {

  private final SubscriptionGraph subscriptionGraph;

  UpdateOrderCalculator(SubscriptionGraph subscriptionGraph) {
    this.subscriptionGraph = subscriptionGraph;
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
