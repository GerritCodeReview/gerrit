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

import com.google.gerrit.common.auth.openid.OpenIdProviderPattern;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.realm.DefaultRealm;
import com.google.gerrit.realm.account.AccountByEmailCache;
import com.google.gerrit.realm.account.EmailExpander;
import com.google.gerrit.realm.config.AuthConfig;
import com.google.gerrit.reviewdb.client.AccountExternalId;

public class OpenIdRealm extends DefaultRealm {
  protected final AuthConfig authConfig;

  @Inject
  OpenIdRealm(EmailExpander emailExpander, AccountByEmailCache byEmail,
      AuthConfig ac) {
    super(emailExpander, byEmail);
    this.authConfig = ac;
  }

  @Override
  public void customizeGerritConfig(GerritConfig config) {
    config.setAllowedOpenIDs(authConfig.getAllowedOpenIDs());
  }

  @Override
  public boolean isIdentityTrustable(Collection<AccountExternalId> ids) {
    for (final AccountExternalId e : ids) {
      if (!isTrusted(e)) {
        return false;
      }
    }
    return true;
  }

  private boolean isTrusted(final AccountExternalId id) {
    if (id.isScheme(AccountExternalId.LEGACY_GAE)) {
      // Assume this is a trusted token, its a legacy import from
      // a fairly well respected provider and only takes effect if
      // the administrator has the import still enabled
      //
      return authConfig.isAllowGoogleAccountUpgrade();
    }

    if (id.isScheme(AccountExternalId.SCHEME_MAILTO)) {
      // mailto identities are created by sending a unique validation
      // token to the address and asking them to come back to the site
      // with that token.
      //
      return true;
    }

    if (id.isScheme(AccountExternalId.SCHEME_UUID)) {
      // UUID identities are absolutely meaningless and cannot be
      // constructed through any normal login process we use.
      //
      return true;
    }

    if (id.isScheme(AccountExternalId.SCHEME_USERNAME)) {
      // We can trust their username, its local to our server only.
      //
      return true;
    }

    for (final OpenIdProviderPattern p : authConfig.getTrustedOpenIDs()) {
      if (p.matches(id)) {
        return true;
      }
    }
    return false;
  }
}
