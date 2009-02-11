// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.rpc.SignInRequired;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;
import java.util.Set;

public interface ProjectAdminService extends RemoteJsonService {
  @SignInRequired
  void ownedProjects(AsyncCallback<List<Project>> callback);

  @SignInRequired
  void projectDetail(Project.Id projectId, AsyncCallback<ProjectDetail> callback);

  @SignInRequired
  void changeProjectDescription(Project.Id projectId, String description,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void changeProjectOwner(Project.Id projectId, String newOwnerName,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void deleteRight(Set<ProjectRight.Key> ids, AsyncCallback<VoidResult> callback);

  @SignInRequired
  void addRight(Project.Id projectId, ApprovalCategory.Id categoryId,
      String groupName, short min, short max,
      AsyncCallback<ProjectDetail> callback);

  @SignInRequired
  void listBranches(Project.Id project, AsyncCallback<List<Branch>> callback);

  @SignInRequired
  void addBranch(Project.Id project, String branchName,
      String startingRevision, AsyncCallback<List<Branch>> callback);

  @SignInRequired
  void deleteBranch(Set<Branch.NameKey> ids,
      AsyncCallback<Set<Branch.NameKey>> callback);
}
