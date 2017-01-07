// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetAccountExternalIds implements RestReadView<AccountResource> {
  private final ReviewDb db;
  private final AuthConfig authConfig;

  @Inject
  GetAccountExternalIds(final ReviewDb db,
      final AuthConfig authConfig) {
    this.db = db;
    this.authConfig = authConfig;
  }

  @Override
  public List<AccountExternalIdInfo> apply(AccountResource rsrc)
      throws AuthException, ResourceNotFoundException, OrmException {
    List<AccountExternalIdInfo> idInfoList = new ArrayList<>();
    final List<AccountExternalId> ids = db.accountExternalIds().
        byAccount(rsrc.getUser().getAccountId()).toList();

    for (final AccountExternalId id : ids) {
      id.setTrusted(authConfig.isIdentityTrustable(Collections.singleton(id)));

      // The identity can be deleted only if its not the one used to
      // establish this web session, and if only if an identity was
      // actually used to establish this web session.
      if (id.isScheme(SCHEME_USERNAME)) {
        id.setCanDelete(false);
      } else {
        // To do
        // id.setCanDelete(last != null && !last.equals(e.getKey()));
      }
      idInfoList.add(toInfo(id));
    }

    Collections.sort(idInfoList);
    return idInfoList;
  }

  @VisibleForTesting
  public static AccountExternalIdInfo toInfo(AccountExternalId id) {
    AccountExternalIdInfo info = new AccountExternalIdInfo();
    info.externalId = id.getExternalId();
    info.accountId = id.getAccountId().get();
    info.email = id.getEmailAddress();
    info.password = id.getPassword();
    info.trusted = id.isTrusted();
    info.canDelete = id.canDelete();
    return info;
  }
}
