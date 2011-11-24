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

import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class ProjectDetailFactory extends Handler<ProjectDetail> {
  interface Factory {
    ProjectDetailFactory create(@Assisted Project.NameKey name);
  }

  private final ProjectControl.Factory projectControlFactory;

  private final Project.NameKey projectName;

  @Inject
  ProjectDetailFactory(final ProjectControl.Factory projectControlFactory,

      @Assisted final Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;

    this.projectName = name;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException {
    final ProjectControl pc =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);
    final ProjectState projectState = pc.getProjectState();
    final ProjectDetail detail = new ProjectDetail();

    // This block of code will be removed when integrating Merge Strategy UI change.
    final Project p = projectState.getProject();

    boolean setDefaultMergeStrategy = true;
    for (MergeStrategySection section : projectState.getLocalMergeStrategySections()) {
      if (section.getName().equals(RefConfigSection.ALL)) {
        p.setSubmitType(section.getSubmitType().name());
        p.setUseContentMerge(section.isUseContentMerge());
        setDefaultMergeStrategy = false;
        break;
      }
    }

    // if there is no mergeStrategySection to refs/* set default value
    if (setDefaultMergeStrategy) {
      p.setSubmitType(MergeStrategySection.SubmitType.MERGE_IF_NECESSARY.name());
    }

    detail.setProject(p);

    final boolean userIsOwner = pc.isOwner();
    final boolean userIsOwnerAnyRef = pc.isOwnerAnyRef();

    detail.setCanModifyAccess(userIsOwnerAnyRef);
    detail.setCanModifyAgreements(userIsOwner);
    detail.setCanModifyDescription(userIsOwner);
    detail.setCanModifyMergeType(userIsOwner);
    return detail;
  }
}
