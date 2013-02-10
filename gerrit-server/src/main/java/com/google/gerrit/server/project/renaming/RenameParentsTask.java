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

package com.google.gerrit.server.project.renaming;

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class RenameParentsTask implements Task {
  private static final Logger log = LoggerFactory
      .getLogger(RenameParentsTask.class);

  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final IdentifiedUser currentUser;
  private final ProjectCache projectCache;
  private final Project.NameKey source;
  private final Project.NameKey destination;

  private Collection<Project.NameKey> updatedProjectNameKeys;

  public interface Factory extends Task.Factory {
    RenameParentsTask create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  @Inject
  public RenameParentsTask(MetaDataUpdate.Server metaDataUpdateFactory,
      IdentifiedUser currentUser, ProjectCache projectCache,
      @Assisted("source") Project.NameKey source,
      @Assisted("destination") Project.NameKey destination) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.source = source;
    this.destination = destination;

    this.updatedProjectNameKeys = new HashSet<Project.NameKey>();
  }

  private void setParent(Project.NameKey childProjectNameKey,
      Project.NameKey newParent, String message) throws IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(childProjectNameKey);
    Project childProject = null;
    try {
      ProjectConfig config = ProjectConfig.read(md);
      childProject = config.getProject();
      childProject.setParentName(newParent);
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
  public void carryOut() throws ProjectRenamingFailedException{
    Project.NameKey rootExceptionProjectNameKey = null;
    try {
      for(Project.NameKey child: projectCache.all()) {
        final ProjectState childProjectState = projectCache.get(child);
        if (childProjectState != null) {
          final Project childProject = childProjectState.getProject();
          Project.NameKey childParentNameKey = childProject.getParent();
          if (childParentNameKey != null &&
              childParentNameKey.equals(source)) {
            try {
              setParent(child, destination, "Follow parent rename to "
                  + destination);
              updatedProjectNameKeys.add(child);
            } catch (Exception e2) {
              rootExceptionProjectNameKey = child;
              throw e2;
            }
          }
        }
      }
    } catch (Throwable e) {
      rollback();

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
  public void rollback() {
    for(Project.NameKey child: updatedProjectNameKeys) {
      try {
        final ProjectState childProjectState = projectCache.get(child);
        if (childProjectState == null) {
          // Not finding child can be harmless, as the project might simply
          // have been deleted meanwhile. Nevertheless, we flag an error
          // here. Better safe than sorry when rolling back.
          throw new NoSuchProjectException(child);
        }
        // Instead of resetting the git repository, we attempt a rollback via
        // a further git commit, so we catch cases where users checked
        // out/updated the config between our initial renaming and this
        // rollback.
        setParent(child, source, "Rollback of parent renaming");
      } catch (Throwable e) {
        log.error("Rolling back renaming parent failed for project " + child
            + ". Please set the project's parent to " + source, e);
      }
    }
  }
}
