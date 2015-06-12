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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A set of changes grouped together to be submitted atomically.*/
@AutoValue
public abstract class ChangeSet {
  public static ChangeSet create(Iterable<Change> changes) {
    ImmutableSet.Builder<Project.NameKey> pb = ImmutableSet.builder();
    ImmutableSet.Builder<Branch.NameKey> bb = ImmutableSet.builder();
    ImmutableSet.Builder<Change.Id> ib = ImmutableSet.builder();
    Map<Project.NameKey, ImmutableSet.Builder<Change.Id>> map = new HashMap<>();
    ImmutableMap.Builder<Project.NameKey, ImmutableSet<Change.Id>> mb
      = ImmutableMap.builder();
    ImmutableSet.Builder<String> tb = ImmutableSet.builder();


    for (Change c : changes) {
      Project.NameKey project = c.getDest().getParentKey();
      pb.add(project);
      bb.add(c.getDest());
      ib.add(c.getId());
      if (!Strings.isNullOrEmpty(c.getTopic())) {
        tb.add(c.getTopic());
      }
      if (!map.containsKey(project)) {
        ImmutableSet.Builder<Change.Id> mapId = ImmutableSet.builder();
        map.put(project, mapId);
      }
      map.get(project).add(c.getId());
    }

    ImmutableSet<Project.NameKey> projects = pb.build();
    for (Project.NameKey project : projects) {
      mb.put(project, map.get(project).build());
    }

    return new AutoValue_ChangeSet(projects, bb.build(), ib.build(),
        tb.build(), mb.build());
  }

  public static ChangeSet create(Change change) {
    return create(ImmutableList.of(change));
  }

  public abstract ImmutableSet<Project.NameKey> repos();
  public abstract ImmutableSet<Branch.NameKey> branches();
  public abstract ImmutableSet<Change.Id> ids();
  public abstract ImmutableSet<String> topics();
  public abstract ImmutableMap<Project.NameKey, ImmutableSet<Change.Id>> idByProject();


  @Override
  public int hashCode() {
    return ids().hashCode();
  }
}
