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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
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
  private final DynamicItem<WebSession> session;

  @Inject
  ExternalIdDetailFactory(
      final ReviewDb db,
      final IdentifiedUser user,
      final AuthConfig authConfig,
      final DynamicItem<WebSession> session) {
    this.db = db;
    this.user = user;
    this.authConfig = authConfig;
    this.session = session;
  }

  @Override
  public List<AccountExternalId> call() throws OrmException {
    final AccountExternalId.Key last = session.get().getLastLoginExternalId();
    final List<AccountExternalId> ids =
        db.accountExternalIds().byAccount(user.getAccountId()).toList();

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
