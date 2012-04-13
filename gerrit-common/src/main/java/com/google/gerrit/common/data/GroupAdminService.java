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

import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.RpcImpl;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtjsonrpc.common.RpcImpl.Version;

import java.util.List;
import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface GroupAdminService extends RemoteJsonService {
  @Audit
  @SignInRequired
  void visibleGroups(AsyncCallback<GroupList> callback);

  @Audit
  @SignInRequired
  void createGroup(String newName, AsyncCallback<AccountGroup.Id> callback);

  @Audit
  @SignInRequired
  void groupDetail(AccountGroup.Id groupId, AccountGroup.UUID uuid,
      AsyncCallback<GroupDetail> callback);

  @Audit
  @SignInRequired
  void changeGroupDescription(AccountGroup.Id groupId, String description,
      AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void changeGroupOptions(AccountGroup.Id groupId, GroupOptions groupOptions,
      AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void changeGroupOwner(AccountGroup.Id groupId, String newOwnerName,
      AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void renameGroup(AccountGroup.Id groupId, String newName,
      AsyncCallback<GroupDetail> callback);

  @Audit
  @SignInRequired
  void changeGroupType(AccountGroup.Id groupId, AccountGroup.Type newType,
      AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void changeExternalGroup(AccountGroup.Id groupId,
      AccountGroup.ExternalNameKey bindTo, AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void searchExternalGroups(String searchFilter,
      AsyncCallback<List<AccountGroup.ExternalNameKey>> callback);

  @Audit
  @SignInRequired
  void addGroupMember(AccountGroup.Id groupId, String nameOrEmail,
      AsyncCallback<GroupDetail> callback);

  @Audit
  @SignInRequired
  void addGroupInclude(AccountGroup.Id groupId, String groupName,
      AsyncCallback<GroupDetail> callback);

  @Audit
  @SignInRequired
  void deleteGroupMembers(AccountGroup.Id groupId,
      Set<AccountGroupMember.Key> keys, AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void deleteGroupIncludes(AccountGroup.Id groupId,
      Set<AccountGroupInclude.Key> keys, AsyncCallback<VoidResult> callback);
}
