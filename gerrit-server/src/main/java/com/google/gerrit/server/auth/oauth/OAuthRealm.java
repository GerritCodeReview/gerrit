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

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AbstractRealm;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OAuthRealm extends AbstractRealm {
  static final Logger log = LoggerFactory.getLogger(OAuthRealm.class);

  private final DynamicItem<OAuthLoginService> loginService;

  @Inject
  OAuthRealm(DynamicItem<OAuthLoginService> loginService) {
    this.loginService = loginService;
  }

  @Override
  public boolean allowsEdit(FieldName field) {
    return false;
  }

  /**
   * Authenticates with the registered {@link OAuthLoginService}.
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
   * the OAuth2 server failed.
   */
  @Override
  public AuthRequest authenticate(AuthRequest who) throws AccountException {
    if (who.getPassword() == null && who.getEmailAddress() != null) {
      return who;
    }

    if (loginService.get() == null) {
      log.warn("no implementation of OAuthLoginService available");
      return who;
    }

    OAuthUserInfo userInfo = loginService.get()
        .login(who.getUserName(), who.getPassword());
    if (userInfo.getEmailAddress() != null) {
      who.setEmailAddress(userInfo.getEmailAddress());
    }
    if (userInfo.getDisplayName() != null) {
      who.setDisplayName(userInfo.getDisplayName());
    }
    return who;
  }

  @Override
  public AuthRequest link(ReviewDb db, Id to, AuthRequest who) {
    return who;
  }

  @Override
  public AuthRequest unlink(ReviewDb db, Id to, AuthRequest who)
      throws AccountException {
    return who;
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {
  }

  @Override
  public Id lookup(String accountName) {
    return null;
  }
}
