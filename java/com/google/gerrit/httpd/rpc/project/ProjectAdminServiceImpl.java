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
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.data.ProjectAdminService;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

class ProjectAdminServiceImpl implements ProjectAdminService {
  private final ChangeProjectAccess.Factory changeProjectAccessFactory;
  private final ReviewProjectAccess.Factory reviewProjectAccessFactory;
  private final ProjectAccessFactory.Factory projectAccessFactory;

  @Inject
  ProjectAdminServiceImpl(
      final ChangeProjectAccess.Factory changeProjectAccessFactory,
      final ReviewProjectAccess.Factory reviewProjectAccessFactory,
      final ProjectAccessFactory.Factory projectAccessFactory) {
    this.changeProjectAccessFactory = changeProjectAccessFactory;
    this.reviewProjectAccessFactory = reviewProjectAccessFactory;
    this.projectAccessFactory = projectAccessFactory;
  }

  @Override
  public void projectAccess(
      final Project.NameKey projectName, AsyncCallback<ProjectAccess> callback) {
    projectAccessFactory.create(projectName).to(callback);
  }

  private static ObjectId getBase(String baseRevision) {
    if (baseRevision != null && !baseRevision.isEmpty()) {
      return ObjectId.fromString(baseRevision);
    }
    return null;
  }

  @Override
  public void changeProjectAccess(
      Project.NameKey projectName,
      String baseRevision,
      String msg,
      List<AccessSection> sections,
      Project.NameKey parentProjectName,
      AsyncCallback<ProjectAccess> cb) {
    changeProjectAccessFactory
        .create(projectName, getBase(baseRevision), sections, parentProjectName, msg)
        .to(cb);
  }

  @Override
  public void reviewProjectAccess(
      Project.NameKey projectName,
      String baseRevision,
      String msg,
      List<AccessSection> sections,
      Project.NameKey parentProjectName,
      AsyncCallback<Change.Id> cb) {
    reviewProjectAccessFactory
        .create(projectName, getBase(baseRevision), sections, parentProjectName, msg)
        .to(cb);
  }
}
