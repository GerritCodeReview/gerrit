// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.common.cache.Cache;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Common class that holds the code to rename projects */
public class RenameProject {
  private static final Logger log = LoggerFactory
      .getLogger(RenameProject.class);

  public interface Factory {
    RenameProject create(@Assisted("sourceName") String sourceName,
        @Assisted("destinationName") String destinationName);
  }

  private final ReviewDb db;
  private final ProjectCache projectCache;
  private final String sourceName;
  private final String destinationName;
  private final GitRepositoryManager repoManager;
  private final File gitDir;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final IdentifiedUser currentUser;

  @Inject
  RenameProject(ReviewDb db, ProjectCache projectCache, SitePaths site,
      @GerritServerConfig Config cfg, DynamicMap<Cache<?, ?>> cacheMap,
      GitRepositoryManager repoManager, IdentifiedUser identifiedUser,
      @Assisted("sourceName") String sourceName,
      @Assisted("destinationName") String destinationName) {
    gitDir = site.resolve(cfg.getString("gerrit", null, "basePath"));
    this.db = db;
    this.cacheMap = cacheMap;
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.currentUser = identifiedUser;
    this.sourceName = sourceName;
    this.destinationName = destinationName;
  }

  /**
   * Renames a project
   *
   * @throws ProjectRenamingFailedException
   */
  public String renameProject() throws ProjectRenamingFailedException {
    final Project.NameKey sourceProjectNameKey;
    final ProjectState sourceProjectState;
    final Project sourceProject;
    final Repository sourceRepository;
    final String destinationProjectName;
    final String destinationRepositoryName;
    final Project.NameKey destinationProjectNameKey;

    if (!currentUser.getCapabilities().canRenameProject()) {
      throw new ProjectRenamingFailedException(String.format(
          "%s does not have \"Rename Project\" capability.",
          currentUser.getUserName()));
    }

    // -- Preparing variables for project renaming's source -------------------

    sourceProjectNameKey = new Project.NameKey(sourceName);
    sourceProjectState = projectCache.get(sourceProjectNameKey);
    if (sourceProjectState == null) {
      throw new ProjectRenamingFailedException("Could not obtain state of "
          + "project");
    }
    if (sourceProjectState.isAllProjects()) {
      throw new ProjectRenamingFailedException("Renaming \""
        + AllProjectsNameProvider.DEFAULT + "\" is prohibited. "
        + "See gerrit.allProjects setting." );
    }
    sourceProject = sourceProjectState.getProject();
    if (sourceProject == null) {
      throw new ProjectRenamingFailedException("Could not obtain project");
    }
    try {
      sourceRepository = repoManager.openRepository(sourceProjectNameKey);
    } catch (RepositoryNotFoundException e) {
      throw new ProjectRenamingFailedException("No repository was found for "
          + "key: " + sourceProjectNameKey, e);
    } catch (IOException e) {
      throw new ProjectRenamingFailedException("Could not read " +
          "key " + sourceProjectNameKey + "as repository", e);
    }
    if (sourceRepository == null) {
      throw new ProjectRenamingFailedException("Could not obtain source "
          + "project");
    }
    final File sourceDirectory = sourceRepository.getDirectory();

    // -- Preparing variables for project renaming's destination --------------

    if (destinationName.endsWith(Constants.DOT_GIT_EXT)) {
      destinationProjectName = destinationName.substring(0,
          destinationName.length() - Constants.DOT_GIT_EXT.length());
      destinationRepositoryName = destinationName;
    } else {
      destinationProjectName = destinationName;
      destinationRepositoryName = destinationName + Constants.DOT_GIT_EXT;
    }
    destinationProjectNameKey = new Project.NameKey(destinationProjectName);
    final File destinationDirectory = new File(gitDir,
        destinationRepositoryName);


    // -- Precursory checks that do not modifying file system or database -----
    assertDatabaseAllowsMove(sourceProjectNameKey);
    assertFilesystemAllowsMove(destinationDirectory);

    // -- Environment is fine, so we can now start moving the project ---------

    final boolean oldAutoCommit;
    final Connection conn = ((JdbcSchema) db).getConnection();
    try {
      oldAutoCommit = conn.getAutoCommit();
      conn.setAutoCommit(false);
    } catch (SQLException e) {
      throw new ProjectRenamingFailedException("Could not force turning "
          + "autocommit off", e);
    }

    try {
      // Actual moving around and updating of data ----------------------------
      doDatabaseMove(sourceProjectNameKey, destinationProjectNameKey);
      doFilesystemMove(sourceDirectory, destinationDirectory);

      // Cleaning up ----------------------------------------------------------
      cleanupFileSystemMove(sourceDirectory);

      try {
        conn.commit();
      } catch (SQLException e) {
        throw new ProjectRenamingFailedException("Could not commit project "
            + "renaming", e);
      }
    } catch (ProjectRenamingFailedException e) {
      System.err.println(e.getMessage());
      try {
        conn.rollback();
      } catch (SQLException eRollback) {
        log.error("Rollback of project renaming failed");
      }
      throw e;
    } finally {
      try {
        conn.setAutoCommit(oldAutoCommit);
      } catch (SQLException e) {
        throw new ProjectRenamingFailedException("Could not reset autocommit "
            + "mode", e);
      }
    }
    purgeCache(sourceProject, sourceRepository);

    return destinationProjectName;
  }

