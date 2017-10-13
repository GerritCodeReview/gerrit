// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;

public class ProjectData {
  public interface AssistedFactory {
    ProjectData create(Project project, Iterable<Project.NameKey> ancestors);
  }

  private final GitRepositoryManager repoManager;
  private final Project project;
  private final ImmutableList<Project.NameKey> ancestors;

  private ImmutableList<Branch.NameKey> branches;

  @Inject
  public ProjectData(
      GitRepositoryManager repoManager,
      @Assisted Project project,
      @Assisted Iterable<Project.NameKey> ancestors) {
    this.repoManager = repoManager;
    this.project = project;
    this.ancestors = ImmutableList.copyOf(ancestors);
  }

  public Project getProject() {
    return project;
  }

  public ImmutableList<Project.NameKey> getAncestors() {
    return ancestors;
  }

  public Iterable<Branch.NameKey> getBranches() throws IOException {
    if (branches == null) {
      branches =
          ListBranches.allRefs(repoManager, getProject().getNameKey())
              .stream()
              .map(r -> new Branch.NameKey(getProject().getNameKey(), r.getName()))
              .collect(toImmutableList());
    }
    return branches;
  }

  public void setBranches(Iterable<Branch.NameKey> branches) {
    this.branches = ImmutableList.copyOf(branches);
  }
}
