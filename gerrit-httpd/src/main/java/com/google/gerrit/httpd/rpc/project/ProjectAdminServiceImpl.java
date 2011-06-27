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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.data.ProjectAdminService;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.common.data.ProjectMergeStrategies;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;

import java.util.List;
import java.util.Set;

class ProjectAdminServiceImpl implements ProjectAdminService {
  private final AddBranch.Factory addBranchFactory;
  private final ChangeProjectAccess.Factory changeProjectAccessFactory;
  private final ChangeRefMergeStrategies.Factory changeRefMergeStrategies;
  private final ChangeProjectSettings.Factory changeProjectSettingsFactory;
  private final DeleteBranches.Factory deleteBranchesFactory;
  private final ListBranches.Factory listBranchesFactory;
  private final VisibleProjects.Factory visibleProjectsFactory;
  private final ProjectAccessFactory.Factory projectAccessFactory;
  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectMergeStrategiesFactory.Factory projectMergeStrategiesFactory;

  @Inject
  ProjectAdminServiceImpl(final AddBranch.Factory addBranchFactory,
      final ChangeProjectAccess.Factory changeProjectAccessFactory,
      final ChangeRefMergeStrategies.Factory changeRefMergeStrategies,
      final ChangeProjectSettings.Factory changeProjectSettingsFactory,
      final DeleteBranches.Factory deleteBranchesFactory,
      final ListBranches.Factory listBranchesFactory,
      final VisibleProjects.Factory visibleProjectsFactory,
      final ProjectAccessFactory.Factory projectAccessFactory,
      final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectMergeStrategiesFactory.Factory projectMergeStrategiesFactory) {
    this.addBranchFactory = addBranchFactory;
    this.changeProjectAccessFactory = changeProjectAccessFactory;
    this.changeRefMergeStrategies = changeRefMergeStrategies;
    this.changeProjectSettingsFactory = changeProjectSettingsFactory;
    this.deleteBranchesFactory = deleteBranchesFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.visibleProjectsFactory = visibleProjectsFactory;
    this.projectAccessFactory = projectAccessFactory;
    this.projectDetailFactory = projectDetailFactory;
    this.projectMergeStrategiesFactory = projectMergeStrategiesFactory;
  }

  @Override
  public void visibleProjects(final AsyncCallback<List<Project>> callback) {
    visibleProjectsFactory.create().to(callback);
  }

  @Override
  public void projectDetail(final Project.NameKey projectName,
      final AsyncCallback<ProjectDetail> callback) {
    projectDetailFactory.create(projectName).to(callback);
  }

  @Override
  public void projectAccess(final Project.NameKey projectName,
      final AsyncCallback<ProjectAccess> callback) {
    projectAccessFactory.create(projectName).to(callback);
  }

  @Override
  public void changeProjectSettings(final Project update,
      final AsyncCallback<ProjectDetail> callback) {
    changeProjectSettingsFactory.create(update).to(callback);
  }

  @Override
  public void changeProjectAccess(Project.NameKey projectName,
      String baseRevision, String msg, List<AccessSection> sections,
      AsyncCallback<ProjectAccess> cb) {
    ObjectId base = ObjectId.fromString(baseRevision);
    changeProjectAccessFactory.create(projectName, base, sections, msg).to(cb);
  }

  @Override
  public void projectMergeStrategies(final NameKey projectName,
      AsyncCallback<ProjectMergeStrategies> callback) {
    projectMergeStrategiesFactory.create(projectName).to(callback);
  }

  @Override
  public void changeMergeStrategies(final NameKey projectName,
      String baseRevision, String msg, List<MergeStrategySection> sections,
      AsyncCallback<VoidResult> callback) {
    final ObjectId base = ObjectId.fromString(baseRevision);
    changeRefMergeStrategies.create(projectName, base, sections, msg).to(callback);
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
}
