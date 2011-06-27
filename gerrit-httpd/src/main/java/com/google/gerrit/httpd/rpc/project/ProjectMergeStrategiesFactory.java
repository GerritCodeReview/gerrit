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

import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.common.data.ProjectMergeStrategies;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectMergeStrategiesFactory extends
    Handler<ProjectMergeStrategies> {
  interface Factory {
    ProjectMergeStrategiesFactory create(@Assisted Project.NameKey name);
  }

  private final ProjectCache projectCache;
  private final ProjectControl.Factory projectControlFactory;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final AllProjectsName allProjectsName;

  private final Project.NameKey projectName;
  private ProjectControl pc;

  @Inject
  ProjectMergeStrategiesFactory(final ProjectCache projectCache,
      final ProjectControl.Factory projectControlFactory,
      final MetaDataUpdate.Server metaDataUpdateFactory,
      final AllProjectsName allProjectsName,
      @Assisted final Project.NameKey name) {
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allProjectsName = allProjectsName;
    this.projectName = name;
  }

  @Override
  public ProjectMergeStrategies call() throws Exception {
    pc = open();

    // Load the current configuration from the repository, ensuring its the most
    // recent version available. If it differs from what was in the project
    // state, force a cache flush now.
    //
    ProjectConfig config;
    MetaDataUpdate md = metaDataUpdateFactory.create(projectName);
    try {
      config = ProjectConfig.read(md);

      if (config.getRevision() != null
          && !config.getRevision().equals(
              pc.getProjectState().getConfig().getRevision())) {
        projectCache.evict(config.getProject());
        pc = open();
      }
    } finally {
      md.close();
    }

    List<MergeStrategySection> local = new ArrayList<MergeStrategySection>();
    Set<String> ownerOf = new HashSet<String>();
    for (MergeStrategySection section : config.getMergeStrategySections()) {
      RefControl rc = pc.controlForRef(section.getName());
      if (rc.isOwner()) {
        local.add(section);
        ownerOf.add(section.getName());
      } else if (rc.isVisible()) {
        local.add(section);
      }
    }

    final ProjectMergeStrategies detail = new ProjectMergeStrategies();
    detail.setRevision(config.getRevision().name());
    detail.setLocal(local);
    detail.setOwnerOf(ownerOf);

    if (projectName.equals(allProjectsName)) {
      detail.setInheritsFrom(null);
    } else if (config.getProject().getParent() != null) {
      detail.setInheritsFrom(config.getProject().getParent());
    } else {
      detail.setInheritsFrom(allProjectsName);
    }

    return detail;
  }

  private ProjectControl open() throws NoSuchProjectException {
    return projectControlFactory.validateFor( //
        projectName, //
        ProjectControl.OWNER | ProjectControl.VISIBLE);
  }
}
