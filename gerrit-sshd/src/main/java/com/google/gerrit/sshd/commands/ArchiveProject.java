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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.ehcache.EhcachePoolImpl;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;

/** Removes the project to archive dir and flushes the project list cache. */
final class ArchiveProject extends BaseCommand {

  @Argument(index = 0, required = true, metaVar = "PROJECT_NAME", usage = "name of project")
  private String projectName;

  @Inject
  IdentifiedUser currentUser;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private EhcachePoolImpl poolImpl;

  @Inject
  private ReviewDb db;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canAdministrateServer()) {
          String msg =
              String.format(
                  "fatal: %s is not an administrator. Can't archive project",
                  currentUser.getUserName());
          throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        parseCommandLine();
        archiveProject();
      }
    });
  }

  private void archiveProject() throws Failure {
    try {
      Project.NameKey name = Project.NameKey.parse(projectName);

      if (db.changes().byProjectOpenAll(name).toList().size() > 0) {
        throw new UnloggedFailure(1,
            "fatal: this project has open changes, close them first then retry");
      }

      if (!repoManager.archiveRepository(name)) {
        throw new UnloggedFailure(1, "fatal: can't move the directory");
      }
      // flush the projects list cache
      poolImpl.getCacheManager().getCache("project_list").removeAll();
    } catch (RepositoryNotFoundException ex) {
      throw new UnloggedFailure(1, "fatal: project \"" + projectName
          + "\" does not exist");
    } catch (OrmException e) {
      throw new UnloggedFailure(1, "fatal: can not get open changes for \""
          + projectName + "\"");
    }
  }
}
