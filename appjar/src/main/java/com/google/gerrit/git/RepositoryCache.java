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

/** Cache of active Git repositories being used by the manager. */
public class RepositoryCache {
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

    final Reference<Repository> ref = cache.get(name);
    Repository db = ref != null ? ref.get() : null;
    if (db == null) {
      if (isUnreasonableName(name)) {
        throw new InvalidRepositoryException(name);
      }
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

  private boolean isUnreasonableName(final String name) {
    if (name.length() == 0) return true; // no empty paths

    if (name.indexOf('\\') >= 0) return true; // no windows/dos stlye paths
    if (name.charAt(0) == '/') return true; // no absolute paths
    if (new File(name).isAbsolute()) return true; // no absolute paths

    if (name.startsWith("../")) return true; // no "l../etc/passwd"
    if (name.contains("/../")) return true; // no "foo/../etc/passwd"
    if (name.contains("/./")) return true; // "foo/./foo" is insane to ask
    if (name.contains("//")) return true; // windows UNC path can be "//..."

    return false; // is a reasonable name
  }
}
