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

package com.google.gerrit.httpd.auth.ldap;

import com.google.gerrit.common.auth.userpass.LoginResult;
import com.google.gerrit.common.auth.userpass.UserPassAuthService;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthRequest;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UserPassAuthServiceImpl implements UserPassAuthService {
  private final Provider<WebSession> webSession;
  private final AccountManager accountManager;
  private final AuthType authType;

  private static final Logger log = LoggerFactory
      .getLogger(UserPassAuthServiceImpl.class);

  @Inject
  UserPassAuthServiceImpl(final Provider<WebSession> webSession,
      final AccountManager accountManager, final AuthConfig authConfig) {
    this.webSession = webSession;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
  }

  @Override
  public void authenticate(String username, final String password,
      final AsyncCallback<LoginResult> callback) {
    LoginResult result = new LoginResult(authType);
    if (username == null || "".equals(username.trim()) //
        || password == null || "".equals(password)) {
      result.setError(LoginResult.Error.INVALID_LOGIN);
      callback.onSuccess(result);
      return;
    }

    username = username.trim();
    AuthRequest req = new AuthRequest(username, password) {};
    final AuthResult res;
    try {
      res = accountManager.authenticate(req);
    } catch (AuthException e) {
      log.info(String.format("'%s' failed to sign in: %s", username, e.getMessage()));
      callback.onFailure(e);
      return;
    }

    result.success = true;
    result.isNew = res.isNew();
    webSession.get().login(res, true /* persistent cookie */);
    callback.onSuccess(result);
  }
}
