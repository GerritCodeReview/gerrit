// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Project;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class RecentlyAccessed implements Serializable {

  private static final long serialVersionUID = 1L;

  private final LinkedList<Project.NameKey> projects =
      new LinkedList<Project.NameKey>();

  /**
   * Adds a project that was recently accessed.
   *
   * @param project the name of the project that was recently accessed
   */
  synchronized void add(final Project.NameKey project) {
    projects.remove(project);
    projects.addFirst(project);
  }

  /**
   * If there are more recently accessed objects cached than the given
   * maxEntries, remove the oldest entries from the cache so that maxEntries
   * stay in the cache.
   *
   * @param maxEntries maximum number of entries that should stay in the cache
   */
  synchronized void limit(final int maxEntries) {
    while (projects.size() > maxEntries) {
      projects.removeLast();
    }
  }

  /**
   * Returns the projects that were recently accessed.
   */
  synchronized List<Project.NameKey> getProjects() {
    return Lists.newArrayList(projects);
  }
}
