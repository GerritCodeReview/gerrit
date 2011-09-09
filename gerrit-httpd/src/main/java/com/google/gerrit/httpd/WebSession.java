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

package com.google.gerrit.httpd;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AuthResult;

public interface WebSession {
  public boolean isSignedIn();

  public String getToken();

  public boolean isTokenValid(String inputToken);

  public AccountExternalId.Key getLastLoginExternalId();

  public CurrentUser getCurrentUser();

  public void login(AuthResult res, boolean rememberMe);

  /** Change the access path from the default of {@link AccessPath#WEB_UI}. */
  public void setAccessPath(AccessPath path);

  /** Set the user account for this current request only. */
  public void setUserAccountId(Account.Id id);

  public void logout();
}
