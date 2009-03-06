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

package com.google.gerrit.client.account;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.rpc.SignInRequired;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;
import java.util.Set;

public interface AccountSecurity extends RemoteJsonService {
  @SignInRequired
  void mySshKeys(AsyncCallback<List<AccountSshKey>> callback);

  @SignInRequired
  void addSshKey(String keyText, AsyncCallback<AccountSshKey> callback);

  @SignInRequired
  void deleteSshKeys(Set<AccountSshKey.Id> ids,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void myExternalIds(AsyncCallback<ExternalIdDetail> callback);

  @SignInRequired
  void deleteExternalIds(Set<AccountExternalId.Key> keys,
      AsyncCallback<Set<AccountExternalId.Key>> callback);

  @SignInRequired
  void updateContact(String fullName, String emailAddr,
      ContactInformation info, AsyncCallback<Account> callback);

  @SignInRequired
  void enterAgreement(ContributorAgreement.Id id,
      AsyncCallback<VoidResult> callback);

  @SignInRequired
  void registerEmail(String address, AsyncCallback<VoidResult> callback);

  @SignInRequired
  void validateEmail(String token, AsyncCallback<VoidResult> callback);
}
