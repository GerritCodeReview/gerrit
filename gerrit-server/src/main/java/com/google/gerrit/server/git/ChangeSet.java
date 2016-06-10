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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A set of changes grouped together to be submitted atomically.
 * <p>
 * This class is not thread safe.
 */
public class ChangeSet {
  private final ImmutableMap<Change.Id, ChangeData> changeData;

  public ChangeSet(Iterable<ChangeData> changes) {
    Map<Change.Id, ChangeData> cds = new LinkedHashMap<>();
    for (ChangeData cd : changes) {
      if (!cds.containsKey(cd.getId())) {
        cds.put(cd.getId(), cd);
      }
    }
    changeData = ImmutableMap.copyOf(cds);
  }

  public ChangeSet(ChangeData change) {
    this(ImmutableList.of(change));
  }

  public ImmutableSet<Change.Id> ids() {
    return changeData.keySet();
  }

  public ImmutableMap<Change.Id, ChangeData> changesById() {
    return changeData;
  }

  public Set<PatchSet.Id> patchIds() throws OrmException {
    Set<PatchSet.Id> ret = new HashSet<>();
    for (ChangeData cd : changeData.values()) {
      ret.add(cd.change().currentPatchSetId());
    }
    return ret;
  }

  public SetMultimap<Project.NameKey, Branch.NameKey> branchesByProject()
      throws OrmException {
    SetMultimap<Project.NameKey, Branch.NameKey> ret =
        HashMultimap.create();
    for (ChangeData cd : changeData.values()) {
      ret.put(cd.change().getProject(), cd.change().getDest());
    }
    return ret;
  }

  public Multimap<Project.NameKey, Change.Id> changesByProject()
      throws OrmException {
    ListMultimap<Project.NameKey, Change.Id> ret =
        ArrayListMultimap.create();
    for (ChangeData cd : changeData.values()) {
      ret.put(cd.change().getProject(), cd.getId());
    }
    return ret;
  }

  public Multimap<Branch.NameKey, ChangeData> changesByBranch()
      throws OrmException {
    ListMultimap<Branch.NameKey, ChangeData> ret =
        ArrayListMultimap.create();
    for (ChangeData cd : changeData.values()) {
      ret.put(cd.change().getDest(), cd);
    }
    return ret;
  }

  public ImmutableCollection<ChangeData> changes() {
    return changeData.values();
  }

  public int size() {
    return changeData.size();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ids();
  }
}
