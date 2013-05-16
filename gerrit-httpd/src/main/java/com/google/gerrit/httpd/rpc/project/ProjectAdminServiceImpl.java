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
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.data.ProjectAdminService;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;

import java.util.List;
import java.util.Set;

class ProjectAdminServiceImpl implements ProjectAdminService {
  private final ChangeProjectAccess.Factory changeProjectAccessFactory;
  private final ReviewProjectAccess.Factory reviewProjectAccessFactory;
  private final ChangeProjectSettings.Factory changeProjectSettingsFactory;
  private final DeleteBranches.Factory deleteBranchesFactory;
  private final ListBranches.Factory listBranchesFactory;
  private final VisibleProjectDetails.Factory visibleProjectDetailsFactory;
  private final ProjectAccessFactory.Factory projectAccessFactory;
  private final ProjectDetailFactory.Factory projectDetailFactory;

  @Inject
  ProjectAdminServiceImpl(final ChangeProjectAccess.Factory changeProjectAccessFactory,
      final ReviewProjectAccess.Factory reviewProjectAccessFactory,
      final ChangeProjectSettings.Factory changeProjectSettingsFactory,
      final DeleteBranches.Factory deleteBranchesFactory,
      final ListBranches.Factory listBranchesFactory,
      final VisibleProjectDetails.Factory visibleProjectDetailsFactory,
      final ProjectAccessFactory.Factory projectAccessFactory,
      final ProjectDetailFactory.Factory projectDetailFactory) {
    this.changeProjectAccessFactory = changeProjectAccessFactory;
    this.reviewProjectAccessFactory = reviewProjectAccessFactory;
    this.changeProjectSettingsFactory = changeProjectSettingsFactory;
    this.deleteBranchesFactory = deleteBranchesFactory;
    this.listBranchesFactory = listBranchesFactory;
    this.visibleProjectDetailsFactory = visibleProjectDetailsFactory;
    this.projectAccessFactory = projectAccessFactory;
    this.projectDetailFactory = projectDetailFactory;
  }

  @Override
  public void visibleProjectDetails(final AsyncCallback<List<ProjectDetail>> callback) {
    visibleProjectDetailsFactory.create().to(callback);
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

  private static ObjectId getBase(final String baseRevision) {
    if (baseRevision != null && !baseRevision.isEmpty()) {
      return ObjectId.fromString(baseRevision);
    }
    return null;
  }

  @Override
  public void changeProjectAccess(Project.NameKey projectName,
      String baseRevision, String msg, List<AccessSection> sections,
      AsyncCallback<ProjectAccess> cb) {
    changeProjectAccessFactory.create(projectName, getBase(baseRevision), sections, msg).to(cb);
  }

  @Override
  public void reviewProjectAccess(Project.NameKey projectName,
      String baseRevision, String msg, List<AccessSection> sections,
      AsyncCallback<Change.Id> cb) {
    reviewProjectAccessFactory.create(projectName, getBase(baseRevision), sections, msg).to(cb);
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
}
