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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.ExternalIdsConfig;
import com.google.gerrit.server.account.ExternalIdsConfig.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

class ExternalIdDetailFactory extends Handler<List<AccountExternalId>> {
  interface Factory {
    ExternalIdDetailFactory create();
  }

  private final ReviewDb db;
  private final Config cfg;
  private final IdentifiedUser user;
  private final AuthConfig authConfig;
  private final ExternalIdsConfig.Accessor.User externalIdsConfig;
  private final DynamicItem<WebSession> session;

  @Inject
  ExternalIdDetailFactory(ReviewDb db,
      @GerritServerConfig Config cfg,
      IdentifiedUser user,
      AuthConfig authConfig,
      ExternalIdsConfig.Accessor.User externalIdsConfig,
      DynamicItem<WebSession> session) {
    this.db = db;
    this.cfg = cfg;
    this.user = user;
    this.authConfig = authConfig;
    this.externalIdsConfig = externalIdsConfig;
    this.session = session;
  }

  @Override
  public List<AccountExternalId> call()
      throws OrmException, IOException, ConfigInvalidException {
    List<AccountExternalId> ids;
    if (ExternalIdsConfig.readFromGit(cfg)) {
      ids = FluentIterable
          .from(externalIdsConfig.get(user.getAccountId()).values())
          .transform(new Function<ExternalId, AccountExternalId>() {
            @Override
            public AccountExternalId apply(ExternalId externalId) {
              return externalId.asAccountExternalId(user.getAccountId());
            }
          }).toList();
    } else {
      ids = db.accountExternalIds().byAccount(user.getAccountId()).toList();
    }

    for (AccountExternalId e : ids) {
      e.setTrusted(authConfig.isIdentityTrustable(Collections.singleton(e)));

      // The identity can be deleted only if its not the one used to
      // establish this web session, and if only if an identity was
      // actually used to establish this web session.
      //
      if (e.isScheme(SCHEME_USERNAME)) {
        e.setCanDelete(false);
      } else {
        AccountExternalId.Key last = session.get().getLastLoginExternalId();
        e.setCanDelete(last != null && !last.equals(e.getKey()));
      }
    }
    return ids;
  }
}
