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
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.common.ProjectUtil;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
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
  private final GitRepositoryManager repoManager;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final IdentifiedUser currentUser;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final Project.NameKey sourceProjectNameKey;
  private final Project.NameKey destinationProjectNameKey;

  @Inject
  RenameProject(ReviewDb db, ProjectCache projectCache, SitePaths site,
      @GerritServerConfig Config cfg, DynamicMap<Cache<?, ?>> cacheMap,
      GitRepositoryManager repoManager, IdentifiedUser identifiedUser,
      MetaDataUpdate.Server metaDataUpdateFactory,
      @Assisted("sourceName") String sourceName,
      @Assisted("destinationName") String destinationName) {
    this.db = db;
    this.cacheMap = cacheMap;
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.currentUser = identifiedUser;
    this.metaDataUpdateFactory = metaDataUpdateFactory;

    this.sourceProjectNameKey = new Project.NameKey(sourceName);
    this.destinationProjectNameKey = new Project.NameKey(
        ProjectUtil.stripGitSuffix(destinationName));
  }

  /**
   * Renames a project
   *
   * This method takes care of updating the database, filesystem, and parent
   * repositories during rename. Projects that are subscribed by other
   * projects do not allow to be renamed and will throw an exception.
   *
   * @throws NameAlreadyUsedException
   * @throws NoSuchProjectException
   * @throws PermissionDeniedException
   * @throws ProjectRenamingFailedException
   * @throws RepositoryNotFoundException
   */
  public String renameProject() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException, RepositoryNotFoundException  {
    final ProjectState sourceProjectState;
    final Project sourceProject;

    if (!currentUser.getCapabilities().canAdministrateServer()) {
      throw new PermissionDeniedException(String.format(
          "%s does not have \"Rename Project\" capability.",
          currentUser.getUserName()));
    }

    sourceProjectState = projectCache.get(sourceProjectNameKey);
    if (sourceProjectState == null) {
      throw new NoSuchProjectException(sourceProjectNameKey);
    }
    if (sourceProjectState.isAllProjects()) {
      throw new ProjectRenamingFailedException("Renaming \""
        + AllProjectsNameProvider.DEFAULT + "\" is prohibited. "
        + "See gerrit.allProjects setting." );
    }
    sourceProject = sourceProjectState.getProject();

    if (projectCache.get(destinationProjectNameKey) != null) {
      throw new NameAlreadyUsedException(destinationProjectNameKey.toString());
    }

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
    final Connection conn = ((JdbcSchema) db).getConnection();

    // Actual renaming process is split into separate steps that are chained
    // together. To rename a project we run down this chain of steps,
    // carrying out each of the steps. As soon as some problem occurs we stop
    // and move back along the chain, informing every step to undo itself. If
    // no problem occurs, and we reach the end of the chain, project renaming
    // is done.
    ProjectRenamingStep commandChain = null;
    commandChain = new DatabaseCommitStep(conn);
    commandChain = new RepositoryRenameStep(commandChain);
    commandChain = new ChildrenRenameStep(commandChain);
    commandChain = new DatabaseRenameStep(commandChain, conn);
    commandChain = new AdjustAutoCommitStep(commandChain, conn);
    try {
      commandChain.run();
    } catch (NameAlreadyUsedException e) {
      throw e;
    } catch (NoSuchProjectException e) {
      throw e;
    } catch (PermissionDeniedException e) {
      throw e;
    } catch (ProjectRenamingFailedException e) {
      throw e;
    } catch (RepositoryNotFoundException e) {
      throw e;
    } catch (Throwable e) {
      throw new ProjectRenamingFailedException("Renaming failed for unknown "
          + "reason", e);
    }
    purgeCaches(sourceProject);
    return destinationProjectNameKey.toString();
  }

  /**
   * Purges caches relevant to renaming a project
   *
   * @param sourceProject the project for the renaming's source
   */
  private void purgeCaches(Project sourceProject) {
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
  }

  /**
   * Models a step in renaming a project.
   *
   * The step may have another step chained to it, which is automatically run,
   * after this step has been run.
   */
  private class ProjectRenamingStep {
    /**
     * The step that is chained to this step.
     */
    protected ProjectRenamingStep chainedStep;

    public ProjectRenamingStep(ProjectRenamingStep chainedStep) {
      this.chainedStep = chainedStep;
    }

    /**
     * Run this step.
     *
     * This method is run upon {@code run@} and does only deal with the current
     * step. It should need to deal with chained steps. Chained steps are dealt
     * with automatically through {@code run()} as well.
     * @throws Throwable
     */
    protected void doRun() throws Throwable {}

    /**
     * Finalize a run of this step.
     *
     * This method is run at the end of {@code run()}, regardless of whether or
     * not a further step is attached or some error occurred.
     *
     * If an error occurs within this method, the error is logged, but it does
     * not cause a roll back of the chained step, or the steps this step is
     * chained to.
     * @throws Throwable
     */
    protected void finalize() throws Throwable {}

    /**
     * Recover from a failure of running the chained step.
     *
     * @param e The error thrown by running the chained step.
     * @throws Throwable
     */
    protected void onChainedStepFailure(Throwable e) throws Throwable {}

    /**
     * Recover from a failure during running the (chained) step.
     *
     * @param e The error thrown by running the (chained) step.
     * @throws Throwable
     */
    protected void onFailure(Throwable e) throws Throwable {}

    /**
     * Log a thrown exception, when already in a catch block.
     *
     * @param e The error that is to be logged
     */
    private void logThrowable(Throwable e) {
      RenameProject.log.error("Error during recovery from project renaming "
          + "exception", e);
    }

    /**
     * Runs the step along with a chained step.
     *
     * First, this step itself is run {@code doRun}. If an error occurs,
     * {@code onFailure} is invoked. If no error occurs, the chained step is
     * run. If running the chained step caused errors,
     * {@code onChainedStepFailure()}, and afterwards {@code onFailure()} is
     * invoked. Finally, in all cases, {@code finalize()} is run.
     *
     * @throws Throwable The mail error stemming from either {@doRun()} or
     *         from running the chained step.
     */
    public final void run() throws Throwable {
      try {
        doRun();
        if (chainedStep != null) {
          try {
            chainedStep.run();
          } catch (Throwable e) {
            try {
              onChainedStepFailure(e);
            } catch (Throwable e2) {
              logThrowable(e2);
            }
            throw e;
          }
        }
      } catch (Throwable e) {
        try {
          onFailure(e);
        } catch (Throwable e2) {
          logThrowable(e2);
        }
        throw e;
      } finally {
        try {
          finalize();
        } catch (Throwable e2) {
          logThrowable(e2);
        }
      }
    }
  }

  /**
   * Enforces turned off auto commit for chained step.
   *
   * After this (and an eventual chained) step has been run, auto commit is
   * reset to the value it had before running this step. Also if an error
   * occurs we try to reset auto commit.
   */
  private class AdjustAutoCommitStep extends ProjectRenamingStep {
    private Boolean oldAutoCommit;
    private final Connection conn;

    public AdjustAutoCommitStep(ProjectRenamingStep chainedStep,
        Connection conn) {
      super(chainedStep);
      this.conn = conn;
      this.oldAutoCommit = null;
    }

    @Override
    protected void doRun() throws ProjectRenamingFailedException {
      try {
        oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
      } catch (SQLException e) {
        throw new ProjectRenamingFailedException("Could not force turning "
            + "autocommit off", e);
      }
    }

    @Override
    protected void finalize() throws ProjectRenamingFailedException {
      if (oldAutoCommit != null) {
        try {
          conn.setAutoCommit(oldAutoCommit);
        } catch (SQLException e) {
          throw new ProjectRenamingFailedException("Could not reset autocommit "
              + "mode", e);
        }
      }
    }
  }

  /**
   * Updates rows in the database to reflect the project renaming.
   */
  private class DatabaseRenameStep extends ProjectRenamingStep {
    private final Connection conn;

    public DatabaseRenameStep(ProjectRenamingStep chainedStep,
        Connection conn) {
      super(chainedStep);
      this.conn = conn;
    }

    private void renameChanges() throws ProjectRenamingFailedException {
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

    private void renameWatches() throws ProjectRenamingFailedException {
      try {
        List<AccountProjectWatch> accountProjectWatches =
            db.accountProjectWatches().byProject(sourceProjectNameKey)
            .toList();
        // As the project name is part of the watch's key, we cannot just
        // update the watch, but have delete the watch and reinsert it after
        // updating the project name.
        db.accountProjectWatches().delete(accountProjectWatches);
        for(AccountProjectWatch accountProjectWatch: accountProjectWatches) {
          accountProjectWatch.setProjectNameKey(destinationProjectNameKey);
        }
        db.accountProjectWatches().insert(accountProjectWatches);
      } catch (OrmException e) {
        throw new ProjectRenamingFailedException("Could not update project "
            + "watches", e);
      }
    }

    @Override
    protected void doRun() throws ProjectRenamingFailedException {
      renameChanges();
      renameWatches();
    }

    @Override
    protected void onFailure(Throwable e) {
      try {
        conn.rollback();
      } catch (SQLException e2) {
        log.error("Rollback of project renaming failed", e2);
      }
    }
  }

  /**
   * Updates inheritFrom for affected projects
   */
  private class ChildrenRenameStep extends ProjectRenamingStep {
    private Collection<Project.NameKey> updatedProjectNameKeys;

    public ChildrenRenameStep(ProjectRenamingStep chainedStep) {
      super(chainedStep);
      this.updatedProjectNameKeys = new HashSet<Project.NameKey>();
    }

    private void setParent(Project.NameKey childProjectNameKey,
        Project.NameKey newParentProjectNameKey,
        String message) throws IOException, ConfigInvalidException {
      MetaDataUpdate md = metaDataUpdateFactory.create(childProjectNameKey);
      Project childProject = null;
      try {
        ProjectConfig config = ProjectConfig.read(md);
        childProject = config.getProject();
        childProject.setParentName(newParentProjectNameKey);
        md.setMessage(message);
        md.setAuthor(currentUser);
        config.commit(md);
      } finally {
        md.close();
        if (childProject != null) {
          projectCache.evict(childProject);
        }
      }
    }

    @Override
    protected void doRun() throws ProjectRenamingFailedException {
      Project.NameKey rootExceptionProjectNameKey = null;
      try {
        for(Project.NameKey childProjectNameKey: projectCache.all()) {
          final ProjectState childProjectState =
              projectCache.get(childProjectNameKey);
          if (childProjectState != null) {
            final Project childProject = childProjectState.getProject();
            Project.NameKey childParentNameKey = childProject.getParent();
            if (childParentNameKey != null &&
                childParentNameKey.equals(sourceProjectNameKey)) {
              try {
                setParent(childProjectNameKey, destinationProjectNameKey,
                    "Follow parent rename to " + destinationProjectNameKey);
                updatedProjectNameKeys.add(childProjectNameKey);
              } catch (Exception e2) {
                rootExceptionProjectNameKey = childProjectNameKey;
                throw e2;
              }
            }
          }
        }
      } catch (Throwable e) {
        // Some project did not allow to update the parent.
        // We try to rollback each of the children for which we renamed
        // the parent.

        String message = null;
        if (rootExceptionProjectNameKey == null) {
          message = "Could not rename parents for projects";
        } else {
          message = "Could not rename parent for project "
              + rootExceptionProjectNameKey;
        }
        throw new ProjectRenamingFailedException(message, e);
      }
    }

    @Override
    protected void onFailure(Throwable e)
        throws ProjectRenamingFailedException {
      for(Project.NameKey childProjectNameKey: updatedProjectNameKeys) {
        try {
          final ProjectState childProjectState = projectCache
              .get(childProjectNameKey);
          if (childProjectState == null) {
            throw new NoSuchProjectException(childProjectNameKey);
          }
          // Instead of resetting the git repository, we attempt a rollback via
          // a further git commit, so we catch cases where users checked
          // out/updated the config between our initial renaming and this
          // rollback.
          setParent(childProjectNameKey, sourceProjectNameKey,
              "Rollback of parent renaming");
        } catch (Throwable e2) {
          log.error("Rollback failed for project " + childProjectNameKey
              + ". Please set the project's parent to " + sourceProjectNameKey,
              e2);
        }
      }
    }
  }

  /**
   * Moves the git repository around in the file system
   */
  private class RepositoryRenameStep extends ProjectRenamingStep {
    public RepositoryRenameStep(ProjectRenamingStep chainedStep) {
      super(chainedStep);
    }

    @Override
    protected void doRun() throws RepositoryNotFoundException,
        ProjectRenamingFailedException {
      repoManager.renameRepository(sourceProjectNameKey,
          destinationProjectNameKey);
    }

    @Override
    protected void onChainedStepFailure(Throwable e) {
      try {
        repoManager.renameRepository(destinationProjectNameKey,
          sourceProjectNameKey);
      } catch (Throwable e2) {
        log.error("Rollback failed to move repository of "
            + destinationProjectNameKey + " back to " + sourceProjectNameKey,
            e2);
      }
    }
  }

  /**
   * Commits the changes in the database.
   */
  private class DatabaseCommitStep extends ProjectRenamingStep {
    private final Connection conn;

    public DatabaseCommitStep(Connection conn) {
      super(null);
      this.conn = conn;
    }

    @Override
    protected void doRun() throws ProjectRenamingFailedException {
      try {
        conn.commit();
      } catch (SQLException e) {
        throw new ProjectRenamingFailedException("Could not commit project "
            + "renaming", e);
      }
    }
  }
}
