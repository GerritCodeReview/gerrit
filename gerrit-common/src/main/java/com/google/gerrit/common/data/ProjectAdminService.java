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

package com.google.gerrit.common.data;

import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectRight;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;
import java.util.Set;

public interface ProjectAdminService extends RemoteJsonService {
  @SignInRequired
  void ownedProjects(AsyncCallback<List<Project>> callback);

  @SignInRequired
  void projectDetail(Project.NameKey projectName,
      AsyncCallback<ProjectDetail> callback);

  @SignInRequired
  void changeProjectSettings(Project update,
      AsyncCallback<ProjectDetail> callback);

  @SignInRequired
  void deleteRight(Project.NameKey projectName, Set<ProjectRight.Key> ids,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void addRight(Project.NameKey projectName, ApprovalCategory.Id categoryId,
      String groupName, short min, short max,
      AsyncCallback<ProjectDetail> callback);

  @SignInRequired
  void listBranches(Project.NameKey projectName,
      AsyncCallback<List<Branch>> callback);

  @SignInRequired
  void addBranch(Project.NameKey projectName, String branchName,
      String startingRevision, AsyncCallback<List<Branch>> callback);

  @SignInRequired
  void deleteBranch(Project.NameKey projectName, Set<Branch.NameKey> ids,
      AsyncCallback<Set<Branch.NameKey>> callback);
}
