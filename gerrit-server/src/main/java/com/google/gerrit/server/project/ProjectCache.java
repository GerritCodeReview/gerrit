// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Project;

import java.util.Map;

/** Cache of project information, including access rights. */
public interface ProjectCache {
  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @return the cached data; null if no such project exists.
   */
  public ProjectState get(Project.NameKey projectName);

  /**
   * Get the cached data for a list of projects by a list of project names.
   *
   * @param projectNames name of the project.
   * @return the cached data; an empty map if no such projects exist.
   */
  public Map<Project.NameKey, ProjectState> getAll(
      Iterable<Project.NameKey> projectNames);

  /** Invalidate the cached information about the given project. */
  public void evict(Project p);

  /** Invalidate the cached information about all projects. */
  public void evictAll();
}
