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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ProjectAccessFactory extends Handler<ProjectAccess> {
  interface Factory {
    ProjectAccessFactory create(@Assisted Project.NameKey name);
  }

  private final GroupCache groupCache;
  private final ProjectCache projectCache;
  private final ProjectControl.Factory projectControlFactory;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final Project.NameKey wildProject;

  private final Project.NameKey projectName;
  private ProjectControl pc;

  @Inject
  ProjectAccessFactory(final GroupCache groupCache,
      final ProjectCache projectCache,
      final ProjectControl.Factory projectControlFactory,
      final MetaDataUpdate.Server metaDataUpdateFactory,
      @WildProjectName final Project.NameKey wildProject,


      @Assisted final Project.NameKey name) {
    this.groupCache = groupCache;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.wildProject = wildProject;

    this.projectName = name;
  }

  @Override
  public ProjectAccess call() throws NoSuchProjectException, IOException,
      ConfigInvalidException {
    pc = open();

    // Load the current configuration from the repository, ensuring its the most
    // recent version available. If it differs from what was in the project
    // state, force a cache flush now.
    //
    ProjectConfig config;
    MetaDataUpdate md = metaDataUpdateFactory.create(projectName);
    try {
      config = ProjectConfig.read(md);

      if (config.updateGroupNames(groupCache)) {
        md.setMessage("Update group names\n");
        if (config.commit(md)) {
          projectCache.evict(config.getProject());
          pc = open();
        }
      } else if (config.getRevision() != null
          && !config.getRevision().equals(
              pc.getProjectState().getConfig().getRevision())) {
        projectCache.evict(config.getProject());
        pc = open();
      }
    } finally {
      md.close();
    }

    List<AccessSection> local = new ArrayList<AccessSection>();
    Set<String> ownerOf = new HashSet<String>();
    for (AccessSection section : config.getAccessSections()) {
      RefControl rc = pc.controlForRef(section.getRefPattern());
      if (rc.isOwner()) {
        local.add(section);
        ownerOf.add(section.getRefPattern());
      } else if (rc.isVisible()) {
        local.add(section);
      }
    }

    final ProjectAccess detail = new ProjectAccess();
    detail.setRevision(config.getRevision().name());
    detail.setLocal(local);
    detail.setOwnerOf(ownerOf);

    detail.setInheritsFrom(new HashSet<Project.NameKey>(2));
    if (!projectName.equals(wildProject)) {
      detail.setInheritsFrom(config.getProject().getParents());
      if (detail.getInheritsFrom().isEmpty()) {
        detail.getInheritsFrom().add(wildProject);
      }
    }

    return detail;
  }

  private ProjectControl open() throws NoSuchProjectException {
    return projectControlFactory.validateFor( //
        projectName, //
        ProjectControl.OWNER | ProjectControl.VISIBLE);
  }
}
