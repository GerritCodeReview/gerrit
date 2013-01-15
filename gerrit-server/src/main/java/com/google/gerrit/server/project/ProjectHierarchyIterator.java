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

package com.google.gerrit.server.project;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

class ProjectHierarchyIterator implements Iterator<ProjectState> {
  private final ProjectCache cache;
  private final AllProjectsName allProjectsName;
  private final Set<Project.NameKey> seen;
  private ProjectState next;

  ProjectHierarchyIterator(ProjectCache c,
      AllProjectsName all,
      ProjectState firstResult) {
    cache = c;
    allProjectsName = all;
    seen = Sets.newHashSet();
    seen.add(firstResult.getProject().getParent());
    next = firstResult;
  }

  @Override
  public boolean hasNext() {
    return next != null;
  }

  @Override
  public ProjectState next() {
    ProjectState n = next;
    if (n == null) {
      throw new NoSuchElementException();
    }
    next = null;

    Project.NameKey parentName = n.getProject().getParent();
    if (parentName != null && seen.add(parentName)) {
      ProjectState p = cache.get(parentName);
      if (p != null) {
        next = p;
        return n;
      }
    }

    // Parent does not exist or was already visited.
    // Fall back to visit All-Projects exactly once.
    if (seen.add(allProjectsName)) {
      next = cache.get(allProjectsName);
    }
    return n;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
