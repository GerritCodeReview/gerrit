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

package com.google.gerrit.httpd.rpc.account;

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountExternalIdCache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.client.OrmException;
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
  private final WebSession session;
  private final AccountExternalIdCache accountExternalIdCache;

  @Inject
  ExternalIdDetailFactory(final ReviewDb db, final IdentifiedUser user,
      final AuthConfig authConfig, final WebSession session,
      final AccountExternalIdCache accountExternalIdCache) {
    this.db = db;
    this.user = user;
    this.authConfig = authConfig;
    this.session = session;
    this.accountExternalIdCache = accountExternalIdCache;
  }

  @Override
  public List<AccountExternalId> call() {
    final AccountExternalId.Key last = session.getLastLoginExternalId();
    final List<AccountExternalId> ids =
      accountExternalIdCache.byAccount(user.getAccountId());

    for (final AccountExternalId e : ids) {
      e.setTrusted(authConfig.isIdentityTrustable(Collections.singleton(e)));

      // The identity can be deleted only if its not the one used to
      // establish this web session, and if only if an identity was
      // actually used to establish this web session.
      //
      if (e.isScheme(SCHEME_USERNAME)) {
        e.setCanDelete(false);
      } else {
        e.setCanDelete(last != null && !last.equals(e.getKey()));
      }
    }
    return ids;
  }
}
