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

import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.common.data.ProjectAdminService;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.data.ProjectRightsBased;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;

import java.util.List;
import java.util.Set;

class ProjectAdminServiceImpl implements ProjectAdminService {
  private final AddBranch.Factory addBranchFactory;
  private final ChangeProjectSettings.Factory changeProjectSettingsFactory;
  private final DeleteBranches.Factory deleteBranchesFactory;
  private final ListBranches.Factory listBranchesFactory;
  private final ProjectsTO.Factory projectsTOFactory;
  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final AddRefRight.Factory addRefRightFactory;
  private final DeleteRefRights.Factory deleteRefRightsFactory;
  private final SetParent.Factory setParentFactory;

  @Inject
  ProjectAdminServiceImpl(final AddBranch.Factory addBranchFactory,
      final ChangeProjectSettings.Factory changeProjectSettingsFactory,
      final DeleteBranches.Factory deleteBranchesFactory,
      final ListBranches.Factory listBranchesFactory,
      final ProjectsTO.Factory projectsTOFactory,
      final ProjectDetailFactory.Factory projectDetailFactory,
      final AddRefRight.Factory addRefRightFactory,
      final DeleteRefRights.Factory deleteRefRightsFactory,
      final SetParent.Factory setParentFactory) {
    this.addBranchFactory = addBranchFactory;
    this.changeProjectSettingsFactory = changeProjectSettingsFactory;
    this.deleteBranchesFactory = deleteBranchesFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.projectsTOFactory = projectsTOFactory;
    this.projectDetailFactory = projectDetailFactory;
    this.addRefRightFactory = addRefRightFactory;
    this.deleteRefRightsFactory = deleteRefRightsFactory;
    this.setParentFactory = setParentFactory;
  }

  @Override
  public void projectsTO(final AsyncCallback<List<ProjectRightsBased>> callback) {
    projectsTOFactory.create().to(callback);
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
  public void deleteRight(final Project.NameKey projectName,
      final Set<RefRight.Key> toRemove, final AsyncCallback<ProjectDetail> callback) {
    deleteRefRightsFactory.create(projectName, toRemove).to(callback);
  }

  @Override
  public void addRight(final Project.NameKey projectName,
      final ApprovalCategory.Id categoryId, final String groupName,
      final String refPattern, final short min, final short max,
      final AsyncCallback<ProjectDetail> callback) {
    addRefRightFactory.create(projectName, categoryId, groupName, refPattern,
        min, max).to(callback);
  }

  @Override
  public void listBranches(final Project.NameKey projectName,
      final AsyncCallback<ListBranchesResult> callback) {
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
      final AsyncCallback<ListBranchesResult> callback) {
    addBranchFactory.create(projectName, branchName, startingRevision).to(
        callback);
  }

  @Override
  public void setParentProject(String parentName, List<Project.NameKey> childProjects,
      AsyncCallback<String> callback) {
    setParentFactory.create(parentName, childProjects).to(callback);
  }
}