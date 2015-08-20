// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;

import java.util.HashSet;
import java.util.Set;

/** A set of changes grouped together to be submitted atomically.*/
public class ChangeSet {
  private Set<Project.NameKey> projects;
  private Set<Branch.NameKey> branches;
  private Set<Change.Id> changeIds;
  private Set<PatchSet.Id> patchsetIds;
  private Multimap<Project.NameKey, Branch.NameKey> branchByProject;
  private Multimap<Project.NameKey, Change.Id> changeIdbyProject;
  private Multimap<Branch.NameKey, Change.Id> changeIdbyBranch;
  private Set<Change> changes;

  public ChangeSet(Iterable<Change> changes) {
    this.changes = new HashSet<>();
    projects = new HashSet<>();
    branches = new HashSet<>();
    changeIds = new HashSet<>();
    patchsetIds = new HashSet<>();
    branchByProject = HashMultimap.create();
    changeIdbyProject = HashMultimap.create();
    changeIdbyBranch = HashMultimap.create();
    for (Change c : changes) {
      Branch.NameKey branch = c.getDest();
      Project.NameKey project = branch.getParentKey();
      projects.add(project);
      branches.add(branch);
      changeIds.add(c.getId());
      patchsetIds.add(c.currentPatchSetId());
      branchByProject.put(project, branch);
      changeIdbyProject.put(project, c.getId());
      changeIdbyBranch.put(branch, c.getId());
      this.changes.add(c);
    }
  }

  public ChangeSet(Change change) {
    this(ImmutableList.of(change));
  }

  public Set<Project.NameKey> projects() {
    return projects;
  }

  public Set<Branch.NameKey> branches() {
    return branches;
  }

  public Set<Change.Id> ids() {
    return changeIds;
  }

  public Set<PatchSet.Id> patchIds() {
    return patchsetIds;
  }

  public Multimap<Project.NameKey, Branch.NameKey>
      branchesByProject() {
    return branchByProject;
  }

  public Multimap<Project.NameKey, Change.Id>
      changesByProject() {
    return changeIdbyProject;
  }

  public Multimap<Branch.NameKey, Change.Id>
      changesByBranch() {
    return changeIdbyBranch;
  }

  public Set<Change> changes() {
    return changes;
  }

  public int size() {
    return ids().size();
  }
}
