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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterates from a project up through its parents to All-Projects.
 *
 * <p>If a cycle is detected the cycle is broken and All-Projects is visited.
 */
class ProjectHierarchyIterator implements Iterator<ProjectState> {
  private static final Logger log = LoggerFactory.getLogger(ProjectHierarchyIterator.class);

  private final ProjectCache cache;
  private final AllProjectsName allProjectsName;
  private final Set<Project.NameKey> seen;
  private ProjectState next;

  ProjectHierarchyIterator(ProjectCache c, AllProjectsName all, ProjectState firstResult) {
    cache = c;
    allProjectsName = all;

    seen = Sets.newLinkedHashSet();
    seen.add(firstResult.getNameKey());
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
    next = computeNext(n);
    return n;
  }

  private ProjectState computeNext(ProjectState n) {
    Project.NameKey parentName = n.getProject().getParent();
    if (parentName != null && visit(parentName)) {
      ProjectState p = cache.get(parentName);
      if (p != null) {
        return p;
      }
    }

    // Parent does not exist or was already visited.
    // Fall back to visit All-Projects exactly once.
    if (seen.add(allProjectsName)) {
      return cache.get(allProjectsName);
    }
    return null;
  }

  private boolean visit(Project.NameKey parentName) {
    if (seen.add(parentName)) {
      return true;
    }

    List<String> order = Lists.newArrayListWithCapacity(seen.size() + 1);
    for (Project.NameKey p : seen) {
      order.add(p.get());
    }
    int idx = order.lastIndexOf(parentName.get());
    order.add(parentName.get());
    log.warn(
        "Cycle detected in projects: " + Joiner.on(" -> ").join(order.subList(idx, order.size())));
    return false;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
