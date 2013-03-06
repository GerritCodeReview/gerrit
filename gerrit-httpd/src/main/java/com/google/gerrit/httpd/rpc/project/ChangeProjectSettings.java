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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.PerRequestProjectControlCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmConcurrencyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;

class ChangeProjectSettings extends Handler<ProjectDetail> {
  interface Factory {
    ChangeProjectSettings create(@Assisted Project update);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final GitRepositoryManager mgr;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final Provider<PerRequestProjectControlCache> userCache;
  private final CurrentUser currentUser;

  private final Project update;

  @Inject
  ChangeProjectSettings(
      final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager mgr,
      final MetaDataUpdate.User metaDataUpdateFactory,
      final Provider<PerRequestProjectControlCache> uc,
      final CurrentUser currentUser, @Assisted final Project update) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectControlFactory = projectControlFactory;
    this.mgr = mgr;
    this.userCache = uc;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.currentUser = currentUser;

    this.update = update;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, OrmException,
      IOException {
    final Project.NameKey projectName = update.getNameKey();
    projectControlFactory.ownerFor(projectName);

    final MetaDataUpdate md;
    try {
      md = metaDataUpdateFactory.create(projectName);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(projectName);
    } catch (IOException e) {
      throw new OrmException(e);
    }
    try {
      // TODO We really should take advantage of the Git commit DAG and
      // ensure the current version matches the old version the caller read.
      //
      ProjectConfig config = ProjectConfig.read(md);
      config.getProject().copySettingsFrom(update,
          currentUser.getCapabilities().canAdministrateServer());

      md.setMessage("Modified project settings\n");
      try {
        config.commit(md);
        mgr.setProjectDescription(projectName, update.getDescription());
        userCache.get().evict(config.getProject());
      } catch (IOException e) {
        throw new OrmConcurrencyException("Cannot update " + projectName);
      }
    } catch (ConfigInvalidException err) {
      throw new OrmException("Cannot read project " + projectName, err);
    } catch (IOException err) {
      throw new OrmException("Cannot update project " + projectName, err);
    } finally {
      md.close();
    }

    return projectDetailFactory.create(projectName).call();
  }
}
