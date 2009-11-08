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
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;

class UserPassAuthServiceImpl implements UserPassAuthService {
  private final Provider<WebSession> webSession;
  private final AccountManager accountManager;

  @Inject
  UserPassAuthServiceImpl(final Provider<WebSession> webSession,
      final AccountManager accountManager) {
    this.webSession = webSession;
    this.accountManager = accountManager;
  }

  @Override
  public void authenticate(final String username, final String password,
      final AsyncCallback<LoginResult> callback) {
    LoginResult result = new LoginResult();
    if (username == null || "".equals(username) //
        || password == null || "".equals(password)) {
      result.success = false;
      callback.onSuccess(result);
      return;
    }

    final AuthRequest req = AuthRequest.forUser(username);
    req.setPassword(password);

    final AuthResult res;
    try {
      res = accountManager.authenticate(req);
    } catch (AccountException e) {
      result.success = false;
      callback.onSuccess(result);
      return;
    }

    result.success = true;
    result.isNew = res.isNew();
    webSession.get().login(res.getAccountId(), false);
    callback.onSuccess(result);
  }
}
