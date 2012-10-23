// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.realm.openid;

import java.util.Collection;

import javax.inject.Inject;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.realm.account.AccountByEmailCache;
import com.google.gerrit.realm.account.EmailExpander;
import com.google.gerrit.realm.config.AuthConfig;
import com.google.gerrit.reviewdb.client.AccountExternalId;

public class OpenIdSsoRealm extends OpenIdRealm {

  @Inject
  OpenIdSsoRealm(EmailExpander emailExpander, AccountByEmailCache byEmail,
      AuthConfig ac) {
    super(emailExpander, byEmail, ac);
  }

  @Override
  public void customizeGerritConfig(GerritConfig config) {
    config.setOpenIdSsoUrl(authConfig.getOpenIdSsoUrl());
  }

  @Override
  public boolean isIdentityTrustable(Collection<AccountExternalId> ids) {
    return true;
  }
}
