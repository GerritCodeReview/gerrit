// Copyright 2008 Google Inc.
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

package com.google.gerrit.git;

import org.spearce.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Cache of active Git repositories being used by the manager. */
public class RepositoryCache {
  private static final Pattern REPO_NAME =
      Pattern.compile("^[A-Za-z][A-Za-z0-9/_-]+$");

  private final File base;

  private final Map<String, Reference<Repository>> cache;

  /**
   * Create a new cache to manage a specific base directory (and below).
   * 
   * @param basedir top level directory that contains all repositories.
   */
  public RepositoryCache(final File basedir) {
    base = basedir;
    cache = new HashMap<String, Reference<Repository>>();
  }

  /**
   * @return the base directory which contains all known repositories.
   */
  public File getBaseDirectory() {
    return base;
  }

  /**
   * Get (or open) a repository by name.
   * 
   * @param name the repository name, relative to the base directory supplied
   *        when the cache was created.
   * @return the cached Repository instance.
   * @throws InvalidRepositoryException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public synchronized Repository get(String name)
      throws InvalidRepositoryException {
    if (name.endsWith(".git")) {
      name = name.substring(0, name.length() - 4);
    }

    if (!REPO_NAME.matcher(name).matches()) {
      throw new InvalidRepositoryException(name);
    }

    final Reference<Repository> ref = cache.get(name);
    Repository db = ref != null ? ref.get() : null;
    if (db == null) {
      try {
        db = GitMetaUtil.open(new File(base, name));
        if (db == null) {
          throw new InvalidRepositoryException(name);
        }
      } catch (IOException err) {
        throw new InvalidRepositoryException(name, err);
      }
      cache.put(name, new SoftReference<Repository>(db));
    }
    return db;
  }
}
