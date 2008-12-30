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

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.rpc.SignInRequired;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.Set;

public interface AdminService extends RemoteJsonService {
  @SignInRequired
  void groupDetail(AccountGroup.Id groupId,
      AsyncCallback<AccountGroupDetail> callback);

  @SignInRequired
  void changeGroupDescription(AccountGroup.Id groupId, String description,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void addGroupMember(AccountGroup.Id groupId, String nameOrEmail,
      AsyncCallback<AccountGroupDetail> callback);

  @SignInRequired
  void deleteGroupMembers(Set<AccountGroupMember.Key> keys,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void changeGroupOwner(AccountGroupMember.Key key, boolean owner,
      AsyncCallback<VoidResult> callback);
}
