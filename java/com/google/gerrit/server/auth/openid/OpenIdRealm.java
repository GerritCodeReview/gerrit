// Copyright (C) 2021 Open Infrastructure Foundation
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

package com.google.gerrit.server.auth.openid;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_HTTP;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_HTTPS;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_XRI;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;

@Singleton
public class OpenIdRealm extends DefaultRealm {
  @Inject
  @VisibleForTesting
  public OpenIdRealm(EmailExpander emailExpander, Provider<Emails> emails, AuthConfig authConfig) {
    super(emailExpander, emails, authConfig);
  }

  @Override
  public boolean accountBelongsToRealm(Collection<ExternalId> externalIds) {
    for (ExternalId id : externalIds) {
      if (id.isScheme(SCHEME_HTTP) || id.isScheme(SCHEME_HTTPS) || id.isScheme(SCHEME_XRI)) {
        return true;
      }
    }
    return false;
  }
}
