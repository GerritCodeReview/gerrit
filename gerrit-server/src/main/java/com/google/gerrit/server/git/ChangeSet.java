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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

import java.util.HashSet;
import java.util.Set;

/**
 * A set of changes grouped together to be submitted atomically.
 * This is not thread safe.
 */
public class ChangeSet {
  private Set<ChangeData> changeData;

  public ChangeSet(Iterable<ChangeData> changes) {
    changeData = new HashSet<>();
    for (ChangeData cd : changes) {
      this.changeData.add(cd);
    }
  }

  public ChangeSet(ChangeData change) {
    this(ImmutableList.of(change));
  }

  public Set<Project.NameKey> projects() throws OrmException {
    Set<Project.NameKey> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      ret.add(cd.change().getProject());
    }
    return ret;
  }

  public Set<Branch.NameKey> branches() throws OrmException {
    Set<Branch.NameKey> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      ret.add(cd.change().getDest());
    }
    return ret;
  }

  public Set<Change.Id> ids() {
    Set<Change.Id> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      ret.add(cd.getId());
    }
    return ret;
  }

  public Set<PatchSet.Id> patchIds() throws OrmException {
    Set<PatchSet.Id> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      ret.add(cd.change().currentPatchSetId());
    }
    return ret;
  }

  public Set<Branch.NameKey> branchesByProject(Project.NameKey project)
      throws OrmException {
    Set<Branch.NameKey> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      Branch.NameKey branch = cd.change().getDest();
      if (branch.getParentKey().equals(project)) {
        ret.add(branch);
      }
    }
    return ret;
  }

  public Set<Change.Id> changesByProject(Project.NameKey project)
      throws OrmException {
    Set<Change.Id> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      if (cd.change().getProject().equals(project)) {
        ret.add(cd.getId());
      }
    }
    return ret;
  }

  public Set<Change.Id> changesByBranch(Branch.NameKey branch)
      throws OrmException {
    Set<Change.Id> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      if (cd.change().getDest().equals(branch)) {
        ret.add(cd.getId());
      }
    }
    return ret;
  }

  public Set<ChangeData> changes() {
    return changeData;
  }

  public int size() {
    return changeData.size();
  }
}
