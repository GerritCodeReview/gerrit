// Copyright 2009 Google Inc.
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

package com.google.gerrit.server;

import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.RelyingParty;
import com.dyuproject.openid.manager.CookieBasedUserManager;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

public class GerritOpenIdUserManager extends CookieBasedUserManager {
  public GerritOpenIdUserManager() {
    final int age = 2 * 60/* seconds */;

    setCookieName("gerrit_openid");
    setSecretKey("gerrit_openid");
    setMaxAge(age);
    setLoginTimeout(age);
    setEncrypted(true);
  }

  @Override
  public void init(Properties properties) {
  }

  @Override
  public OpenIdUser getUser(final HttpServletRequest request)
      throws IOException {
    if (request.getParameter(RelyingParty.DEFAULT_IDENTIFIER_PARAMETER) != null) {
      return null;
    }
    return super.getUser(request);
  }
}
