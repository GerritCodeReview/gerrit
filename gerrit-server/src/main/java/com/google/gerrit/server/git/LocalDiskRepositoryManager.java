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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Manages Git repositories stored on the local filesystem. */
@Singleton
public class LocalDiskRepositoryManager implements GitRepositoryManager {
  private static final Logger log =
      LoggerFactory.getLogger(LocalDiskRepositoryManager.class);

  private static final String UNNAMED =
      "Unnamed repository; edit this file to name it for gitweb.";

  /**
   * Map that keeps a {@link ReadWriteLock} for each {@link Repository}.
   *
   * Why do we need to do locking for the repositories?
   * In general when working with repositories there is no need for Gerrit to
   * care about locking, since locking and synchronization is already handled
   * within JGit.
   * Unfortunately JGit is currently not supporting renaming of repositories.
   * Renaming a repository means to move the repository folder in the filesystem.
   * While moving the folder in the filesystem we have to ensure that there
   * are no operations executed on the repository. Hence we have to do proper
   * locking if we want to support renaming of repositories.
   *
   * For locking a repository we use a {@link ReadWriteLock}.
   * A write lock is used for those operations for which we have to ensure
   * exclusive access to the repository (rename, repository creation).
   * On the other hand read locks are used for those operations that do not
   * need exclusive repository access or for which the locking and synchronization
   * is already handled in JGit. This means that even for most write operations
   * a read lock on the repository is sufficient.
   */
  private final Map<String, ReadWriteLock> repoReadWriteLocks =
      new HashMap<String, ReadWriteLock>();

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

  public Repository openRepository(String name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    final FileKey loc = FileKey.lenient(new File(basePath, name), FS.DETECTED);

    // For all operations that can be performed on a repository a read lock is
    // sufficient since for these operations JGit already ensures proper
    // locking and synchronization.
    // The read lock will be released when the repository is closed by
    // #closeRepository(Repository).
    final Lock readLock = getLockInstance(name).readLock();
    readLock.lock();

    try {
      return RepositoryCache.open(loc);
    } catch (IOException e1) {
      readLock.unlock();
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    } catch (RuntimeException e) {
      readLock.unlock();
      throw e;
    }
  }

  public Repository createRepository(final String name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    // the creation of a new repository must be protected by a write lock
    // on the repository name (otherwise we would run into problems if at the
    // same time 2 repositories with the same name are created or if at the
    // same time a new repository is created with the same name to which a
    // repository is being renamed)
    final Lock writeLock = getLockInstance(name).writeLock();
    writeLock.lock();
    try {
      FileKey loc = getLocation(name);

      // the created repository is returned to the caller to perform further
      // operations on it, for these operations a read lock is sufficient since
      // JGit already ensures proper locking and synchronization for them,
      // the read lock will be released when the repository is closed by
      // #closeRepository(Repository)
      final Lock readLock = getLockInstance(name).readLock();
      readLock.lock();
      try {
        return RepositoryCache.open(loc, false);
      } catch (IOException e1) {
        readLock.unlock();
        final RepositoryNotFoundException e2;
        e2 = new RepositoryNotFoundException("Cannot open repository " + name);
        e2.initCause(e1);
        throw e2;
      } catch (RuntimeException e) {
        readLock.unlock();
        throw e;
      }
    } finally {
      writeLock.unlock();
    }
  }

  private FileKey getLocation(String name) {
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
    return loc;
  }

  public void closeRepository(final Repository repo) {
    try {
      repo.close();
    } finally {
      // even if the closing of the repository fails with a runtime exception we
      // must release the lock, it is unlikely that the caller will perform
      // additional operations on the repository after closing the repository
      // has failed
      getLockInstance(repo).readLock().unlock();
    }
  }

