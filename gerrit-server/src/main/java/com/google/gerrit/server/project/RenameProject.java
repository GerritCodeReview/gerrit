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
import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
          "%s does not have \"Administrate Server\" capability.",
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
      Iterator<SubmoduleSubscription> iterator =
          db.submoduleSubscriptions().bySubmoduleProject(source).iterator();
      if (iterator.hasNext()) {
        throw new ProjectRenamingFailedException("Cannot rename project "
            + source + ", as it is a subscribed submodule (e.g.: "
            + (iterator.next()) + ")" );
      }
    } catch (OrmException e) {
      throw new ProjectRenamingFailedException("Could not fetch "
          + "subscriptions", e);
    }

    // Creating the subtasks
    List<Task> tasks = Lists.newArrayList();
    for (Task.Factory taskFactory : taskFactories) {
      if (!tasks.add(taskFactory.create(source, destination))) {
        throw new ProjectRenamingFailedException("Could not add created task "
            + "to list of tasks");
      }
    }

    // Prioritizing the subtasks
    Collections.sort(tasks, new Comparator<Task>() {
        @Override
        public int compare(Task o1, Task o2) {
          return o1.getPriority() - o2.getPriority();
        }
      });

    // Running the subtasks
    Deque<Task> carriedOutTasks = new ArrayDeque<Task>();
    boolean rollbackNecessary = false;
    try {
      for (Task task : tasks) {
        task.carryOut();
        carriedOutTasks.push(task);
      }
    } catch (Throwable e) {
      rollbackNecessary = true;
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
      purgeCaches();

      if (rollbackNecessary) {
        projectCache.evict(sourceProject);
        ProjectState destinationProjectState = projectCache.get(destination);
        if (destinationProjectState != null) {
          projectCache.remove(destinationProjectState.getProject());
        }
      } else {
        projectCache.evict(destination);
        projectCache.remove(sourceProject);
      }
    }
    return destination.toString();
  }

  /**
   * Purges caches relevant to renaming a project
   */
  private void purgeCaches() {
    Map<String, Provider<Cache<?, ?>>> gerritCaches =
        cacheMap.byPlugin("gerrit");
    for (String cacheToClear: new String[] { "adv_bases", "changes",
        "git_tags", "project_list"}) {
      try {
        Provider<Cache<?, ?>> cacheProvider = gerritCaches.get(cacheToClear);
        if (cacheProvider != null) {
          cacheProvider.get().invalidateAll();
        }
      } catch (Throwable err) {
        log.error("cannot flush cache \"" + cacheToClear + "\": " + err);
      }
    }
  }
}
