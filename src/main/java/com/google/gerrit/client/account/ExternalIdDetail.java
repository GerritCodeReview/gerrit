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

package com.google.gerrit.client.account;

import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.TrustedExternalId;
import com.google.gerrit.client.rpc.Common;

import java.util.List;

public class ExternalIdDetail {
  protected List<AccountExternalId> ids;
  protected List<TrustedExternalId> trusted;

  protected ExternalIdDetail() {
  }

  public ExternalIdDetail(final List<AccountExternalId> myIds,
      final List<TrustedExternalId> siteTrusts) {
    ids = myIds;
    trusted = siteTrusts;
  }

  public List<AccountExternalId> getIds() {
    return ids;
  }

  public boolean isTrusted(final AccountExternalId id) {
    switch (Common.getGerritConfig().getLoginType()) {
      case HTTP:
        return true;
      case OPENID:
      default:
        return TrustedExternalId.isTrusted(id, trusted);
    }
  }
}
