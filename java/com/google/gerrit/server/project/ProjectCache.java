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

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
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
   * @return the cached data; null if no such project exists, projectName is null or an error
   *     occurred.
   * @see #checkedGet(com.google.gerrit.entities.Project.NameKey)
   */
  ProjectState get(@Nullable Project.NameKey projectName);

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @throws IOException when there was an error.
   * @return the cached data; null if no such project exists or projectName is null.
   */
  ProjectState checkedGet(@Nullable Project.NameKey projectName) throws IOException;

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @param strict true when any error generates an exception
   * @throws Exception in case of any error (strict = true) or only for I/O or other internal
   *     errors.
   * @return the cached data or null when strict = false
   */
  ProjectState checkedGet(Project.NameKey projectName, boolean strict) throws Exception;

  /**
   * Invalidate the cached information about the given project, and triggers reindexing for it
   *
   * @param p project that is being evicted
   * @throws IOException thrown if the reindexing fails
   */
  void evict(Project p) throws IOException;

  /**
   * Invalidate the cached information about the given project, and triggers reindexing for it
   *
   * @param p the NameKey of the project that is being evicted
   * @throws IOException thrown if the reindexing fails
   */
  void evict(Project.NameKey p) throws IOException;

  /**
   * Remove information about the given project from the cache. It will no longer be returned from
   * {@link #all()}.
   */
  void remove(Project p) throws IOException;

  /**
   * Remove information about the given project from the cache. It will no longer be returned from
   * {@link #all()}.
   */
  void remove(Project.NameKey name) throws IOException;

  /** @return sorted iteration of projects. */
  ImmutableSortedSet<Project.NameKey> all();

  /**
   * @return estimated set of relevant groups extracted from hot project access rules. If the cache
   *     is cold or too small for the entire project set of the server, this set may be incomplete.
   */
  Set<AccountGroup.UUID> guessRelevantGroupUUIDs();

  /**
   * Filter the set of registered project names by common prefix.
   *
   * @param prefix common prefix.
   * @return sorted iteration of projects sharing the same prefix.
   */
  ImmutableSortedSet<Project.NameKey> byName(String prefix);

  /** Notify the cache that a new project was constructed. */
  void onCreateProject(Project.NameKey newProjectName) throws IOException;
}
