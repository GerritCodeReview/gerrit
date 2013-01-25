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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Iterates from a project up through its parents to All-Projects.
 * <p>
 * If a cycle is detected the cycle is broken and All-Projects is visited.
 * When multiple parents are defined for a project parents are visited in
 * reverse order and only the first parent is recursed on. For example if
 * project Child inherits from [A, B, C] then the iteration order will be:
 * <ol>
 * <li>Child</li>
 * <li>C</li>
 * <li>B</li>
 * <li>A</li>
 * <li>A's parent(s)</li>
 * <li>All-Projects</li>
 * </ol>
 */
class ProjectHierarchyIterator implements Iterator<ProjectState> {
  private static final Logger log = LoggerFactory.getLogger(ProjectHierarchyIterator.class);

  private final ProjectCache cache;
  private final AllProjectsName allProjectsName;
  private final Set<Project.NameKey> seen;
  private List<Project.NameKey> parentQueue;
  private ProjectState next;

  ProjectHierarchyIterator(ProjectCache c,
      AllProjectsName all,
      ProjectState firstResult) {
    cache = c;
    allProjectsName = all;

    seen = Sets.newLinkedHashSet();
    seen.add(firstResult.getProject().getNameKey());
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
    // If more parents remain from the last project "n" is a mixin
    // and its own parents are to be ignored. Recursion up the tree
    // resumes when parentQueue drops to empty as "n" is now the first
    // parent of the prior project.
    if (parentQueue != null && !parentQueue.isEmpty()) {
      ProjectState r = popParentQueue();
      if (r != null) {
        return r;
      }
    }

    List<Project.NameKey> parents = n.getProject().getParents();
    if (parents.size() == 1 && visit(parents.get(0))) {
      ProjectState p = cache.get(parents.get(0));
      if (p != null) {
        return p;
      }
    } else if (parents.size() > 1) {
      // With multiple parents visit them in reverse declaration order and
      // recurse only on the first declared parent. For example inheritsFrom
      // is [A, B, C] we visit C, then B, then A and recurse on A. C and B
      // do not visit their parents.
      if (parentQueue == null) {
        parentQueue = Lists.newArrayListWithExpectedSize(parents.size());
      }
      parentQueue.addAll(parents);
      ProjectState r = popParentQueue();
      if (r != null) {
        return r;
      }
    }

    // Parent does not exist or was already visited.
    // Fall back to visit All-Projects exactly once.
    if (seen.add(allProjectsName)) {
      return cache.get(allProjectsName);
    }
    return null;
  }

  private ProjectState popParentQueue() {
    Project.NameKey pn = parentQueue.remove(parentQueue.size() - 1);
    if (visit(pn)) {
      ProjectState p = cache.get(pn);
      if (p != null) {
        return p;
      }
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
    log.warn("Cycle detected in projects: "
        + Joiner.on(" -> ").join(order.subList(idx, order.size())));
    return false;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
