// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

class ChangeProjectSettings extends Handler<ProjectDetail> {
  interface Factory {
    ChangeProjectSettings create(@Assisted Project update);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final IdentifiedUser currentUser;
  private final GitRepositoryManager mgr;
  private final PersonIdent serverIdent;

  private final Project update;

  @Inject
  ChangeProjectSettings(
      final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final IdentifiedUser currentUser,
      final GitRepositoryManager mgr,
      @GerritPersonIdent final PersonIdent serverIdent,
      @Assisted final Project update) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.currentUser = currentUser;
    this.mgr = mgr;
    this.serverIdent = serverIdent;

    this.update = update;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, OrmException {
    final Project.NameKey projectName = update.getNameKey();
    final ProjectControl projectControl =
        projectControlFactory.ownerFor(projectName);

    final Repository git;
    try {
      git = mgr.openRepository(projectName);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(projectName);
    }
    try {
      // TODO We really should take advantage of the Git commit DAG and
      // ensure the current version matches the old version the caller read.
      //
      ProjectConfig config = new ProjectConfig();
      config.load(git);
      config.getProject().copySettingsFrom(update);

      CommitBuilder commit = new CommitBuilder();
      commit.setAuthor(currentUser.newCommitterIdent(serverIdent.getWhen(),
          serverIdent.getTimeZone()));
      commit.setCommitter(serverIdent);
      commit.setMessage("Modified project settings\n");
      if (config.commit(commit, git)) {
        mgr.setProjectDescription(projectName, update.getDescription());
        projectCache.evict(config.getProject());
      } else {
        throw new OrmConcurrencyException("Cannot update " + projectName);
      }
    } catch (ConfigInvalidException err) {
      throw new OrmException("Cannot read project " + projectName, err);
    } catch (IOException err) {
      throw new OrmException("Cannot update project " + projectName, err);
    } finally {
      git.close();
    }

    return projectDetailFactory.create(projectName).call();
  }
}
