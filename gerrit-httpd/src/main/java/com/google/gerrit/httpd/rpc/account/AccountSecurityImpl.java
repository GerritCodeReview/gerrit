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

import com.google.gerrit.common.data.AccountSecurity;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Set;

class AccountSecurityImpl extends BaseServiceImplementation implements AccountSecurity {
  private final DeleteExternalIds.Factory deleteExternalIdsFactory;
  private final ExternalIdDetailFactory.Factory externalIdDetailFactory;

  @Inject
  AccountSecurityImpl(
      final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final DeleteExternalIds.Factory deleteExternalIdsFactory,
      final ExternalIdDetailFactory.Factory externalIdDetailFactory) {
    super(schema, currentUser);
    this.deleteExternalIdsFactory = deleteExternalIdsFactory;
    this.externalIdDetailFactory = externalIdDetailFactory;
  }

  @Override
  public void myExternalIds(AsyncCallback<List<AccountExternalId>> callback) {
    externalIdDetailFactory.create().to(callback);
  }

  @Override
  public void deleteExternalIds(
      final Set<AccountExternalId.Key> keys,
      final AsyncCallback<Set<AccountExternalId.Key>> callback) {
    deleteExternalIdsFactory.create(keys).to(callback);
  }
}
