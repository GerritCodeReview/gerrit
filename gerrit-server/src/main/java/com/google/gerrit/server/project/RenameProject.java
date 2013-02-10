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
// limitations under the License.

package com.google.gerrit.server.project;

import com.google.common.cache.Cache;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.common.ProjectUtil;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.project.renaming.Task;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
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
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final IdentifiedUser currentUser;
  private final Project.NameKey source;
  private final Project.NameKey destination;
  private final DynamicSet<Task.Factory> taskFactories;

  @Inject
  RenameProject(ReviewDb db, ProjectCache projectCache,
      DynamicMap<Cache<?, ?>> cacheMap, IdentifiedUser identifiedUser,
      DynamicSet<Task.Factory> taskFactories,
      @Assisted("sourceName") String sourceName,
      @Assisted("destinationName") String destinationName) {
    this.db = db;
    this.cacheMap = cacheMap;
    this.projectCache = projectCache;
    this.currentUser = identifiedUser;
    this.taskFactories = taskFactories;

    this.source = new Project.NameKey(sourceName);
    this.destination = new Project.NameKey(
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
   */
  public String renameProject() throws NameAlreadyUsedException,
      NoSuchProjectException, PermissionDeniedException,
      ProjectRenamingFailedException {
    final ProjectState sourceProjectState;
    final Project sourceProject;

    if (!currentUser.getCapabilities().canAdministrateServer()) {
      throw new PermissionDeniedException(String.format(
          "%s does not have \"Rename Project\" capability.",
          currentUser.getUserName()));
    }

    sourceProjectState = projectCache.get(source);
    if (sourceProjectState == null) {
      throw new NoSuchProjectException(source);
    }
    if (sourceProjectState.isAllProjects()) {
      throw new ProjectRenamingFailedException("Renaming \""
        + AllProjectsNameProvider.DEFAULT + "\" is prohibited. "
        + "See gerrit.allProjects setting." );
    }
    sourceProject = sourceProjectState.getProject();

    if (projectCache.get(destination) != null) {
      throw new NameAlreadyUsedException(destination.toString());
    }

    try {
      if (db.submoduleSubscriptions().bySubmoduleProject(source).iterator()
          .hasNext()) {
        throw new ProjectRenamingFailedException("Cannot rename project "
            + source + ", as it is a subscribed submodule." );
      }
      if (db.submoduleSubscriptions().bySuperProjectProject(source).iterator()
          .hasNext()) {
        throw new ProjectRenamingFailedException("Cannot rename project "
            + source + ", as it has submodules subscribed." );
      }
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not fetch "
          + "subscriptions", e);
    }

    // Running the subtasks
    Deque<Task> carriedOutTasks = new ArrayDeque<Task>();
    try {
      for (Task.Factory taskFactory : taskFactories) {
        Task task = taskFactory.create(source, destination);
        task.carryOut();
        carriedOutTasks.push(task);
      }
    } catch (Throwable e) {
      // Rolling back already carried out steps
      while (!carriedOutTasks.isEmpty()) {
        Task carriedOutTask = carriedOutTasks.pop();
        try {
          carriedOutTask.rollback();
        } catch (Throwable e2) {
          log.error("Rollback task for project renaming from " + source
              + " to " + destination + " failed for (" + carriedOutTask + ")",
              e2);
        }
      }

      // Rethrowing to parent
      if (e instanceof ProjectRenamingFailedException) {
        throw (ProjectRenamingFailedException) e;
      } else {
        throw new ProjectRenamingFailedException("Project renaming failed for "
            + "unspecified reason", e);
      }
    } finally {
      purgeCaches(sourceProject);
    }
    return destination.toString();
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
}
