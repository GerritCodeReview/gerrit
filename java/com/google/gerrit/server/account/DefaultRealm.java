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

package com.google.gerrit.server.account;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;

@Singleton
public class DefaultRealm extends AbstractRealm {
  private final EmailExpander emailExpander;
  private final Provider<Emails> emails;
  private final AuthConfig authConfig;

  @Inject
  @VisibleForTesting
  public DefaultRealm(EmailExpander emailExpander, Provider<Emails> emails, AuthConfig authConfig) {
    this.emailExpander = emailExpander;
    this.emails = emails;
    this.authConfig = authConfig;
  }

  @Override
  public boolean allowsEdit(AccountFieldName field) {
    if (authConfig.getAuthType() == AuthType.HTTP) {
      switch (field) {
        case USER_NAME:
          return false;
        case FULL_NAME:
          return Strings.emptyToNull(authConfig.getHttpDisplaynameHeader()) == null;
        case REGISTER_NEW_EMAIL:
          return authConfig.isAllowRegisterNewEmail()
              && Strings.emptyToNull(authConfig.getHttpEmailHeader()) == null;
        default:
          return true;
      }
    }
    switch (field) {
      case REGISTER_NEW_EMAIL:
        return authConfig.isAllowRegisterNewEmail();
      case FULL_NAME:
      case USER_NAME:
      default:
        return true;
    }
  }

  @Override
  public AuthRequest authenticate(AuthRequest who) {
    if (who.getEmailAddress() == null
        && who.getLocalUser() != null
        && emailExpander.canExpand(who.getLocalUser())) {
      who.setEmailAddress(emailExpander.expand(who.getLocalUser()));
    }
    return who;
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {}

  @Override
  public Account.Id lookup(String accountName) throws IOException {
    if (emailExpander.canExpand(accountName)) {
      try {
        Set<Account.Id> c = emails.get().getAccountFor(emailExpander.expand(accountName));
        if (1 == c.size()) {
          return c.iterator().next();
        }
      } catch (OrmException e) {
        throw new IOException("Failed to query accounts by email", e);
      }
    }
    return null;
  }
}
