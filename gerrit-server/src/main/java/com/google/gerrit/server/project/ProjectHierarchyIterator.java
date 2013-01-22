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
import com.google.common.collect.Iterators;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Iterates from a project up through its parents to All-Projects.
 * <p>
 * If a cycle is detected the cycle is broken and All-Projects is visited.
 */
class ProjectHierarchyIterator implements Iterator<ProjectState> {
  private static final Logger log = LoggerFactory.getLogger(ProjectHierarchyIterator.class);

  private final ProjectCache cache;
  private final AllProjectsName allProjectsName;
  private final List<Project.NameKey> order;
  private int index;

  ProjectHierarchyIterator(ProjectCache c,
      AllProjectsName all,
      ProjectState firstResult) {
    cache = c;
    allProjectsName = all;
    order = new ArrayList<Project.NameKey>();
    index = 0;

    Set<Project.NameKey> seen = Sets.newLinkedHashSet();
    visit(firstResult, seen);

    seen.clear();
    ListIterator<Project.NameKey> iter = order.listIterator(order.size());
    while (iter.hasPrevious()) {
      if (!seen.add(iter.previous()))
        iter.remove();
    }
  }

  private final void visit(ProjectState st, Set<Project.NameKey> seen) {
    if (st == null) {
      order.add(allProjectsName);
      return;
    }

    Project pr = st.getProject();
    if (pr == null) {
      return;
    }

    // add comes before seen checking on purpose.
    // this visitlist is only for cycle checking

    order.add(pr.getNameKey());

    if (!seen.add(pr.getNameKey())) {
      return;
    }

    List<Project.NameKey> parents = pr.getParents();

    if (parents == null || parents.size() < 1) {
      order.add(allProjectsName);
      return;
    }

    for (Project.NameKey parentKey : pr.getParents()) {
      visit(cache.get(parentKey), seen);
    }
  }

  @Override
  public boolean hasNext() {
    return index < order.size();
  }

  @Override
  public ProjectState next() {
    return cache.get(order.get(index++));
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
