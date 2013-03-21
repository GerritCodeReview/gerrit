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

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Set;

@Singleton
public class GarbageCollectionQueue {
  private final Set<Project.NameKey> projectsScheduledForGc = Sets.newHashSet();

  public synchronized Set<Project.NameKey> addAll(Collection<Project.NameKey> projects) {
    Set<Project.NameKey> added =
        Sets.newLinkedHashSetWithExpectedSize(projects.size());
    for (Project.NameKey p : projects) {
      if (projectsScheduledForGc.add(p)) {
        added.add(p);
      }
    }
    return added;
  }

  public synchronized void gcFinished(Project.NameKey project) {
    projectsScheduledForGc.remove(project);
  }
}
