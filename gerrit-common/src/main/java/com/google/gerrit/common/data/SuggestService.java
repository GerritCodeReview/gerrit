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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.RpcImpl;
import com.google.gwtjsonrpc.client.RpcImpl.Version;

import java.util.List;
import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface SuggestService extends RemoteJsonService {
  void suggestProjectNameKey(String query, int limit,
      AsyncCallback<List<Project.NameKey>> callback);

  void suggestAccount(String query, Boolean enabled, int limit,
      AsyncCallback<List<AccountInfo>> callback);

  void suggestAccountGroup(String query, int limit,
      AsyncCallback<List<GroupReference>> callback);

  void suggestLabel(Change.Id changeId,
      AsyncCallback<Set<ChangeLabel.LabelKey>> callback);

  /**
   * Suggests reviewers. A reviewer can be a user or a group. Inactive users,
   * the system groups {@link AccountGroup#ANONYMOUS_USERS} and
   * {@link AccountGroup#REGISTERED_USERS} and groups that have more than the
   * configured <code>addReviewer.maxAllowed</code> members are not suggested as
   * reviewers.
   * @param project the project for which reviewers should be suggested
   */
  void suggestReviewer(Project.NameKey project, String query, int limit,
      AsyncCallback<List<ReviewerInfo>> callback);
}
