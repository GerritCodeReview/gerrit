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

import com.google.gerrit.reviewdb.Project;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.SortedSet;

/**
 * Manages Git repositories for the Gerrit server process.
 * <p>
 * Implementations of this interface should be a {@link Singleton} and
 * registered in Guice so they are globally available within the server
 * environment.
 */
public interface GitRepositoryManager {
  /** Notes branch successful reviews are written to after being merged. */
  public static final String REFS_NOTES_REVIEW = "refs/notes/review";

  /** Note tree listing commits we refuse {@code refs/meta/reject-commits} */
  public static final String REF_REJECT_COMMITS = "refs/meta/reject-commits";

  /** Configuration settings for a project {@code refs/meta/config} */
  public static final String REF_CONFIG = "refs/meta/config";

  /**
   * Get (or open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public abstract Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException;

  /**
   * Create (and open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public abstract Repository createRepository(Project.NameKey name)
      throws RepositoryNotFoundException;

  /** @return set of all known projects, sorted by natural NameKey order. */
  public abstract SortedSet<Project.NameKey> list();

  /**
   * Read the {@code GIT_DIR/description} file for gitweb.
   * <p>
   * NB: This code should really be in JGit, as a member of the Repository
   * object. Until it moves there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @return description text; null if no description has been configured.
   * @throws RepositoryNotFoundException the named repository does not exist.
   * @throws IOException the description file exists, but is not readable by
   *         this process.
   */
  public abstract String getProjectDescription(Project.NameKey name)
      throws RepositoryNotFoundException, IOException;

  /**
   * Set the {@code GIT_DIR/description} file for gitweb.
   * <p>
   * NB: This code should really be in JGit, as a member of the Repository
   * object. Until it moves there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @param description new description text for the repository.
   */
  public abstract void setProjectDescription(Project.NameKey name,
      final String description);
}
