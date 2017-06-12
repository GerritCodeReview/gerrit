// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.SetParent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

class ChangeProjectAccess extends ProjectAccessHandler<ProjectAccess> {
  interface Factory {
    ChangeProjectAccess create(
        @Assisted("projectName") Project.NameKey projectName,
        @Nullable @Assisted ObjectId base,
        @Assisted List<AccessSection> sectionList,
        @Nullable @Assisted("parentProjectName") Project.NameKey parentProjectName,
        @Nullable @Assisted String message);
  }

  private final GitReferenceUpdated gitRefUpdated;
  private final ProjectAccessFactory.Factory projectAccessFactory;
  private final ProjectCache projectCache;

  @Inject
  ChangeProjectAccess(
      ProjectAccessFactory.Factory projectAccessFactory,
      ProjectControl.Factory projectControlFactory,
      ProjectCache projectCache,
      GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory,
      AllProjectsName allProjects,
      Provider<SetParent> setParent,
      GitReferenceUpdated gitRefUpdated,
      @Assisted("projectName") Project.NameKey projectName,
      @Nullable @Assisted ObjectId base,
      @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted("parentProjectName") Project.NameKey parentProjectName,
      @Nullable @Assisted String message) {
    super(
        projectControlFactory,
        groupBackend,
        metaDataUpdateFactory,
        allProjects,
        setParent,
        projectName,
        base,
        sectionList,
        parentProjectName,
        message,
        true);
    this.projectAccessFactory = projectAccessFactory;
    this.projectCache = projectCache;
    this.gitRefUpdated = gitRefUpdated;
  }

  @Override
  protected ProjectAccess updateProjectConfig(
      CurrentUser user, ProjectConfig config, MetaDataUpdate md, boolean parentProjectUpdate)
      throws IOException, NoSuchProjectException, ConfigInvalidException {
    RevCommit commit = config.commit(md);

    gitRefUpdated.fire(
        config.getProject().getNameKey(),
        RefNames.REFS_CONFIG,
        base,
        commit.getId(),
        user.asIdentifiedUser().getAccount());

    projectCache.evict(config.getProject());
    return projectAccessFactory.create(projectName).call();
  }
}
