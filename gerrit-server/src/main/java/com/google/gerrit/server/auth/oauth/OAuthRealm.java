// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.auth.oauth;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AbstractRealm;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class OAuthRealm extends AbstractRealm {
  private final DynamicMap<OAuthLoginProvider> loginProviders;

  @Inject
  OAuthRealm(DynamicMap<OAuthLoginProvider> loginProviders) {
    this.loginProviders = loginProviders;
  }

  @Override
  public boolean allowsEdit(FieldName field) {
    return false;
  }

  /**
   * Authenticates with the {@link OAuthLoginProvider} specified
   * in the authentication request.
   *
   * {@link AccountManager} calls this method without password
   * if authenticity of the user has already been established.
   * In that case the {@link AuthRequest} is supposed to contain
   * a resolved email address and we can skip the authentication
   * request to the {@code OAuthLoginService}.
   *
   * @param who the authentication request.
   *
   * @return the authentication request with resolved email address
   * and display name in case the authenticity of the user could
   * be established; otherwise {@code who} is returned unchanged.
   *
   * @throws AccountException if the authentication request with
   * the OAuth2 server failed or no {@code OAuthLoginProvider} was
   * available to handle the request.
   */
  @Override
  public AuthRequest authenticate(AuthRequest who) throws AccountException {
    if (Strings.isNullOrEmpty(who.getPassword()) &&
        !Strings.isNullOrEmpty(who.getEmailAddress())) {
      return who;
    }

    if (Strings.isNullOrEmpty(who.getAuthPlugin())
        || Strings.isNullOrEmpty(who.getAuthProvider())) {
      throw new AccountException("Cannot authenticate");
    }
    OAuthLoginProvider loginProvider =
        loginProviders.get(who.getAuthPlugin(), who.getAuthProvider());
    if (loginProvider == null) {
      throw new AccountException("Cannot authenticate");
    }

    OAuthUserInfo userInfo;
    try {
      userInfo = loginProvider.login(who.getUserName(), who.getPassword());
    } catch (IOException e) {
      throw new AccountException("Cannot authenticate", e);
    }
    if (userInfo == null) {
      throw new AccountException("Cannot authenticate");
    }
    if (!Strings.isNullOrEmpty(userInfo.getEmailAddress())) {
      who.setEmailAddress(userInfo.getEmailAddress());
    }
    if (!Strings.isNullOrEmpty(userInfo.getDisplayName())) {
      who.setDisplayName(userInfo.getDisplayName());
    }
    return who;
  }

  @Override
  public AuthRequest link(ReviewDb db, Account.Id to, AuthRequest who) {
    return who;
  }

  @Override
  public AuthRequest unlink(ReviewDb db, Account.Id to, AuthRequest who)
      throws AccountException {
    return who;
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {
  }

  @Override
  public Account.Id lookup(String accountName) {
    return null;
  }
}
