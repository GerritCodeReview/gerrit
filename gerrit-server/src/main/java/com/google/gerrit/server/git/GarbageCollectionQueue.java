// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class GarbageCollectionQueue {
  private Set<Project.NameKey> projectsScheduledForGc =
      Collections.synchronizedSet(new HashSet<Project.NameKey>());

  public List<Project.NameKey> addAll(List<Project.NameKey> projects) {
    List<Project.NameKey> addedProjects =
        Lists.newArrayListWithExpectedSize(projects.size());
    for (Project.NameKey p : projects) {
      if (!projectsScheduledForGc.contains(p)) {
        projectsScheduledForGc.add(p);
        addedProjects.add(p);
      }
    }
    return addedProjects;
  }

  public void gcFinished(Project.NameKey project) {
    projectsScheduledForGc.remove(project);
  }
}