  public void renameRepository(String name, String newName)
      throws RepositoryNotFoundException, RepositoryRenameException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }
    if (isUnreasonableName(newName)) {
      throw new RepositoryNotFoundException("Invalid name: " + newName);
    }

    if (name.equals(newName)) {
      // nothing to do
      return;
    }

    // for the rename operation we need exclusive access to the repository,
    // this is why we need to acquire a write lock for it, in addition we need a
    // write lock for the new repository name (e.g. to prevent 2 different
    // projects being renamed to the same name at the same time or to prevent at
    // the same time the creation of a new repository with the same name to
    // which a repository is being renamed)

    // in order to prevent potential deadlocks the order of acquiring the locks
    // for the 2 repository names (old name and new name) must be deterministic,
    // this is why we always first request the lock for the repository name
    // which comes first in lexicographically order (without this sorting we could
    // run with following situation into a deadlock: 2 renames at the same time,
    // project 'a' being renamed to 'b' and project 'b' being renamed to 'a', the first
    // rename might get a lock for 'a' while the second rename got the lock for 'b',
    // now both renames would be waiting for the lock hold by the other rename)
    final Lock writeLock1;
    final Lock writeLock2;
    if (name.compareTo(newName) < 0) {
      writeLock1 = getLockInstance(name).writeLock();
      writeLock2 = getLockInstance(newName).writeLock();
    } else {
      writeLock1 = getLockInstance(newName).writeLock();
      writeLock2 = getLockInstance(name).writeLock();
    }

    writeLock1.lock();
    try {
      writeLock2.lock();
      try {
        if (exists(newName)) {
          throw new RepositoryRenameException(name, newName,
              "A project with the name \"" + newName + "\" already exists.");
        }

        final FileKey loc = FileKey.lenient(new File(basePath, name), FS.DETECTED);
        final FileKey newLoc = getLocation(newName);

        // move the repository in the filesystem
        final File newFile = newLoc.getFile();
        if (!newFile.getParentFile().exists()) {
          if (!newFile.getParentFile().mkdirs()) {
            throw new RepositoryRenameException(name, newName);
          }
        }
        if (!loc.getFile().renameTo(newFile)) {
          throw new RepositoryRenameException(name, newName);
        }
      } finally {
        writeLock2.unlock();
      }
    } finally {
      writeLock1.unlock();
    }
  }

  /**
   * Checks whether a repository with the given name already exists.
   *
   * @param name the name of the repository
   * @return <code>true</code> if a repository with the given name already
   *         exists, otherwise <code>false</code>
   */
  private boolean exists(final String name) {
    final File dir = FileKey.resolve(new File(basePath, name), FS.DETECTED);
    return dir != null;
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
      closeRepository(e);
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
        closeRepository(e);
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

  /**
   * Returns the {@link ReadWriteLock} for the repository with the given name.
   * If a read write lock is requested for a repository for the first time, a
   * new read write lock is instantiated and stored within the map. If a read
   * write lock for a repository was already created the existing instance from
   * the map is returned.
   *
   * @param name the name of the repository
   * @return the {@link ReadWriteLock} for the repository
   */
  private ReadWriteLock getLockInstance(final String name) {
    return getLockInstanceByRepoPath(getLocation(name).getFile().getAbsolutePath());
  }

  /**
   * Returns the {@link ReadWriteLock} for the given repository.
   * If a read write lock is requested for a repository for the first time, a
   * new read write lock is instantiated and stored within the map. If a read
   * write lock for a repository was already created the existing instance from
   * the map is returned.
   *
   * @param name the name of the repository
   * @return the {@link ReadWriteLock} for the repository
   */
  private ReadWriteLock getLockInstance(final Repository repo) {
    return getLockInstanceByRepoPath(repo.getDirectory().getAbsolutePath());
  }

  /**
   * Returns the {@link ReadWriteLock} for the repository with the given path.
   * If a read write lock is requested for a repository for the first time, a
   * new read write lock is instantiated and stored within the map. If a read
   * write lock for a repository was already created the existing instance from
   * the map is returned.
   *
   * @param path the absolute path of the repository
   * @return the {@link ReadWriteLock} for the repository
   */
  private synchronized ReadWriteLock getLockInstanceByRepoPath(final String repoPath) {
    ReadWriteLock lock = repoReadWriteLocks.get(repoPath);
    if (lock == null) {
      lock = new ReentrantReadWriteLock();
      repoReadWriteLocks.put(repoPath, lock);
    }
    return lock;
  }
}
