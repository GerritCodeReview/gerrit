// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.rpc.account;

import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.rpc.Handler;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.List;

class ExternalIdDetailFactory extends Handler<List<AccountExternalId>> {
  interface Factory {
    ExternalIdDetailFactory create();
  }

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final AuthConfig authConfig;

  @Inject
  ExternalIdDetailFactory(final ReviewDb db, final IdentifiedUser user,
      final AuthConfig authConfig) {
    this.db = db;
    this.user = user;
    this.authConfig = authConfig;
  }

  @Override
  public List<AccountExternalId> call() throws Exception {
    final List<AccountExternalId> ids =
        db.accountExternalIds().byAccount(user.getAccountId()).toList();
    for (final AccountExternalId e : ids) {
      e.setTrusted(authConfig.isIdentityTrustable(Collections.singleton(e)));
    }
    return ids;
  }
}
