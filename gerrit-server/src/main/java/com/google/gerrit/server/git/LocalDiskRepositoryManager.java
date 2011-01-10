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
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
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

  private File gitDirOf(Project.NameKey name) {
    return new File(getBasePath(), name.get());
  }

  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      final FileKey loc = FileKey.lenient(gitDirOf(name), FS.DETECTED);
      return RepositoryCache.open(loc);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  public Repository createRepository(final Project.NameKey name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      File dir = FileKey.resolve(gitDirOf(name), FS.DETECTED);
      FileKey loc;
      if (dir != null) {
        // Already exists on disk, use the repository we found.
        //
        loc = FileKey.exact(dir, FS.DETECTED);
      } else {
        // It doesn't exist under any of the standard permutations
        // of the repository name, so prefer the standard bare name.
        //
        String n = name.get();
        if (!n.endsWith(Constants.DOT_GIT_EXT)) {
          n = n + Constants.DOT_GIT_EXT;
        }
        loc = FileKey.exact(new File(basePath, n), FS.DETECTED);
      }

      Repository db = RepositoryCache.open(loc, false);
      db.create(true /* bare */);

      StoredConfig config = db.getConfig();
      config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
        null, ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
      config.save();

      return db;
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot create repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  public String getProjectDescription(final Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    final Repository e = openRepository(name);
    try {
      return getProjectDescription(e);
    } finally {
      e.close();
    }
  }

  private String getProjectDescription(final Repository e) throws IOException {
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
  }

  public void setProjectDescription(final Project.NameKey name,
      final String description) {
    // Update git's description file, in case gitweb is being used
    //
    try {
      final Repository e = openRepository(name);
      try {
        final String old = getProjectDescription(e);
        if ((old == null && description == null)
            || (old != null && old.equals(description))) {
          return;
        }

        final LockFile f = new LockFile(new File(e.getDirectory(), "description"), FS.DETECTED);
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

  private boolean isUnreasonableName(final Project.NameKey nameKey) {
    final String name = nameKey.get();

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
  public SortedSet<Project.NameKey> list() {
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
      String fileName = f.getName();
      if (FileKey.isGitRepository(f, FS.DETECTED)) {
        String projectName;
        if (fileName.equals(Constants.DOT_GIT)) {
          projectName = prefix.substring(0, prefix.length() - 1);

        } else if (fileName.endsWith(Constants.DOT_GIT_EXT)) {
          int newLen = fileName.length() - Constants.DOT_GIT_EXT.length();
          projectName = prefix + fileName.substring(0, newLen);

        } else {
          projectName = prefix + fileName;
        }

        Project.NameKey nameKey = new Project.NameKey(projectName);
        if (isUnreasonableName(nameKey)) {
          log.warn("Ignoring unreasonably named repository " + f.getAbsolutePath());
        } else {
          names.add(nameKey);
        }

      } else if (f.isDirectory()) {
        scanProjects(f, prefix + f.getName() + "/", names);
      }
    }
  }
}
