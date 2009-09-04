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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.admin.ProjectAdminService;
import com.google.gerrit.client.admin.ProjectDetail;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;

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

  @Inject
  ProjectAdminServiceImpl(final AddBranch.Factory addBranchFactory,
      final AddProjectRight.Factory addProjectRightFactory,
      final ChangeProjectSettings.Factory changeProjectSettingsFactory,
      final DeleteBranches.Factory deleteBranchesFactory,
      final DeleteProjectRights.Factory deleteProjectRightFactory,
      final ListBranches.Factory listBranchesFactory,
      final OwnedProjects.Factory ownedProjectsFactory,
      final ProjectDetailFactory.Factory projectDetailFactory) {
    this.addBranchFactory = addBranchFactory;
    this.addProjectRightFactory = addProjectRightFactory;
    this.changeProjectSettingsFactory = changeProjectSettingsFactory;
    this.deleteBranchesFactory = deleteBranchesFactory;
    this.deleteProjectRightsFactory = deleteProjectRightFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.ownedProjectsFactory = ownedProjectsFactory;
    this.projectDetailFactory = projectDetailFactory;
  }

  public void ownedProjects(final AsyncCallback<List<Project>> callback) {
    ownedProjectsFactory.create().to(callback);
  }

  public void projectDetail(final Project.NameKey projectName,
      final AsyncCallback<ProjectDetail> callback) {
    projectDetailFactory.create(projectName).to(callback);
  }

  public void changeProjectSettings(final Project update,
      final AsyncCallback<ProjectDetail> callback) {
    changeProjectSettingsFactory.create(update).to(callback);
  }

  public void deleteRight(final Project.NameKey projectName,
      final Set<ProjectRight.Key> toRemove,
      final AsyncCallback<VoidResult> callback) {
    deleteProjectRightsFactory.create(projectName, toRemove).to(callback);
  }

  public void addRight(final Project.NameKey projectName,
      final ApprovalCategory.Id categoryId, final String groupName,
      final short min, final short max,
      final AsyncCallback<ProjectDetail> callback) {
    addProjectRightFactory.create(projectName, categoryId, groupName, min, max)
        .to(callback);
  }

  public void listBranches(final Project.NameKey projectName,
      final AsyncCallback<List<Branch>> callback) {
    listBranchesFactory.create(projectName).to(callback);
  }

  public void deleteBranch(final Project.NameKey projectName,
      final Set<Branch.NameKey> toRemove,
      final AsyncCallback<Set<Branch.NameKey>> callback) {
    deleteBranchesFactory.create(projectName, toRemove).to(callback);
  }

  public void addBranch(final Project.NameKey projectName,
      final String branchName, final String startingRevision,
      final AsyncCallback<List<Branch>> callback) {
    addBranchFactory.create(projectName, branchName, startingRevision).to(
        callback);
  }
}
