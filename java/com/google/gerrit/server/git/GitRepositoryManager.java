// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.entities.Project;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

/**
 * Manages Git repositories for the Gerrit server process.
 *
 * <p>Implementations of this interface should be a {@link Singleton} and registered in Guice so
 * they are globally available within the server environment.
 */
@ImplementedBy(value = LocalDiskRepositoryManager.class)
public interface GitRepositoryManager {
  /**
   * Get (or open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()} when done to decrement
   *     the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing repository.
   * @throws IOException the name cannot be read as a repository.
   */
  Repository openRepository(Project.NameKey name) throws RepositoryNotFoundException, IOException;

  /**
   * Create (and open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()} when done to decrement
   *     the resource handle.
   * @throws RepositoryCaseMismatchException the name collides with an existing repository name, but
   *     only in case of a character within the name.
   * @throws RepositoryNotFoundException the name is invalid.
   * @throws IOException the repository cannot be created.
   */
  Repository createRepository(Project.NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException;

  /** @return set of all known projects, sorted by natural NameKey order. */
  SortedSet<Project.NameKey> list();

  /**
   * Check if garbage collection can be performed by the repository manager.
   *
   * @return true if repository can perform garbage collection.
   */
  default Boolean canPerformGC() {
    return false;
  }
}
