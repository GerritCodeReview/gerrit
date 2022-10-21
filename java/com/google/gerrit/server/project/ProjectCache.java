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
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Cache of project information, including access rights. */
public interface ProjectCache {
  /**
   * Returns a supplier to be used as a short-hand when unwrapping an {@link Optional} returned from
   * this cache.
   */
  static Supplier<IllegalStateException> illegalState(Project.NameKey nameKey) {
    return () -> new IllegalStateException("unable to find project " + nameKey);
  }

  /**
   * Returns a supplier to be used as a short-hand when unwrapping an {@link Optional} returned from
   * this cache.
   */
  static Supplier<NoSuchProjectException> noSuchProject(Project.NameKey nameKey) {
    return () -> new NoSuchProjectException(nameKey);
  }

  /** Returns the parent state for all projects on this server. */
  ProjectState getAllProjects();

  /** Returns the project state of the project storing meta data for all users. */
  ProjectState getAllUsers();

  /**
   * Get the cached data for a project by its unique name.
   *
   * @param projectName name of the project.
   * @return an {@link Optional} wrapping the cached data; {@code absent} if no such project exists
   *     or the projectName is null
   * @throws StorageException when there was an error.
   */
  Optional<ProjectState> get(@Nullable Project.NameKey projectName) throws StorageException;

  /**
   * Invalidate the cached information about the given project.
   *
   * @param p the NameKey of the project that is being evicted
   */
  void evict(Project.NameKey p);

  /**
   * Invalidate the cached information about the given project, and triggers reindexing for it
   *
   * @param p project that is being evicted
   */
  void evictAndReindex(Project p);

  /**
   * Invalidate the cached information about the given project, and triggers reindexing for it
   *
   * @param p the NameKey of the project that is being evicted
   */
  void evictAndReindex(Project.NameKey p);

  /**
   * Remove information about the given project from the cache. It will no longer be returned from
   * {@link #all()}.
   */
  void remove(Project p);

  /**
   * Remove information about the given project from the cache. It will no longer be returned from
   * {@link #all()}.
   */
  void remove(Project.NameKey name);

  /** Returns sorted iteration of projects. */
  ImmutableSortedSet<Project.NameKey> all();

  /** Refreshes project list cache */
  void refreshProjectList();

  /**
   * Returns estimated set of relevant groups extracted from hot project access rules. If the cache
   * is cold or too small for the entire project set of the server, this set may be incomplete.
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
