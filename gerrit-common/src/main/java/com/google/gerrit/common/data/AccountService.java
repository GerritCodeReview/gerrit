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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.common.RpcImpl;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtjsonrpc.common.RpcImpl.Version;

import java.util.List;
import java.util.Set;

@RpcImpl(version = Version.V2_0)
public interface AccountService extends RemoteJsonService {
  @SignInRequired
  void myAccount(AsyncCallback<Account> callback);

  @SignInRequired
  void myDiffPreferences(AsyncCallback<AccountDiffPreference> callback);

  @Audit
  @SignInRequired
  void changePreferences(AccountGeneralPreferences pref,
      AsyncCallback<VoidResult> gerritCallback);

  @Audit
  @SignInRequired
  void changeDiffPreferences(AccountDiffPreference diffPref,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void myProjectWatch(AsyncCallback<List<AccountProjectWatchInfo>> callback);

  @Audit
  @SignInRequired
  void addProjectWatch(String projectName, String filter,
      AsyncCallback<AccountProjectWatchInfo> callback);

  @Audit
  @SignInRequired
  void updateProjectWatch(AccountProjectWatch watch,
      AsyncCallback<VoidResult> callback);

  @Audit
  @SignInRequired
  void deleteProjectWatches(Set<AccountProjectWatch.Key> keys,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void myAgreements(AsyncCallback<AgreementInfo> callback);
}
