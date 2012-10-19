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

package com.google.gerrit.httpd.auth.openid;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;

public class OpenIdRealm extends DefaultRealm {

  private AuthConfig authConfig;

  @Inject
  OpenIdRealm(final AuthConfig authConfig, final EmailExpander emailExpander,
      final AccountByEmailCache byEmail) {
    super(emailExpander, byEmail);
    this.authConfig = authConfig;
  }

  @Override
  public void setAdditionalConfiguraction(GerritConfig config) {
    config.setAllowedOpenIDs(authConfig.getAllowedOpenIDs());
  }
}
