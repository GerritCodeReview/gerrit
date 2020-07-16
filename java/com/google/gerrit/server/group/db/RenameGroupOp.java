// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.DefaultQueueOp;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;

class RenameGroupOp extends DefaultQueueOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    RenameGroupOp create(
        @Assisted("author") PersonIdent author,
        @Assisted AccountGroup.UUID uuid,
        @Assisted("oldName") String oldName,
        @Assisted("newName") String newName);
  }

  private static final int MAX_TRIES = 10;

  private final ProjectCache projectCache;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectConfig.Factory projectConfigFactory;

  private final PersonIdent author;
  private final AccountGroup.UUID uuid;
  private final String oldName;
  private final String newName;
  private final List<Project.NameKey> retryOn;

  private boolean tryingAgain;

  @Inject
  public RenameGroupOp(
      WorkQueue workQueue,
      ProjectCache projectCache,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      @Assisted("author") PersonIdent author,
      @Assisted AccountGroup.UUID uuid,
      @Assisted("oldName") String oldName,
      @Assisted("newName") String newName) {
    super(workQueue);
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectConfigFactory = projectConfigFactory;

    this.author = author;
    this.uuid = uuid;
    this.oldName = oldName;
    this.newName = newName;
    this.retryOn = new ArrayList<>();
  }

  @Override
  public void run() {
    Iterable<Project.NameKey> names = tryingAgain ? retryOn : projectCache.all();
    for (Project.NameKey projectName : names) {
      CachedProjectConfig config =
          projectCache.get(projectName).orElseThrow(illegalState(projectName)).getConfig();
      Optional<GroupReference> ref = config.getGroup(uuid);
      if (!ref.isPresent() || newName.equals(ref.get().getName())) {
        continue;
      }

      try (MetaDataUpdate md = metaDataUpdateFactory.create(projectName)) {
        rename(md);
      } catch (RepositoryNotFoundException noProject) {
        continue;
      } catch (ConfigInvalidException | IOException err) {
        logger.atSevere().withCause(err).log("Cannot rename group %s in %s", oldName, projectName);
      }
    }

    // If one or more projects did not update, wait 5 minutes and give it
    // another attempt. If it doesn't update after that, give up.
    if (!retryOn.isEmpty() && !tryingAgain) {
      tryingAgain = true;
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError = start(5, TimeUnit.MINUTES);
    }
  }

  private void rename(MetaDataUpdate md) throws IOException, ConfigInvalidException {
    boolean success = false;
    for (int attempts = 0; !success && attempts < MAX_TRIES; attempts++) {
      ProjectConfig config = projectConfigFactory.read(md);

      // The group isn't referenced, or its name has been fixed already.
      //
      GroupReference ref = config.getGroup(uuid);
      if (ref == null || newName.equals(ref.getName())) {
        projectCache.evict(config.getProject());
        return;
      }

      config.renameGroup(uuid, newName);
      md.getCommitBuilder().setAuthor(author);
      md.setMessage("Rename group " + oldName + " to " + newName + "\n");
      try {
        config.commit(md);
        projectCache.evict(config.getProject());
        success = true;
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "Could not commit rename of group %s to %s in %s",
            oldName, newName, md.getProjectName().get());
        try {
          Thread.sleep(25 /* milliseconds */);
        } catch (InterruptedException wakeUp) {
          continue;
        }
      }
    }

    if (!success) {
      if (tryingAgain) {
        logger.atWarning().log(
            "Could not rename group %s to %s in %s", oldName, newName, md.getProjectName().get());
      } else {
        retryOn.add(md.getProjectName());
      }
    }
  }

  @Override
  public String toString() {
    return "Rename Group " + oldName;
  }
}
