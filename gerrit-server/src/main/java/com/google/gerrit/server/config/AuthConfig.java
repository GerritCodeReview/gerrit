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

import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.auth.openid.OpenIdProviderPattern;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Authentication related settings from {@code gerrit.config}. */
@Singleton
public class AuthConfig {
  private final AuthType authType;
  private final String httpHeader;
  private final boolean trustContainerAuth;
  private final boolean userNameToLowerCase;
  private final boolean gitBasicAuth;
  private final String logoutUrl;
  private final String openIdSsoUrl;
  private final List<String> openIdDomains;
  private final List<OpenIdProviderPattern> trustedOpenIDs;
  private final List<OpenIdProviderPattern> allowedOpenIDs;
  private final String cookiePath;
  private final boolean cookieSecure;
  private final SignedToken emailReg;
  private final SignedToken restToken;

  private final boolean allowGoogleAccountUpgrade;

  @Inject
  AuthConfig(@GerritServerConfig final Config cfg)
      throws XsrfException {
    authType = toType(cfg);
    httpHeader = cfg.getString("auth", null, "httpheader");
    logoutUrl = cfg.getString("auth", null, "logouturl");
    openIdSsoUrl = cfg.getString("auth", null, "openidssourl");
    openIdDomains = Arrays.asList(cfg.getStringList("auth", null, "openIdDomain"));
    trustedOpenIDs = toPatterns(cfg, "trustedOpenID");
    allowedOpenIDs = toPatterns(cfg, "allowedOpenID");
    cookiePath = cfg.getString("auth", null, "cookiepath");
    cookieSecure = cfg.getBoolean("auth", "cookiesecure", false);
    trustContainerAuth = cfg.getBoolean("auth", "trustContainerAuth", false);
    gitBasicAuth = cfg.getBoolean("auth", "gitBasicAuth", false);
    userNameToLowerCase = cfg.getBoolean("auth", "userNameToLowerCase", false);


    String key = cfg.getString("auth", null, "registerEmailPrivateKey");
    if (key != null && !key.isEmpty()) {
      int age = (int) ConfigUtil.getTimeUnit(cfg,
          "auth", null, "maxRegisterEmailTokenAge",
          TimeUnit.SECONDS.convert(12, TimeUnit.HOURS),
          TimeUnit.SECONDS);
      emailReg = new SignedToken(age, key);
    } else {
      emailReg = null;
    }

    key = cfg.getString("auth", null, "restTokenPrivateKey");
    if (key != null && !key.isEmpty()) {
      int age = (int) ConfigUtil.getTimeUnit(cfg,
          "auth", null, "maxRestTokenAge", 60, TimeUnit.SECONDS);
      restToken = new SignedToken(age, key);
    } else {
      restToken = null;
    }

    if (authType == AuthType.OPENID) {
      allowGoogleAccountUpgrade =
          cfg.getBoolean("auth", "allowgoogleaccountupgrade", false);
    } else {
      allowGoogleAccountUpgrade = false;
    }
  }

  private static List<OpenIdProviderPattern> toPatterns(Config cfg, String name) {
    String[] s = cfg.getStringList("auth", null, name);
    if (s.length == 0) {
      s = new String[] {"http://", "https://"};
    }

    List<OpenIdProviderPattern> r = new ArrayList<OpenIdProviderPattern>();
    for (String pattern : s) {
      r.add(OpenIdProviderPattern.create(pattern));
    }
    return Collections.unmodifiableList(r);
  }

  private static AuthType toType(final Config cfg) {
    return ConfigUtil.getEnum(cfg, "auth", null, "type", AuthType.OPENID);
  }

  /** Type of user authentication used by this Gerrit server. */
  public AuthType getAuthType() {
    return authType;
  }

  public String getLoginHttpHeader() {
    return httpHeader;
  }

  public String getLogoutURL() {
    return logoutUrl;
  }

  public String getOpenIdSsoUrl() {
    return openIdSsoUrl;
  }

  public List<String> getOpenIdDomains() {
    return openIdDomains;
  }

  public String getCookiePath() {
    return cookiePath;
  }

  public boolean getCookieSecure() {
    return cookieSecure;
  }

  public SignedToken getEmailRegistrationToken() {
    return emailReg;
  }

  public SignedToken getRestToken() {
    return restToken;
  }

  public boolean isAllowGoogleAccountUpgrade() {
    return allowGoogleAccountUpgrade;
  }

  /** OpenID identities which the server permits for authentication. */
  public List<OpenIdProviderPattern> getAllowedOpenIDs() {
    return allowedOpenIDs;
  }

  /** Whether git-over-http should trust authentication done by container. */
  public boolean isTrustContainerAuth() {
    return trustContainerAuth;
  }

  /** Whether user name should be converted to lower-case before validation */
  public boolean isUserNameToLowerCase() {
    return userNameToLowerCase;
  }

  /** Whether git-over-http should use Gerrit basic authentication scheme. */
  public boolean isGitBasichAuth() {
    return gitBasicAuth;
  }

  public boolean isIdentityTrustable(final Collection<AccountExternalId> ids) {
    switch (getAuthType()) {
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case HTTP:
      case HTTP_LDAP:
      case LDAP:
      case LDAP_BIND:
      case CLIENT_SSL_CERT_LDAP:
      case CUSTOM_EXTENSION:
        // Its safe to assume yes for an HTTP authentication type, as the
        // only way in is through some external system that the admin trusts
        //
        return true;

      case OPENID_SSO:
        // There's only one provider in SSO mode, so it must be okay.
        return true;

      case OPENID:
        // All identities must be trusted in order to trust the account.
        //
        for (final AccountExternalId e : ids) {
          if (!isTrusted(e)) {
            return false;
          }
        }
        return true;

      default:
        // Assume not, we don't understand the login format.
        //
        return false;
    }
  }

  private boolean isTrusted(final AccountExternalId id) {
    if (id.isScheme(AccountExternalId.LEGACY_GAE)) {
      // Assume this is a trusted token, its a legacy import from
      // a fairly well respected provider and only takes effect if
      // the administrator has the import still enabled
      //
      return isAllowGoogleAccountUpgrade();
    }

    if (id.isScheme(AccountExternalId.SCHEME_MAILTO)) {
      // mailto identities are created by sending a unique validation
      // token to the address and asking them to come back to the site
      // with that token.
      //
      return true;
    }

    if (id.isScheme(AccountExternalId.SCHEME_UUID)) {
      // UUID identities are absolutely meaningless and cannot be
      // constructed through any normal login process we use.
      //
      return true;
    }

    if (id.isScheme(AccountExternalId.SCHEME_USERNAME)) {
      // We can trust their username, its local to our server only.
      //
      return true;
    }

    for (final OpenIdProviderPattern p : trustedOpenIDs) {
      if (p.matches(id)) {
        return true;
      }
    }
    return false;
  }
}
