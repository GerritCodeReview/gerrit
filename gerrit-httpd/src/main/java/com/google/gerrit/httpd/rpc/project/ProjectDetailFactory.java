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

import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.InheritedBoolean;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

class ProjectDetailFactory extends Handler<ProjectDetail> {
  interface Factory {
    ProjectDetailFactory create(@Assisted Project.NameKey name);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final GitRepositoryManager gitRepositoryManager;
  private final CurrentUser currentUser;

  private final Project.NameKey projectName;

  @Inject
  ProjectDetailFactory(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager gitRepositoryManager,
      final CurrentUser currentUser,
      @Assisted final Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;
    this.gitRepositoryManager = gitRepositoryManager;
    this.currentUser = currentUser;
    this.projectName = name;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, IOException {
    final ProjectControl pc =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);
    final ProjectState projectState = pc.getProjectState();
    final ProjectDetail detail = new ProjectDetail();
    detail.setProject(projectState.getProject());

    final boolean userIsOwner = pc.isOwner();
    final boolean userIsOwnerAnyRef = pc.isOwnerAnyRef();

    detail.setCanModifyAccess(userIsOwnerAnyRef);
    detail.setCanModifyAgreements(userIsOwner);
    detail.setCanModifyDescription(userIsOwner);
    detail.setCanModifyMergeType(userIsOwner);
    detail.setCanModifyState(userIsOwner);
    detail.setCanModifyTemplate(currentUser.getCapabilities()
        .canAdministrateServer());

    final InheritedBoolean useContributorAgreements = new InheritedBoolean();
    final InheritedBoolean useSignedOffBy = new InheritedBoolean();
    final InheritedBoolean useContentMerge = new InheritedBoolean();
    final InheritedBoolean requireChangeID = new InheritedBoolean();
    final InheritedBoolean isTemplate = new InheritedBoolean();
    useContributorAgreements.setValue(projectState.getProject()
        .getUseContributorAgreements());
    useSignedOffBy.setValue(projectState.getProject().getUseSignedOffBy());
    useContentMerge.setValue(projectState.getProject().getUseContentMerge());
    requireChangeID.setValue(projectState.getProject().getRequireChangeID());
    isTemplate.setValue(projectState.getProject().getIsTemplate());
    ProjectState parentState = Iterables.getFirst(projectState.parents(), null);
    if (parentState != null) {
      useContributorAgreements.setInheritedValue(parentState
          .isUseContributorAgreements());
      useSignedOffBy.setInheritedValue(parentState.isUseSignedOffBy());
      useContentMerge.setInheritedValue(parentState.isUseContentMerge());
      requireChangeID.setInheritedValue(parentState.isRequireChangeID());
      isTemplate.setInheritedValue(parentState.isIsTemplate());
    }
    detail.setUseContributorAgreements(useContributorAgreements);
    detail.setUseSignedOffBy(useSignedOffBy);
    detail.setUseContentMerge(useContentMerge);
    detail.setRequireChangeID(requireChangeID);
    detail.setIsTemplate(isTemplate);

    final Project.NameKey projectName = projectState.getProject().getNameKey();
    Repository git;
    try {
      git = gitRepositoryManager.openRepository(projectName);
    } catch (RepositoryNotFoundException err) {
      throw new NoSuchProjectException(projectName);
    }
    try {
      Ref head = git.getRef(Constants.HEAD);
      if (head != null && head.isSymbolic()
          && GitRepositoryManager.REF_CONFIG.equals(head.getLeaf().getName())) {
        detail.setPermissionOnly(true);
      }
    } catch (IOException err) {
      throw new NoSuchProjectException(projectName);
    } finally {
      git.close();
    }

    return detail;
  }
}
