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
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AbstractRealm;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

@Singleton
public class OAuthRealm extends AbstractRealm {
  private final DynamicMap<OAuthLoginProvider> loginProviders;
  private final Set<AccountFieldName> editableAccountFields;

  @Inject
  OAuthRealm(DynamicMap<OAuthLoginProvider> loginProviders, @GerritServerConfig Config config) {
    this.loginProviders = loginProviders;
    this.editableAccountFields = new HashSet<>();
    // User name should be always editable, because not all OAuth providers
    // expose them
    editableAccountFields.add(AccountFieldName.USER_NAME);
    if (config.getBoolean("oauth", null, "allowEditFullName", false)) {
      editableAccountFields.add(AccountFieldName.FULL_NAME);
    }
    if (config.getBoolean("oauth", null, "allowRegisterNewEmail", false)) {
      editableAccountFields.add(AccountFieldName.REGISTER_NEW_EMAIL);
    }
  }

  @Override
  public boolean allowsEdit(AccountFieldName field) {
    return editableAccountFields.contains(field);
  }

  /**
   * Authenticates with the {@link OAuthLoginProvider} specified in the authentication request.
   *
   * <p>{@link AccountManager} calls this method without password if authenticity of the user has
   * already been established. In that case we can skip the authentication request to the {@code
   * OAuthLoginService}.
   *
   * @param who the authentication request.
   * @return the authentication request with resolved email address and display name in case the
   *     authenticity of the user could be established; otherwise {@code who} is returned unchanged.
   * @throws AccountException if the authentication request with the OAuth2 server failed or no
   *     {@code OAuthLoginProvider} was available to handle the request.
   */
  @Override
  public AuthRequest authenticate(AuthRequest who) throws AccountException {
    if (Strings.isNullOrEmpty(who.getPassword())) {
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
    if (!Strings.isNullOrEmpty(userInfo.getEmailAddress())
        && (Strings.isNullOrEmpty(who.getUserName())
            || !allowsEdit(AccountFieldName.REGISTER_NEW_EMAIL))) {
      who.setEmailAddress(userInfo.getEmailAddress());
    }
    if (!Strings.isNullOrEmpty(userInfo.getDisplayName())
        && (Strings.isNullOrEmpty(who.getDisplayName())
            || !allowsEdit(AccountFieldName.FULL_NAME))) {
      who.setDisplayName(userInfo.getDisplayName());
    }
    return who;
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {}

  @Override
  public Account.Id lookup(String accountName) {
    return null;
  }
}
