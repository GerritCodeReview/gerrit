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

package com.google.gerrit.server.config;

import com.google.gerrit.client.reviewdb.LoginType;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.spearce.jgit.lib.Config;

/** Authentication related settings from {@code gerrit.config}. */
@Singleton
public class AuthConfig {
  private final int sessionAge;
  private final LoginType loginType;
  private final String httpHeader;
  private final String emailFormat;

  private final SignedToken xsrfToken;
  private final SignedToken accountToken;
  private final SignedToken emailReg;

  private final boolean allowGoogleAccountUpgrade;

  @Inject
  AuthConfig(@GerritServerConfig final Config cfg, final SystemConfig s)
      throws XsrfException {
    sessionAge = cfg.getInt("auth", "maxsessionage", 12 * 60) * 60;
    loginType = toType(cfg);
    httpHeader = cfg.getString("auth", null, "httpheader");
    emailFormat = cfg.getString("auth", null, "emailformat");

    xsrfToken = new SignedToken(getSessionAge(), s.xsrfPrivateKey);
    final int accountCookieAge;
    switch (getLoginType()) {
      case HTTP:
        accountCookieAge = -1; // expire when the browser closes
        break;
      case OPENID:
      default:
        accountCookieAge = getSessionAge();
        break;
    }
    accountToken = new SignedToken(accountCookieAge, s.accountPrivateKey);
    emailReg = new SignedToken(5 * 24 * 60 * 60, s.accountPrivateKey);

    allowGoogleAccountUpgrade =
        cfg.getBoolean("auth", "allowgoogleaccountupgrade", false);
  }

  private static LoginType toType(final Config cfg) {
    if (isBecomeAnyoneEnabled()) {
      return LoginType.DEVELOPMENT_BECOME_ANY_ACCOUNT;
    }
    String type = cfg.getString("auth", null, "type");
    if (type == null) {
      return LoginType.OPENID;
    }
    for (LoginType t : LoginType.values()) {
      if (type.equalsIgnoreCase(t.name())) {
        return t;
      }
    }
    throw new IllegalStateException("Unsupported auth.type: " + type);
  }

  private static boolean isBecomeAnyoneEnabled() {
    try {
      String s = "com.google.gerrit.server.http.BecomeAnyAccountLoginServlet";
      return Boolean.getBoolean(s);
    } catch (SecurityException se) {
      return false;
    }
  }

  /** Type of user authentication used by this Gerrit server. */
  public LoginType getLoginType() {
    return loginType;
  }

  public String getLoginHttpHeader() {
    return httpHeader;
  }

  public String getEmailFormat() {
    return emailFormat;
  }

  /** Time (in seconds) that user sessions stay "signed in". */
  public int getSessionAge() {
    return sessionAge;
  }

  public SignedToken getXsrfToken() {
    return xsrfToken;
  }

  public SignedToken getAccountToken() {
    return accountToken;
  }

  public SignedToken getEmailRegistrationToken() {
    return emailReg;
  }

  public boolean isAllowGoogleAccountUpgrade() {
    return allowGoogleAccountUpgrade;
  }
}
