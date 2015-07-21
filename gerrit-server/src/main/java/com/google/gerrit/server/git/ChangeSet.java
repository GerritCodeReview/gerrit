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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;

/** A set of changes grouped together to be submitted atomically.*/
@AutoValue
public abstract class ChangeSet {
  public static ChangeSet create(Iterable<Change> changes) {
    ImmutableSet.Builder<Project.NameKey> pb = ImmutableSet.builder();
    ImmutableSet.Builder<Branch.NameKey> bb = ImmutableSet.builder();
    ImmutableSet.Builder<Change.Id> ib = ImmutableSet.builder();
    ImmutableSet.Builder<PatchSet.Id> psb = ImmutableSet.builder();
    ImmutableSetMultimap.Builder<Project.NameKey, Branch.NameKey> pbb =
        ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<Project.NameKey, Change.Id> pcb =
        ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<Branch.NameKey, Change.Id> cbb =
        ImmutableSetMultimap.builder();

    for (Change c : changes) {
      Branch.NameKey branch = c.getDest();
      Project.NameKey project = branch.getParentKey();
      pb.add(project);
      bb.add(branch);
      ib.add(c.getId());
      psb.add(c.currentPatchSetId());
      pbb.put(project, branch);
      pcb.put(project, c.getId());
      cbb.put(branch, c.getId());
    }

    return new AutoValue_ChangeSet(pb.build(), bb.build(), ib.build(),
        psb.build(), pbb.build(), pcb.build(), cbb.build());
  }

  public static ChangeSet create(Change change) {
    return create(ImmutableList.of(change));
  }

  public abstract ImmutableSet<Project.NameKey> projects();
  public abstract ImmutableSet<Branch.NameKey> branches();
  public abstract ImmutableSet<Change.Id> ids();
  public abstract ImmutableSet<PatchSet.Id> patchIds();
  public abstract ImmutableSetMultimap<Project.NameKey, Branch.NameKey>
      branchesByProject();
  public abstract ImmutableSetMultimap<Project.NameKey, Change.Id>
      changesByProject();
  public abstract ImmutableSetMultimap<Branch.NameKey, Change.Id>
      changesByBranch();

  @Override
  public int hashCode() {
    return ids().hashCode();
  }

  public int size() {
    return ids().size();
  }
}
