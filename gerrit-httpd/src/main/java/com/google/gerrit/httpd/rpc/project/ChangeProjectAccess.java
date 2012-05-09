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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

class ChangeProjectAccess extends ProjectAccessHandler<ProjectAccess> {
  interface Factory {
    ChangeProjectAccess create(@Assisted Project.NameKey projectName,
        @Nullable @Assisted ObjectId base,
        @Assisted List<AccessSection> sectionList,
        @Nullable @Assisted String message);
  }

  private final ProjectAccessFactory.Factory projectAccessFactory;
  private final ProjectCache projectCache;

  @Inject
  ChangeProjectAccess(final ProjectAccessFactory.Factory projectAccessFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final GroupCache groupCache,
      final MetaDataUpdate.User metaDataUpdateFactory,

      @Assisted final Project.NameKey projectName,
      @Nullable @Assisted final ObjectId base,
      @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted String message) {
    super(projectControlFactory, groupCache, metaDataUpdateFactory,
        projectName, base, sectionList, message, true);
    this.projectAccessFactory = projectAccessFactory;
    this.projectCache = projectCache;
  }

  @Override
  protected ProjectAccess updateProjectConfig(ProjectConfig config,
      MetaDataUpdate md) throws IOException, NoSuchProjectException, ConfigInvalidException {
    config.commit(md);
    projectCache.evict(config.getProject());
    return projectAccessFactory.create(projectName).call();
  }
}
