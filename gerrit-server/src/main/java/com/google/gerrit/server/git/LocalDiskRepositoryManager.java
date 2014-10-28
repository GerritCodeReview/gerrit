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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.jcraft.jsch.Session;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Manages Git repositories stored on the local filesystem. */
@Singleton
public class LocalDiskRepositoryManager implements GitRepositoryManager {
  private static final Logger log =
      LoggerFactory.getLogger(LocalDiskRepositoryManager.class);

  private static final String UNNAMED =
      "Unnamed repository; edit this file to name it for gitweb.";

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(GitRepositoryManager.class).to(LocalDiskRepositoryManager.class);
      listener().to(LocalDiskRepositoryManager.Lifecycle.class);
    }
  }

  public static class Lifecycle implements LifecycleListener {
    private final Config serverConfig;

    @Inject
    Lifecycle(@GerritServerConfig final Config cfg) {
      this.serverConfig = cfg;
    }

    @Override
    public void start() {
      // Install our own factory which always runs in batch mode, as we
      // have no UI available for interactive prompting.
      SshSessionFactory.setInstance(new JschConfigSessionFactory() {
        @Override
        protected void configure(OpenSshConfig.Host hc, Session session) {
          // Default configuration is batch mode.
        }
      });

      WindowCacheConfig cfg = new WindowCacheConfig();
      cfg.fromConfig(serverConfig);
      if (serverConfig.getString("core", null, "streamFileThreshold") == null) {
        long mx = Runtime.getRuntime().maxMemory();
        int limit = (int) Math.min(
            mx / 4, // don't use more than 1/4 of the heap.
            2047 << 20); // cannot exceed array length
        if ((5 << 20) < limit && limit % (1 << 20) != 0) {
          // If the limit is at least 5 MiB but is not a whole multiple
          // of MiB round up to the next one full megabyte. This is a very
          // tiny memory increase in exchange for nice round units.
          limit = ((limit / (1 << 20)) + 1) << 20;
        }

        String desc;
        if (limit % (1 << 20) == 0) {
          desc = String.format("%dm", limit / (1 << 20));
        } else if (limit % (1 << 10) == 0) {
          desc = String.format("%dk", limit / (1 << 10));
        } else {
          desc = String.format("%d", limit);
        }
        log.info(String.format(
            "Defaulting core.streamFileThreshold to %s",
            desc));
        cfg.setStreamFileThreshold(limit);
      }
      cfg.install();
    }

    @Override
    public void stop() {
    }
  }

  private final File basePath;
  private final File noteDbPath;
  private final Lock namesUpdateLock;
  private volatile SortedSet<Project.NameKey> names;

  @Inject
  LocalDiskRepositoryManager(SitePaths site,
      @GerritServerConfig Config cfg,
      NotesMigration notesMigration) {
    basePath = site.resolve(cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }

    if (notesMigration.enabled()) {
      noteDbPath = site.resolve(MoreObjects.firstNonNull(
          cfg.getString("gerrit", null, "noteDbPath"), "notedb"));
    } else {
      noteDbPath = null;
    }
    namesUpdateLock = new ReentrantLock(true /* fair */);
    names = list();
  }

  /** @return base directory under which all projects are stored. */
  public File getBasePath() {
    return basePath;
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException {
    return openRepository(basePath, name);
  }

  private Repository openRepository(File path, Project.NameKey name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }
    File gitDir = new File(path, name.get());
    if (!names.contains(name)) {
      // The this.names list does not hold the project-name but it can still exist
      // on disk; for instance when the project has been created directly on the
      // file-system through replication.
      //
      if (!name.get().endsWith(Constants.DOT_GIT_EXT)) {
        if (FileKey.resolve(gitDir, FS.DETECTED) != null) {
          onCreateProject(name);
        } else {
          throw new RepositoryNotFoundException(gitDir);
        }
      } else {
        final File directory = gitDir;
        if (FileKey.isGitRepository(new File(directory, Constants.DOT_GIT),
            FS.DETECTED)) {
          onCreateProject(name);
        } else if (FileKey.isGitRepository(new File(directory.getParentFile(),
            directory.getName() + Constants.DOT_GIT_EXT), FS.DETECTED)) {
          onCreateProject(name);
        } else {
          throw new RepositoryNotFoundException(gitDir);
        }
      }
    }
    final FileKey loc = FileKey.lenient(gitDir, FS.DETECTED);
    try {
      return RepositoryCache.open(loc);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  @Override
  public Repository createRepository(Project.NameKey name)
      throws RepositoryNotFoundException, RepositoryCaseMismatchException {
    Repository repo = createRepository(basePath, name);
    if (noteDbPath != null) {
      createRepository(noteDbPath, name);
    }
    return repo;
  }

  private Repository createRepository(File path, Project.NameKey name)
      throws RepositoryNotFoundException, RepositoryCaseMismatchException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    File dir = FileKey.resolve(new File(path, name.get()), FS.DETECTED);
    FileKey loc;
    if (dir != null) {
      // Already exists on disk, use the repository we found.
      //
      loc = FileKey.exact(dir, FS.DETECTED);

      if (!names.contains(name)) {
        throw new RepositoryCaseMismatchException(name);
      }
    } else {
      // It doesn't exist under any of the standard permutations
      // of the repository name, so prefer the standard bare name.
      //
      String n = name.get() + Constants.DOT_GIT_EXT;
      loc = FileKey.exact(new File(path, n), FS.DETECTED);
    }

    try {
      Repository db = RepositoryCache.open(loc, false);
      db.create(true /* bare */);

      StoredConfig config = db.getConfig();
      config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
        null, ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
      config.save();

      onCreateProject(name);

      return db;
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot create repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  @Override
  public Repository openMetadataRepository(Project.NameKey name)
      throws RepositoryNotFoundException, IOException {
    checkState(noteDbPath != null, "notedb disabled");
    try {
      return openRepository(noteDbPath, name);
    } catch (RepositoryNotFoundException e) {
      return createRepository(noteDbPath, name);
    }
  }

  private void onCreateProject(final Project.NameKey newProjectName) {
    namesUpdateLock.lock();
    try {
      SortedSet<Project.NameKey> n = new TreeSet<>(names);
      n.add(newProjectName);
      names = Collections.unmodifiableSortedSet(n);
    } finally {
      namesUpdateLock.unlock();
    }
  }

  @Override
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

  @Override
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
    if (name.charAt(name.length() -1) == '/') return true; // no suffix

    if (name.indexOf('\\') >= 0) return true; // no windows/dos style paths
    if (name.charAt(0) == '/') return true; // no absolute paths
    if (new File(name).isAbsolute()) return true; // no absolute paths

    if (name.startsWith("../")) return true; // no "l../etc/passwd"
    if (name.contains("/../")) return true; // no "foo/../etc/passwd"
    if (name.contains("/./")) return true; // "foo/./foo" is insane to ask
    if (name.contains("//")) return true; // windows UNC path can be "//..."
    if (name.contains("?")) return true; // common unix wildcard
    if (name.contains("%")) return true; // wildcard or string parameter
    if (name.contains("*")) return true; // wildcard
    if (name.contains(":")) return true; // Could be used for absolute paths in windows?
    if (name.contains("<")) return true; // redirect input
    if (name.contains(">")) return true; // redirect output
    if (name.contains("|")) return true; // pipe
    if (name.contains("$")) return true; // dollar sign
    if (name.contains("\r")) return true; // carriage return

    return false; // is a reasonable name
  }

  @Override
  public SortedSet<Project.NameKey> list() {
    // The results of this method are cached by ProjectCacheImpl. Control only
    // enters here if the cache was flushed by the administrator to force
    // scanning the filesystem. Don't rely on the cached names collection.
    namesUpdateLock.lock();
    try {
      SortedSet<Project.NameKey> n = new TreeSet<>();
      scanProjects(basePath, "", n);
      names = Collections.unmodifiableSortedSet(n);
      return n;
    } finally {
      namesUpdateLock.unlock();
    }
  }

  private void scanProjects(final File dir, final String prefix,
      final SortedSet<Project.NameKey> names) {
    final File[] ls = dir.listFiles();
    if (ls == null) {
      return;
    }

    for (File f : ls) {
      String fileName = f.getName();
      if (fileName.equals(Constants.DOT_GIT)) {
        // Skip repositories named only `.git`
      } else if (FileKey.isGitRepository(f, FS.DETECTED)) {
        Project.NameKey nameKey = getProjectName(prefix, fileName);
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

  private Project.NameKey getProjectName(final String prefix,
      final String fileName) {
    final String projectName;
    if (fileName.endsWith(Constants.DOT_GIT_EXT)) {
      int newLen = fileName.length() - Constants.DOT_GIT_EXT.length();
      projectName = prefix + fileName.substring(0, newLen);

    } else {
      projectName = prefix + fileName;
    }

    return new Project.NameKey(projectName);
  }
}
