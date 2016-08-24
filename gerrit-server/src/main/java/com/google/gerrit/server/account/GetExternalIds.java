// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class GetExternalIds implements RestReadView<AccountResource> {
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> self;
  private final AuthConfig authConfig;

  @Inject
  GetExternalIds(Provider<ReviewDb> db,
      Provider<CurrentUser> self,
      AuthConfig authConfig) {
    this.db = db;
    this.self = self;
    this.authConfig = authConfig;
  }

  @Override
  public List<ExternalIdInfo> apply(AccountResource resource)
      throws RestApiException {
    IdentifiedUser user = resource.getUser();
    if (self.get() != user) {
      throw new AuthException("not allowed to get external IDs");
    }
    try {
      List<AccountExternalId> ids = db.get().accountExternalIds().byAccount(
          user.getAccountId()).toList();
      if (ids.isEmpty()) {
        return ImmutableList.of();
      }
      List<ExternalIdInfo> result = Lists.newArrayListWithCapacity(ids.size());
      for (AccountExternalId id : ids) {
        ExternalIdInfo info = new ExternalIdInfo();
        info.identity = id.getExternalId();
        info.emailAddress = id.getEmailAddress();
        info.trusted = authConfig.isIdentityTrustable(Collections.singleton(id));
        // The identity can be deleted only if its not the one used to
        // establish this web session, and if only if an identity was
        // actually used to establish this web session.
        //
        if (id.isScheme(SCHEME_USERNAME)) {
          info.canDelete = false;
        } else {
          CurrentUser.PropertyKey<AccountExternalId.Key> k =
              CurrentUser.PropertyKey.create();
          AccountExternalId.Key last = user.get(k);
          info.canDelete = last != null && !last.get().equals(info.identity);
        }
        result.add(info);
      }
      return result;
    } catch (OrmException e) {
      throw new RestApiException("Cannot get external IDs", e);
    }
  }

  static class ExternalIdInfo {
    String identity;
    String emailAddress;
    Boolean trusted;
    Boolean canDelete;
  }

}
