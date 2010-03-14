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

package com.google.gerrit.server.git;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

/** Manages Git repositories stored on the local filesystem. */
@Singleton
public class LocalDiskRepositoryManager implements GitRepositoryManager {
  private static final Logger log =
      LoggerFactory.getLogger(LocalDiskRepositoryManager.class);

  private static final String UNNAMED =
      "Unnamed repository; edit this file to name it for gitweb.";

  public static class Lifecycle implements LifecycleListener {
    private final Config cfg;

    @Inject
    Lifecycle(@GerritServerConfig final Config cfg) {
      this.cfg = cfg;
    }

    @Override
    public void start() {
      final WindowCacheConfig c = new WindowCacheConfig();
      c.fromConfig(cfg);
      WindowCache.reconfigure(c);
    }

    @Override
    public void stop() {
    }
  }

  private final File basePath;
  private volatile SortedSet<Project.NameKey> projects;

  @Inject
  LocalDiskRepositoryManager(final SitePaths site,
      @GerritServerConfig final Config cfg) {
    basePath = site.resolve(cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
  }

  /** @return base directory under which all projects are stored. */
  public File getBasePath() {
    return basePath;
  }

  public Repository openRepository(String name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      final FileKey loc = FileKey.lenient(new File(basePath, name), FS.DETECTED);
      return RepositoryCache.open(loc);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  public Repository createRepository(String name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      File dir = FileKey.resolve(new File(basePath, name), FS.DETECTED);
      FileKey loc;
      if (dir != null) {
        // Already exists on disk, use the repository we found.
        //
        loc = FileKey.exact(dir, FS.DETECTED);
      } else {
        // It doesn't exist under any of the standard permutations
        // of the repository name, so prefer the standard bare name.
        //
        if (!name.endsWith(".git")) {
          name = name + ".git";
        }
        loc = FileKey.exact(new File(basePath, name), FS.DETECTED);
      }
      projects = null;
      return RepositoryCache.open(loc, false);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  public String getProjectDescription(final String name)
      throws RepositoryNotFoundException, IOException {
    final Repository e = openRepository(name);
    try {
      final File d = new File(e.getDirectory(), "description");

      String description;
      try {
        description = RawParseUtils.decode(IO.readFully(d));
      } catch (FileNotFoundException err) {
        return null;
      }

      if (description != null) {
        description = description.trim();
        if (description.isEmpty()) {
          description = null;
        }
        if (UNNAMED.equals(description)) {
          description = null;
        }
      }
      return description;
    } finally {
      e.close();
    }
  }

  public void setProjectDescription(final String name, final String description) {
    // Update git's description file, in case gitweb is being used
    //
    try {
      final Repository e;
      final LockFile f;

      e = openRepository(name);
      try {
        f = new LockFile(new File(e.getDirectory(), "description"), FS.DETECTED);
        if (f.lock()) {
          String d = description;
          if (d != null) {
            d = d.trim();
            if (d.length() > 0) {
              d += "\n";
            }
          } else {
            d = "";
          }
          f.write(Constants.encode(d));
          f.commit();
        }
      } finally {
        e.close();
      }
    } catch (RepositoryNotFoundException e) {
      log.error("Cannot update description for " + name, e);
    } catch (IOException e) {
      log.error("Cannot update description for " + name, e);
    }
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

  @Override
  public Iterable<Project.NameKey> all() {
    return getAllNameKeys();
  }

  @Override
  public Iterable<Project.NameKey> byName(final String pfx) {
    Project.NameKey n = new Project.NameKey(pfx);
    final Iterable<Project.NameKey> src = getAllNameKeys().tailSet(n);
    return new Iterable<Project.NameKey>() {
      @Override
      public Iterator<Project.NameKey> iterator() {
        return new Iterator<Project.NameKey>() {
          private Iterator<Project.NameKey> itr = src.iterator();
          private Project.NameKey next;

          @Override
          public boolean hasNext() {
            if (next != null) {
              return true;
            } else if (itr != null && itr.hasNext()) {
              final Project.NameKey n = itr.next();
              if (n.get().startsWith(pfx)) {
                next = n;
                return true;
              } else {
                itr = null;
              }
            }
            return false;
          }

          @Override
          public Project.NameKey next() {
            if (hasNext()) {
              Project.NameKey n = next;
              next = null;
              return n;
            } else {
              throw new NoSuchElementException();
            }
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  private SortedSet<Project.NameKey> getAllNameKeys() {
    SortedSet<Project.NameKey> r = projects;
    if (r == null) {
      r = scanProjects();
      projects = r;
    }
    return r;
  }

  private SortedSet<Project.NameKey> scanProjects() {
    SortedSet<Project.NameKey> names = new TreeSet<Project.NameKey>();
    scanProjects(basePath, "", names);
    return Collections.unmodifiableSortedSet(names);
  }

  private void scanProjects(final File dir, final String prefix,
      final SortedSet<Project.NameKey> names) {
    final File[] ls = dir.listFiles();
    if (ls == null) {
      return;
    }

    for (File f : ls) {
      String name = f.getName();
      if (FileKey.isGitRepository(f, FS.DETECTED)) {
        if (name.equals(Constants.DOT_GIT)) {
          name = prefix.substring(0, prefix.length() - 1);

        } else if (name.endsWith(Constants.DOT_GIT_EXT)) {
          name = prefix + name.substring(0, name.length() - 4);

        } else {
          name = prefix + name;
        }

        if (!isUnreasonableName(name)) {
          names.add(new Project.NameKey(name));
        }

      } else if (f.isDirectory()) {
        scanProjects(f, prefix + f.getName() + "/", names);
      }
    }
  }
}
