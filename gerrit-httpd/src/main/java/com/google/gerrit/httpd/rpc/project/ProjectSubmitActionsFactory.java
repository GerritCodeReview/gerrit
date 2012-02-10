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

import com.google.gerrit.common.data.SubmitActionSection;
import com.google.gerrit.common.data.ProjectSubmitActions;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectSubmitActionsFactory extends
    Handler<ProjectSubmitActions> {
  interface Factory {
    ProjectSubmitActionsFactory create(@Assisted Project.NameKey name);
  }

  private final ProjectCache projectCache;
  private final ProjectControl.Factory projectControlFactory;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final AllProjectsName allProjectsName;

  private final Project.NameKey projectName;
  private ProjectControl pc;

  @Inject
  ProjectSubmitActionsFactory(final ProjectCache projectCache,
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
  public ProjectSubmitActions call() throws Exception {
    // Load the current configuration from the repository, ensuring its the most
    // recent version available. If it differs from what was in the project
    // state, force a cache flush now.
    //
    ProjectConfig config = null;
    boolean cacheNeedsRefreshing = false;
    MetaDataUpdate md = metaDataUpdateFactory.create(projectName);
    try {
      config = ProjectConfig.read(md);
      if (config.getRevision() != null
          && projectCache.get(projectName).isRevisionOutOfDate(config)) {
        cacheNeedsRefreshing = true;
      }
    } finally {
      md.close();
      if (config != null) {
        pc =
            projectControlFactory.validateFor(config.getProject(),
                ProjectControl.OWNER | ProjectControl.VISIBLE,
                cacheNeedsRefreshing);
      } else {
        pc = projectControlFactory.validateFor(projectName, //
            ProjectControl.OWNER | ProjectControl.VISIBLE);
      }
    }

    List<SubmitActionSection> local = new ArrayList<SubmitActionSection>();
    Set<String> ownerOf = new HashSet<String>();
    for (SubmitActionSection section : config.getSubmitActionSections()) {
      RefControl rc = pc.controlForRef(section.getName());
      if (rc.isOwner()) {
        local.add(section);
        ownerOf.add(section.getName());
      } else if (rc.isVisible()) {
        local.add(section);
      }
    }

    final ProjectSubmitActions submitActions = new ProjectSubmitActions();
    submitActions.setRevision(config.getRevision().name());
    submitActions.setSubmitActions(local);
    submitActions.setOwnerOf(ownerOf);

    if (projectName.equals(allProjectsName)) {
      submitActions.setInheritsFrom(null);
    } else if (config.getProject().getParent() != null) {
      submitActions.setInheritsFrom(config.getProject().getParent());
    } else {
      submitActions.setInheritsFrom(allProjectsName);
    }

    return submitActions;
  }
}
