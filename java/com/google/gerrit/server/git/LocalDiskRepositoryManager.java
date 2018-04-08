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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.SortedSet;
import java.util.TreeSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.RepositoryCacheConfig;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages Git repositories stored on the local filesystem. */
@Singleton
public class LocalDiskRepositoryManager implements GitRepositoryManager {
  private static final Logger log = LoggerFactory.getLogger(LocalDiskRepositoryManager.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(LocalDiskRepositoryManager.Lifecycle.class);
    }
  }

  public static class Lifecycle implements LifecycleListener {
    private final Config serverConfig;

    @Inject
    Lifecycle(@GerritServerConfig Config cfg) {
      this.serverConfig = cfg;
    }

    @Override
    public void start() {
      RepositoryCacheConfig repoCacheCfg = new RepositoryCacheConfig();
      repoCacheCfg.fromConfig(serverConfig);
      repoCacheCfg.install();

      WindowCacheConfig cfg = new WindowCacheConfig();
      cfg.fromConfig(serverConfig);
      if (serverConfig.getString("core", null, "streamFileThreshold") == null) {
        long mx = Runtime.getRuntime().maxMemory();
        int limit =
            (int)
                Math.min(
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
        log.info(String.format("Defaulting core.streamFileThreshold to %s", desc));
        cfg.setStreamFileThreshold(limit);
      }
      cfg.install();
    }

    @Override
    public void stop() {}
  }

  private final Path basePath;

  @Inject
  LocalDiskRepositoryManager(SitePaths site, @GerritServerConfig Config cfg) {
    basePath = site.resolve(cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
  }

  /**
   * Return the basePath under which the specified project is stored.
   *
   * @param name the name of the project
   * @return base directory
   */
  public Path getBasePath(Project.NameKey name) {
    return basePath;
  }

  @Override
  public Repository openRepository(Project.NameKey name) throws RepositoryNotFoundException {
    return openRepository(getBasePath(name), name);
  }

  private Repository openRepository(Path path, Project.NameKey name)
      throws RepositoryNotFoundException {
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }
    FileKey loc = FileKey.lenient(path.resolve(name.get()).toFile(), FS.DETECTED);
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
      throws RepositoryNotFoundException, RepositoryCaseMismatchException, IOException {
    Path path = getBasePath(name);
    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    File dir = FileKey.resolve(path.resolve(name.get()).toFile(), FS.DETECTED);
    if (dir != null) {
      // Already exists on disk, use the repository we found.
      //
      Project.NameKey onDiskName = getProjectName(path, dir.getCanonicalFile().toPath());

      if (!onDiskName.equals(name)) {
        throw new RepositoryCaseMismatchException(name);
      }

      throw new IllegalStateException("Repository already exists: " + name);
    }

    // It doesn't exist under any of the standard permutations
    // of the repository name, so prefer the standard bare name.
    //
    String n = name.get() + Constants.DOT_GIT_EXT;
    FileKey loc = FileKey.exact(path.resolve(n).toFile(), FS.DETECTED);

    try {
      Repository db = RepositoryCache.open(loc, false);
      db.create(true /* bare */);

      StoredConfig config = db.getConfig();
      config.setBoolean(
          ConfigConstants.CONFIG_CORE_SECTION,
          null,
          ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES,
          true);
      config.save();

      // JGit only writes to the reflog for refs/meta/config if the log file
      // already exists.
      //
      File metaConfigLog = new File(db.getDirectory(), "logs/" + RefNames.REFS_CONFIG);
      if (!metaConfigLog.getParentFile().mkdirs() || !metaConfigLog.createNewFile()) {
        log.error(
            String.format(
                "Failed to create ref log for %s in repository %s", RefNames.REFS_CONFIG, name));
      }

      return db;
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot create repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  private boolean isUnreasonableName(Project.NameKey nameKey) {
    final String name = nameKey.get();

    return name.length() == 0 // no empty paths
        || name.charAt(name.length() - 1) == '/' // no suffix
        || name.indexOf('\\') >= 0 // no windows/dos style paths
        || name.charAt(0) == '/' // no absolute paths
        || new File(name).isAbsolute() // no absolute paths
        || name.startsWith("../") // no "l../etc/passwd"
        || name.contains("/../") // no "foo/../etc/passwd"
        || name.contains("/./") // "foo/./foo" is insane to ask
        || name.contains("//") // windows UNC path can be "//..."
        || name.contains(".git/") // no path segments that end with '.git' as "foo.git/bar"
        || name.contains("?") // common unix wildcard
        || name.contains("%") // wildcard or string parameter
        || name.contains("*") // wildcard
        || name.contains(":") // Could be used for absolute paths in windows?
        || name.contains("<") // redirect input
        || name.contains(">") // redirect output
        || name.contains("|") // pipe
        || name.contains("$") // dollar sign
        || name.contains("\r") // carriage return
        || name.contains("/+") // delimiter in /changes/
        || name.contains("~"); // delimiter in /changes/
  }

  @Override
  public SortedSet<Project.NameKey> list() {
    ProjectVisitor visitor = new ProjectVisitor(basePath);
    scanProjects(visitor);
    return Collections.unmodifiableSortedSet(visitor.found);
  }

  protected void scanProjects(ProjectVisitor visitor) {
    try {
      Files.walkFileTree(
          visitor.startFolder,
          EnumSet.of(FileVisitOption.FOLLOW_LINKS),
          Integer.MAX_VALUE,
          visitor);
    } catch (IOException e) {
      log.error("Error walking repository tree " + visitor.startFolder.toAbsolutePath(), e);
    }
  }

  private static Project.NameKey getProjectName(Path startFolder, Path p) {
    String projectName = startFolder.relativize(p).toString();
    if (File.separatorChar != '/') {
      projectName = projectName.replace(File.separatorChar, '/');
    }
    if (projectName.endsWith(Constants.DOT_GIT_EXT)) {
      int newLen = projectName.length() - Constants.DOT_GIT_EXT.length();
      projectName = projectName.substring(0, newLen);
    }
    return new Project.NameKey(projectName);
  }

  protected class ProjectVisitor extends SimpleFileVisitor<Path> {
    private final SortedSet<Project.NameKey> found = new TreeSet<>();
    private Path startFolder;

    public ProjectVisitor(Path startFolder) {
      setStartFolder(startFolder);
    }

    public void setStartFolder(Path startFolder) {
      this.startFolder = startFolder;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      if (!dir.equals(startFolder) && isRepo(dir)) {
        addProject(dir);
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException e) {
      log.warn(e.getMessage());
      return FileVisitResult.CONTINUE;
    }

    private boolean isRepo(Path p) {
      String name = p.getFileName().toString();
      return !name.equals(Constants.DOT_GIT)
          && (name.endsWith(Constants.DOT_GIT_EXT)
              || FileKey.isGitRepository(p.toFile(), FS.DETECTED));
    }

    private void addProject(Path p) {
      Project.NameKey nameKey = getProjectName(startFolder, p);
      if (getBasePath(nameKey).equals(startFolder)) {
        if (isUnreasonableName(nameKey)) {
          log.warn("Ignoring unreasonably named repository " + p.toAbsolutePath());
        } else {
          found.add(nameKey);
        }
      }
    }
  }
}
