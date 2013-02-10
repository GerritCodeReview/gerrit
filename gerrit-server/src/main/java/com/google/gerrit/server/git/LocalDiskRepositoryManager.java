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

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

// To avoid adding a dependency on CGLIB, we reuse the already provided hidden
// instance. See comments in {@code wrapProject@}
import com.google.inject.internal.cglib.proxy.$Enhancer;
import com.google.inject.internal.cglib.proxy.$MethodInterceptor;
import com.google.inject.internal.cglib.proxy.$MethodProxy;

import com.jcraft.jsch.Session;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.storage.file.WindowCache;
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
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Manages Git repositories stored on the local filesystem. */
@Singleton
public class LocalDiskRepositoryManager implements GitRepositoryManager {
  private static final Logger log =
      LoggerFactory.getLogger(LocalDiskRepositoryManager.class);

  private static final String UNNAMED =
      "Unnamed repository; edit this file to name it for gitweb.";

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(GitRepositoryManager.class).to(LocalDiskRepositoryManager.class);

      install(new LifecycleModule() {
        @Override
        protected void configure() {
          listener().to(LocalDiskRepositoryManager.Lifecycle.class);
        }
      });
    }
  }

  public static class Lifecycle implements LifecycleListener {
    private final Config cfg;

    @Inject
    Lifecycle(@GerritServerConfig final Config cfg) {
      this.cfg = cfg;
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

      final WindowCacheConfig c = new WindowCacheConfig();
      c.fromConfig(cfg);
      WindowCache.reconfigure(c);
    }

    @Override
    public void stop() {
    }
  }

  private final File basePath;
  private final Lock namesUpdateLock;
  private volatile SortedSet<Project.NameKey> names;
  private final Map<File, ReadWriteLock> accessLocks =
      new HashMap<File, ReadWriteLock>();

  @Inject
  LocalDiskRepositoryManager(final SitePaths site,
      @GerritServerConfig final Config cfg) {
    basePath = site.resolve(cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
    namesUpdateLock = new ReentrantLock(true /* fair */);
    names = list();
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
    if (!names.contains(name)) {
      // The this.names list does not hold the project-name but it can still exist
      // on disk; for instance when the project has been created directly on the
      // file-system through replication.
      //
      if (FileKey.resolve(gitDirOf(name), FS.DETECTED) != null) {
        onCreateProject(name);
      } else {
        throw new RepositoryNotFoundException(gitDirOf(name));
      }
    }
    final FileKey loc = FileKey.lenient(gitDirOf(name), FS.DETECTED);
    try {
      Lock lock = getReadLock(name);
      lock.lock();
      Repository ret = null;
      try {
        ret = wrapRepository(RepositoryCache.open(loc), lock);
      } finally {
        if (ret == null) {
          lock.unlock();
        }
      }
      return ret;
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  public Repository createRepository(final Project.NameKey name)
      throws RepositoryNotFoundException, RepositoryCaseMismatchException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    File dir = FileKey.resolve(gitDirOf(name), FS.DETECTED);
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
      loc = FileKey.exact(new File(basePath, n), FS.DETECTED);
    }

    Lock lock = getReadLock(name);
    lock.lock();
    boolean releaseLock = true;
    try {
      Repository db = wrapRepository(RepositoryCache.open(loc, false), lock);
      db.create(true /* bare */);

      StoredConfig config = db.getConfig();
      config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION,
        null, ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
      config.save();

      onCreateProject(name);

      releaseLock = false;
      return db;
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot create repository " + name);
      e2.initCause(e1);
      throw e2;
    } finally {
      if (releaseLock) {
        lock.unlock();
      }
    }
  }

  private void onCreateProject(final Project.NameKey newProjectName) {
    namesUpdateLock.lock();
    try {
      SortedSet<Project.NameKey> n = new TreeSet<Project.NameKey>(names);
      n.add(newProjectName);
      names = Collections.unmodifiableSortedSet(n);
    } finally {
      namesUpdateLock.unlock();
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
      SortedSet<Project.NameKey> n = new TreeSet<Project.NameKey>();
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
      if (FileKey.isGitRepository(f, FS.DETECTED)) {
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
    if (fileName.equals(Constants.DOT_GIT)) {
      projectName = prefix.substring(0, prefix.length() - 1);

    } else if (fileName.endsWith(Constants.DOT_GIT_EXT)) {
      int newLen = fileName.length() - Constants.DOT_GIT_EXT.length();
      projectName = prefix + fileName.substring(0, newLen);

    } else {
      projectName = prefix + fileName;
    }

    return new Project.NameKey(projectName);
  }

  @Override
  public void renameRepository(final Project.NameKey source,
      final Project.NameKey destination) throws RepositoryNotFoundException,
      ProjectRenamingFailedException {
    // To make sure source is a valid repository, we try opening it, as
    // trying to open an invalid repository throws exceptions.
    Repository sourceRepository = openRepository(source);

    // As opening did not bail out, source denotes a valid repository
    sourceRepository.close();
    File sourceDirectory = FileKey.resolve(gitDirOf(source), FS.DETECTED);

    if (isUnreasonableName(destination)) {
      throw new ProjectRenamingFailedException("invalid name: " +
          destination);
    }
    File destinationDirectory = new File(getBasePath(),
        destination + Constants.DOT_GIT);

    // Assure that we do not overwrite data during renaming
    if (destinationDirectory.exists()) {
      throw new ProjectRenamingFailedException(destinationDirectory
          + " already exists");
    }

    // Creating parent for destination git repository if necessary
    if (! destinationDirectory.getParentFile().exists()) {
      if (! destinationDirectory.getParentFile().mkdirs()) {
        throw new ProjectRenamingFailedException("Creating required parent "
            + "directories for " + destinationDirectory + " failed");
      }
    }

    final Lock lock1;
    final Lock lock2;
    final int compare;
    try {
      compare = sourceDirectory.getCanonicalPath().compareTo(
          destinationDirectory.getCanonicalPath());
    } catch (IOException e) {
      throw new ProjectRenamingFailedException("Could not get canonical file "
          + "name of source or destination repository");
    }
    if (compare == 0) {
      // This case should typically not happen. However, it might happen, if
      // somebody removes the source repository from the file system between
      // our guarding check for source, and our guarding check for destination.
      // Then, we'd (depending on the use lock implementation) deadlock
      // ourselves by trying to acquire the same lock twice.
      //
      // To guard against such problems, we could lock earlier, but then we'd
      // have to wait for locks before detecting simple problems (e.g.: source
      // does not exist), which would cause unnecessary delays for users.
      //
      // Hence, we treat this case nonetheless, and throw the problem back to
      // the user.
      throw new ProjectRenamingFailedException("Source and destination "
          + "repository are the same file");
    } else if (compare < 0) {
      lock1 = getWriteLock(source);
      lock2 = getWriteLock(destination);
    } else {
      lock1 = getWriteLock(destination);
      lock2 = getWriteLock(source);
    }

    lock1.lock();
    try {
      lock2.lock();
      try {
        // Moving the repository
        if (! sourceDirectory.renameTo(destinationDirectory)) {
          throw new ProjectRenamingFailedException("Moving git repository to "
              + destinationDirectory + " failed");
        }
      } finally {
        lock2.unlock();
      }
    } finally {
      lock1.unlock();
    }

    // Update the local names cache
    namesUpdateLock.lock();
    try {
      SortedSet<Project.NameKey> n = new TreeSet<Project.NameKey>(names);
      n.remove(source);
      n.add(destination);
      names = Collections.unmodifiableSortedSet(n);
    } finally {
      namesUpdateLock.unlock();
    }

    // Cleaning up source's parents if they are empty
    recursiveDeleteEmptyParent(sourceDirectory.getParentFile(), getBasePath());
  }

  /**
   * Deletes an empty directory along with subsequently empty parent
   * directories.
   * <p>
   * This method is used to clean up a repository's source directory after a
   * repository move.
   * <p>
   * Deletion stops if either {@code file} does not refer to an empty
   * directory, or if {@code file} equals {@code until}.
   * <p>
   * This is used when we
   * have a tree structure such as a/b/c/d.git and a/b/e.git - if we delete
   * a/b/c/d.git, we no longer need a/b/c/.
   *
   * @param file empty directory to start deletion at.
   * @param until boundary. If parent traversal arrives at {@code until},
   *        traversal is stopped. {@code until} does not get removed.
   */
  private void recursiveDeleteEmptyParent(File file, File until) {
    while (! file.equals(until) && file.isDirectory()
        && file.listFiles().length == 0) {
      File parent = file.getParentFile();
      file.delete();
      file = parent;
    }
  }

  /**
   * Assures that closing a project also releases the associated lock.
   * <p>
   * This class is used by {@code wrapRepository()} to intercept calls to the
   * {@code close()} method of a project wrapper.
   */
  private class LockReleaseMethodInterceptor implements $MethodInterceptor {
    Repository wrapped;
    Lock lock;

    /**
     * @param wrapped the repository that should get wrapped.
     * @param lock the lock held for {@code wrapped}. This lock has to be
     *        locked already.
     */
    private LockReleaseMethodInterceptor(Repository wrapped, Lock lock) {
      this.wrapped = wrapped;
      this.lock = lock;
    }

    /**
     * Invokes the requested method on the wrapped object, while closing locks
     * upon need.
     * <p>
     * Upon {@code close}, {@lock} gets unlocked, before delegating to
     * {@wrapped}.
     */
    @Override
    public Object intercept(Object proxy, Method method, Object[] args, $MethodProxy methodProxy) throws Throwable {
      if ("close".equals(method.getName())) {
        if (lock != null) {
          lock.unlock();
          lock = null;
        }
      }
      return methodProxy.invoke(wrapped, args);
    }
  }

  /**
   * Wraps a repository to release the associated lock upon project closing.
   *
   * @param wrapped the object that should get wrapped
   * @param lock the lock to release upon closing the repository
   * @return the wrapped repository. Any method invoked on this repository is
   *        passed straight to {@code wrapped}. Only {@code close()} releases
   *        {@code lock}, before delegating to {@code wrapped}.
   */
  private Repository wrapRepository(Repository wrapped, Lock lock) {
    // In order to release locks associated to projects, we could try to modify
    // all calls to {@code project.close()} to also close the locks. But as we
    // cannot rely on plugins adhering to this principle, this approach is
    // futile. Hence, we have to apply automatic means to unlock the locks.
    //
    // We could write a wrapper implementing Project by hand. This is tedious
    // and would require updates for each and every update of Project. And we
    // might not even catch such cases, as Project is an abstract class.
    //
    // To generate the wrapper automatically, we cannot use Java's native
    // proxying, as Project is not an interface.
    //
    // CGLIB allows to proxy abstract classes, sadly enough it is not yet a
    // dependency of gerrit. However, com.google.inject comes with an internal
    // copy of the required CGLIB parts. We therefore reuse those CGLIB parts
    // instead of adding a further dependency on it.

    $Enhancer enhancer = new $Enhancer();
    enhancer.setSuperclass(Repository.class);
    enhancer.setCallback(new LockReleaseMethodInterceptor(wrapped, lock));

    // Unfortunately, Repository does not come with a parameterless
    // constructor, but requires a BaseRepositoryBuilder. Since this is not
    // used by our proxy anyways, we can stub in a plain
    // FileRepositoryBuilder.
    return (Repository) enhancer.create(
        new Class[] {BaseRepositoryBuilder.class},
        new Object[] {new FileRepositoryBuilder()});
  }

  /**
   * Obtains the lock to lock a project for writing.
   * <p>
   * In general, you want to get a read lock. Even if you modify the
   * Repository (add commits, etc), a read lock is sufficient. Only grab a
   * write lock when trying to modify the repository directly through the file
   * system (e.g.: renaming the repository).
   *
   * @param name the name of the project to get the lock for.
   * @return the write lock for {@code}. This lock is not yet locked. The
   *        caller is responsible for (un)locking.
   */
  private Lock getWriteLock(Project.NameKey name) {
    return getLock(name, true);
  }

  /**
   * Obtains the lock to lock a project for reading.
   *
   * @param name the name of the project to get the lock for.
   * @return the read lock for {@code}. This lock is not yet locked. The
   *        caller is responsible for (un)locking.
   */
  private Lock getReadLock(Project.NameKey name) {
    return getLock(name, false);
  }

  /**
   * Gets either a read or a write lock for a project.
   * <p>
   * Rather use the {@code getReadLock()@}, or {@code getWriteLock()@} methods,
   * where possible.
   *
   * @param name the name of the project to get the lock for.
   * @param getWriteLock if true, obtain a write lock. Otherwise, obtain a
   *        read lock.
   * @return the requested lock for the project. This lock is not yet locked.
   *        The caller is responsible for (un)locking.
   */
  private Lock getLock(Project.NameKey name, boolean getWriteLock) {
    File repositoryFile = FileKey.resolve(gitDirOf(name), FS.DETECTED);
    if (repositoryFile == null) {
      repositoryFile = new File(basePath, name.get() + Constants.DOT_GIT_EXT);
    }
    try {
      repositoryFile = repositoryFile.getCanonicalFile();
    } catch (IOException e) {
      // Something went haywire while getting the canonical file name of
      // repositoryFile. This is not much of an issue, as repositoryFile
      // itself already contains a best effort value with which we can
      // continue. Nevertheless we log the incident.
      log.error("Could not get canonical name for " + repositoryFile);
    }

    ReadWriteLock readWriteLock = accessLocks.get(repositoryFile);
    if (readWriteLock == null ) {
      readWriteLock = new ReentrantReadWriteLock();
      accessLocks.put(repositoryFile, readWriteLock);
    }

    Lock lock = null;
    if (getWriteLock) {
      lock = readWriteLock.writeLock();
    } else {
      lock = readWriteLock.readLock();
    }

    return lock;
  }
}
