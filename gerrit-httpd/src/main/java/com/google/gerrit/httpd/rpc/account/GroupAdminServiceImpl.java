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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.GroupAdminService;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;

class GroupAdminServiceImpl extends BaseServiceImplementation implements
    GroupAdminService {
  private final GroupCache groupCache;

  private final GroupDetailHandler.Factory groupDetailFactory;

  @Inject
  GroupAdminServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final GroupCache groupCache,
      final GroupDetailHandler.Factory groupDetailFactory) {
    super(schema, currentUser);
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
  }

  public void groupDetail(AccountGroup.Id groupId, AccountGroup.UUID groupUUID,
      AsyncCallback<GroupDetail> callback) {
    if (groupId == null && groupUUID != null) {
      AccountGroup g = groupCache.get(groupUUID);
      if (g != null) {
        groupId = g.getId();
      }
    }
    groupDetailFactory.create(groupId).to(callback);
  }
}
