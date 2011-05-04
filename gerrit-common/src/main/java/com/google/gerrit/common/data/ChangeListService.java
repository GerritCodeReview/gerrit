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
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.RpcImpl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtjsonrpc.client.RpcImpl.Version;

import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface ChangeListService extends RemoteJsonService {
  /** Get all changes which match an arbitrary query string. */
  void allQueryPrev(String query, String pos, int limit,
      AsyncCallback<SingleListChangeInfo> callback);

  /** Get all changes which match an arbitrary query string. */
  void allQueryNext(String query, String pos, int limit,
      AsyncCallback<SingleListChangeInfo> callback);

  /** Get the data to show AccountDashboardScreen for an account. */
  void forAccount(Account.Id id, AsyncCallback<AccountDashboardInfo> callback);

  /**
   * Get the data from a remote server to show AccountDashboardScreen for an
   * account.
   */
  void forAccountPreferredEmail(String preferredEmail,
      AsyncCallback<AccountDashboardInfo> callback);

  /** Get the ids of all changes starred by the caller. */
  @SignInRequired
  void myStarredChangeIds(AsyncCallback<Set<Change.Id>> callback);

  /**
   * Add and/or remove changes from the set of starred changes of the caller.
   *
   * @param req the add and remove cluster.
   */
  @SignInRequired
  void toggleStars(ToggleStarRequest req, AsyncCallback<VoidResult> callback);
}
