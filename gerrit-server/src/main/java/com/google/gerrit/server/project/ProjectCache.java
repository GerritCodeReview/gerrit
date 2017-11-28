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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;

import java.io.IOException;
import java.util.Set;

/** Cache of project information, including access rights. */
public interface ProjectCache {
  /** @return the parent state for all projects on this server. */
  ProjectState getAllProjects();

  /** @return the project state of the project storing meta data for all users. */
  ProjectState getAllUsers();

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @return the cached data; null if no such project exists or a error occurred.
   * @see #checkedGet(com.google.gerrit.reviewdb.client.Project.NameKey)
   */
  ProjectState get(Project.NameKey projectName);

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @throws IOException when there was an error.
   * @return the cached data; null if no such project exists.
   */
  ProjectState checkedGet(Project.NameKey projectName)
      throws IOException;

  /** Invalidate the cached information about the given project. */
  void evict(Project p);

  /** Invalidate the cached information about the given project. */
  void evict(Project.NameKey p);

  /**
   * Remove information about the given project from the cache. It will no
   * longer be returned from {@link #all()}.
   */
  void remove(Project p);

  /**
   * Remove information about the given project from the cache. It will no longer be returned from
   * {@link #all()}.
   */
  void remove(Project.NameKey name);

  /** @return sorted iteration of projects. */
  Iterable<Project.NameKey> all();

  /**
   * @return estimated set of relevant groups extracted from hot project access
   *         rules. If the cache is cold or too small for the entire project set
   *         of the server, this set may be incomplete.
   */
  Set<AccountGroup.UUID> guessRelevantGroupUUIDs();

  /**
   * Filter the set of registered project names by common prefix.
   *
   * @param prefix common prefix.
   * @return sorted iteration of projects sharing the same prefix.
   */
  Iterable<Project.NameKey> byName(String prefix);

  /** Notify the cache that a new project was constructed. */
  void onCreateProject(Project.NameKey newProjectName);
}
