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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.ProjectAdminService;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectRight;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class ProjectAdminServiceImpl implements ProjectAdminService {
  private final AddBranch.Factory addBranchFactory;
  private final AddProjectRight.Factory addProjectRightFactory;
  private final ChangeProjectSettings.Factory changeProjectSettingsFactory;
  private final DeleteBranches.Factory deleteBranchesFactory;
  private final DeleteProjectRights.Factory deleteProjectRightsFactory;
  private final ListBranches.Factory listBranchesFactory;
  private final OwnedProjects.Factory ownedProjectsFactory;
  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final AddRefRight.Factory addRefRightFactory;
  private final DeleteRefRights.Factory deleteRefRightsFactory;

  @Inject
  ProjectAdminServiceImpl(final AddBranch.Factory addBranchFactory,
      final AddProjectRight.Factory addProjectRightFactory,
      final ChangeProjectSettings.Factory changeProjectSettingsFactory,
      final DeleteBranches.Factory deleteBranchesFactory,
      final DeleteProjectRights.Factory deleteProjectRightFactory,
      final ListBranches.Factory listBranchesFactory,
      final OwnedProjects.Factory ownedProjectsFactory,
      final ProjectDetailFactory.Factory projectDetailFactory,
      final AddRefRight.Factory addRefRightFactory,
      final DeleteRefRights.Factory deleteRefRightsFactory) {
    this.addBranchFactory = addBranchFactory;
    this.addProjectRightFactory = addProjectRightFactory;
    this.changeProjectSettingsFactory = changeProjectSettingsFactory;
    this.deleteBranchesFactory = deleteBranchesFactory;
    this.deleteProjectRightsFactory = deleteProjectRightFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.ownedProjectsFactory = ownedProjectsFactory;
    this.projectDetailFactory = projectDetailFactory;
    this.addRefRightFactory = addRefRightFactory;
    this.deleteRefRightsFactory = deleteRefRightsFactory;
  }

  @Override
  public void ownedProjects(final AsyncCallback<List<Project>> callback) {
    ownedProjectsFactory.create().to(callback);
  }

  @Override
  public void projectDetail(final Project.NameKey projectName,
      final AsyncCallback<ProjectDetail> callback) {
    projectDetailFactory.create(projectName).to(callback);
  }

  @Override
  public void changeProjectSettings(final Project update,
      final AsyncCallback<ProjectDetail> callback) {
    changeProjectSettingsFactory.create(update).to(callback);
  }

  @Override
  public void deleteRefRights(final Project.NameKey projectName,
      final Set<RefRight.Key> toRemove,
      final AsyncCallback<VoidResult> callback) {
    deleteRefRightsFactory.create(projectName, toRemove).to(callback);
  }

  @Override
  public void addRight(final Project.NameKey projectName,
      final ApprovalCategory.Id categoryId, final String groupName,
      final String refPattern,
      final short min, final short max,
      final AsyncCallback<ProjectDetail> callback) {
    if (refPattern == null || refPattern.equals("")) {
      addProjectRightFactory.create(projectName, categoryId, groupName, min, max)
      .to(callback);
    } else {
      addRefRightFactory.create(
          projectName, categoryId, groupName, refPattern, min, max).to(callback);
    }
  }

  @Override
  public void deleteProjectRights(final Project.NameKey projectName,
      final Set<ProjectRight.Key> toRemove,
      final AsyncCallback<VoidResult> callback) {
    deleteProjectRightsFactory.create(projectName, toRemove).to(callback);
  }


  @Override
  public void listBranches(final Project.NameKey projectName,
      final AsyncCallback<List<Branch>> callback) {
    listBranchesFactory.create(projectName).to(callback);
  }

  @Override
  public void deleteBranch(final Project.NameKey projectName,
      final Set<Branch.NameKey> toRemove,
      final AsyncCallback<Set<Branch.NameKey>> callback) {
    deleteBranchesFactory.create(projectName, toRemove).to(callback);
  }

  @Override
  public void addBranch(final Project.NameKey projectName,
      final String branchName, final String startingRevision,
      final AsyncCallback<List<Branch>> callback) {
    addBranchFactory.create(projectName, branchName, startingRevision).to(
        callback);
  }
}
