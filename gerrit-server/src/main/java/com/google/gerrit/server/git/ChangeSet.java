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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A set of changes grouped together to be submitted atomically.
 * <p>
 * This class is not thread safe.
 */
public class ChangeSet {
  private final ImmutableCollection<ChangeData> changeData;
  private final ChangeUtil changeUtil;

  public interface Factory {
    ChangeSet create(ChangeData change);
    ChangeSet create(Iterable<ChangeData> changes);
  }

  @AssistedInject
  public ChangeSet(ChangeUtil changeUtil,
      @Assisted Iterable<ChangeData> changes) {
    this.changeUtil = changeUtil;
    Set<Change.Id> ids = new HashSet<>();
    ImmutableSet.Builder<ChangeData> cdb = ImmutableSet.builder();
    for (ChangeData cd : changes) {
      if (ids.add(cd.getId())) {
        cdb.add(cd);
      }
    }
    changeData = cdb.build();
  }

  @AssistedInject
  public ChangeSet(ChangeUtil changeUtil, @Assisted ChangeData change) {
    this(changeUtil, ImmutableList.of(change));
  }

  public ImmutableSet<Change.Id> ids() {
    ImmutableSet.Builder<Change.Id> ret = ImmutableSet.builder();
    for (ChangeData cd : changeData) {
      ret.add(cd.getId());
    }
    return ret.build();
  }

  public Set<PatchSet.Id> patchIds() throws OrmException {
    Set<PatchSet.Id> ret = new HashSet<>();
    for (ChangeData cd : changeData) {
      ret.add(cd.change().currentPatchSetId());
    }
    return ret;
  }

  public SetMultimap<Project.NameKey, Branch.NameKey> branchesByProject()
      throws OrmException {
    SetMultimap<Project.NameKey, Branch.NameKey> ret =
        HashMultimap.create();
    for (ChangeData cd : changeData) {
      ret.put(cd.change().getProject(), cd.change().getDest());
    }
    return ret;
  }

  public Multimap<Project.NameKey, Change.Id> changesByProject()
      throws OrmException {
    ListMultimap<Project.NameKey, Change.Id> ret =
        ArrayListMultimap.create();
    for (ChangeData cd : changeData) {
      ret.put(cd.change().getProject(), cd.getId());
    }
    return ret;
  }

  public Multimap<Branch.NameKey, ChangeData> changesByBranch()
      throws OrmException {
    ListMultimap<Branch.NameKey, ChangeData> ret =
        ArrayListMultimap.create();
    for (ChangeData cd : changeData) {
      ret.put(cd.change().getDest(), cd);
    }
    return ret;
  }

  public ImmutableCollection<ChangeData> changes() {
    return changeData;
  }

  public int size() {
    return changeData.size();
  }

  public Boolean isMergeable() throws OrmException, IOException {
    Map<ChangeData, Boolean> mergeabilityMap = new HashMap<>();
    for (ChangeData change : changes()) {
      mergeabilityMap.put(change, false);
    }

    Multimap<Branch.NameKey, ChangeData> cbb = changesByBranch();
    for (Branch.NameKey branch : cbb.keySet()) {
      Collection<ChangeData> targetBranch = cbb.get(branch);
      HashMap<ChangeData, RevCommit> commits =
          changeUtil.findCommits(targetBranch, branch.getParentKey());
      for (ChangeData change : targetBranch) {
        RevCommit commit = commits.get(change);
        boolean isMergeCommit = commit.getParentCount() > 1;
        boolean isLastInChain = !isParentOfAnotherCommit(commit, commits);

        // Recheck mergeability rather than using value stored in the index,
        // which may be stale.
        // TODO(dborowitz): This is ugly; consider providing a way to not read
        // stored fields from the index in the first place.
        change.setMergeable(null);
        Boolean mergeable = change.isMergeable();
        if (mergeable == null) {
          // Skip whole check, cannot determine if mergeable
          return null;
        }
        mergeabilityMap.put(change, mergeable);

        if (isLastInChain && isMergeCommit && mergeable) {
          for (ChangeData c : targetBranch) {
            mergeabilityMap.put(c, true);
          }
          break;
        }
      }
    }
    return !mergeabilityMap.values().contains(Boolean.FALSE);
  }

  private boolean isParentOfAnotherCommit(RevCommit commitToCheck,
      HashMap<ChangeData, RevCommit> commits) {
    for (RevCommit commit : commits.values()) {
      for (RevCommit parent : commit.getParents()) {
        if (parent.equals(commitToCheck)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ids();
  }
}