  /**
   * checks for obvious file system problems before starting to rename a
   * project
   *
   * @param destinationDirectory the renaming's destination repository location
   * @throws ProjectRenamingFailedException
   */
  private void assertFilesystemAllowsMove(File destinationDirectory)
      throws ProjectRenamingFailedException {
    // Check against moving projects out of gerrit.base path via '..' etc.
    // Note that gitDir has the separator appended in this check, so we also
    // guard against moving
    //   /some/path/gitDir/source
    // to
    //   /some/path/gitDirEvil/destination
    // .
    try {
      if (! destinationDirectory.getCanonicalPath().startsWith(
          gitDir.getCanonicalPath()+File.separator)) {
        throw new ProjectRenamingFailedException("Can only rename projects "
            + " within gerrit.basepath");
        }
    } catch (IOException e) {
      throw new ProjectRenamingFailedException("Could not obtain canonical "
          + "path for rename destination", e);
    }

    // Assure that we do not overwrite data during renaming
    if (destinationDirectory.exists()) {
      throw new ProjectRenamingFailedException(destinationDirectory + " already exists");
    }
  }

  /**
   * Performs the filesystem part of renaming a project
   *
   * @param sourceDirectory the renaming's source repository location
   * @param destinationDirectory the renaming's destination repository location
   * @throws ProjectRenamingFailedException
   */
  private void doFilesystemMove(File sourceDirectory,
      File destinationDirectory) throws ProjectRenamingFailedException {

    // Creating parent for destination git repository if necessary
    if (! destinationDirectory.getParentFile().exists()) {
      if (! destinationDirectory.getParentFile().mkdirs()) {
        throw new ProjectRenamingFailedException("Creating required parent "
            + "directories for " + destinationDirectory + " failed");
      }
    }

    // Moving the git repository
    if (! sourceDirectory.renameTo(destinationDirectory)) {
      throw new ProjectRenamingFailedException("Moving git repository to "
          + destinationDirectory + " failed");
    }
  }

  /**
   * Cleans up the file system after renaming a project
   *
   * @param sourceDirectory the renaming's source repository location
   */
  private void cleanupFileSystemMove(File sourceDirectory) {
    recursiveDeleteEmptyParent(sourceDirectory.getParentFile(), gitDir);
  }

  /**
   * Deletes an empty directory along with subsequently empty parent
   * directories.
   *
   * Deletion stops if either <code>file</code> does not refer to an empty
   * directory, or if <code>file</code> equals <code>until</code>.
   *
   * This is used when we
   * have a tree structure such as a/b/c/d.git and a/b/e.git - if we delete
   * a/b/c/d.git, we no longer need a/b/c/.
   *
   * @param file empty directory to start deletion at
   * @param until boundary. If parent traversal arrives at
   * <code>until</code>, traversal is stopped. <code>until</code> does not
   * get removed.
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
   * Checks for database settings that forbid renaming a project
   *
   * @param sourceProject the project for the renaming's source
   * @throws ProjectRenamingFailedException
   */
  private void assertDatabaseAllowsMove(Project.NameKey sourceProjectNameKey)
      throws ProjectRenamingFailedException {
    try {
      if (db.submoduleSubscriptions().bySubmoduleProject(sourceProjectNameKey)
          .iterator().hasNext()) {
        throw new ProjectRenamingFailedException("Cannot rename project "
            + sourceProjectNameKey + ", as it is a subscribed submodule." );
      }
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not fetch "
          + "subscriptions", e);
    }
  }

  /**
   * performs the database part of renaming a project
   *
   * @param sourceProjectNameKey the project name key for the renaming's source
   * @param destinationProjectNameKey the project name key for the renaming's
   *   destination
   * @throws ProjectRenamingFailedException
   */
  private void doDatabaseMove(Project.NameKey sourceProjectNameKey,
      Project.NameKey destinationProjectNameKey)
      throws ProjectRenamingFailedException {

    // -- Updating project watches ---------------------------------------
    try {
      List<AccountProjectWatch> accountProjectWatches =
          db.accountProjectWatches().byProject(sourceProjectNameKey).toList();
      // As the project name is part of the watch's key, we cannot just update
      // the watch, but have delete the watch and reinsert it after updating
      // the project name.
      db.accountProjectWatches().delete(accountProjectWatches);
      for(AccountProjectWatch accountProjectWatch: accountProjectWatches) {
        accountProjectWatch.setProjectNameKey(destinationProjectNameKey);
      }
      db.accountProjectWatches().insert(accountProjectWatches);
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not update project "
          + "watches", e);
    }

    // -- Updating Changes -----------------------------------------------
    try {
      List<Change> changes = db.changes().byProject(sourceProjectNameKey)
          .toList();
      for(Change change: changes) {
        change.setProject(destinationProjectNameKey);
      }
      db.changes().update(changes);
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not update project "
          + "changes", e);
    }
  }

  /**
   * Purges caches relevant to renaming a project
   *
   * <code>sourceRepository</code> gets closed within this function.
   *
   * @param sourceProject the project for the renaming's source
   * @param sourceRepository the repository for the renaming's source
   */
  private void purgeCache(Project sourceProject, Repository sourceRepository) {
    Set<String> cachesToClear = new HashSet<String>(){
      private static final long serialVersionUID = 1L;
      {
        // The "projects" cache does not get added, as we can easily
        // invalidate the single project that needs to be invalidated.
        // (See below)
        add("adv_bases");
        add("changes");
        add("git_tags");
        add("project_list");
      }
    };
    for (Map.Entry<String, Provider<Cache<?, ?>>> entry :
      cacheMap.byPlugin("gerrit").entrySet()) {
      if (cachesToClear.contains(entry.getKey())) {
        try {
          entry.getValue().get().invalidateAll();
        } catch (Throwable err) {
          log.error("cannot flush cache \"" + entry.getKey() + "\": " + err);
        }
      }
    }
    projectCache.remove(sourceProject);
    sourceRepository.close();
    RepositoryCache.close(sourceRepository);
  }
}
