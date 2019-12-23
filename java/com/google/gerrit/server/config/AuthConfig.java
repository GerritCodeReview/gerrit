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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_UUID;

import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.auth.openid.OpenIdProviderPattern;
import com.google.gerrit.server.mail.SignedToken;
import com.google.gerrit.server.mail.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** Authentication related settings from {@code gerrit.config}. */
@Singleton
public class AuthConfig {
  static final int DEFAULT_HTTP_PASSWORD_LENGTH = 42;
  static final int MIN_HTTP_PASSWORD_LENGTH = 16;
  static final int MAX_HTTP_PASSWORD_LENGTH = 72;
  static final String SECTION_NAME = "auth";
  private final AuthType authType;
  private final String httpHeader;
  private final String httpDisplaynameHeader;
  private final String httpEmailHeader;
  private final String httpExternalIdHeader;
  private final String registerPageUrl;
  private final String registerUrl;
  private final String registerText;
  private final boolean trustContainerAuth;
  private final boolean enableRunAs;
  private final boolean userNameToLowerCase;
  private final boolean useContributorAgreements;
  private final String loginUrl;
  private final String loginText;
  private final String logoutUrl;
  private final String switchAccountUrl;
  private final String editFullNameUrl;
  private final String httpPasswordUrl;
  private final String openIdSsoUrl;
  private final List<String> openIdDomains;
  private final List<OpenIdProviderPattern> trustedOpenIDs;
  private final List<OpenIdProviderPattern> allowedOpenIDs;
  private final String cookiePath;
  private final String cookieDomain;
  private final boolean cookieSecure;
  private final SignedToken emailReg;
  private final boolean allowRegisterNewEmail;
  private GitBasicAuthPolicy gitBasicAuthPolicy;
  private int httpPasswordLength;

  @Inject
  AuthConfig(@GerritServerConfig Config cfg) throws XsrfException {
    authType = toType(cfg);
    httpHeader = cfg.getString(SECTION_NAME, null, "httpheader");
    httpDisplaynameHeader = cfg.getString(SECTION_NAME, null, "httpdisplaynameheader");
    httpEmailHeader = cfg.getString(SECTION_NAME, null, "httpemailheader");
    httpExternalIdHeader = cfg.getString(SECTION_NAME, null, "httpexternalidheader");
    loginUrl = cfg.getString(SECTION_NAME, null, "loginurl");
    loginText = cfg.getString(SECTION_NAME, null, "logintext");
    logoutUrl = cfg.getString(SECTION_NAME, null, "logouturl");
    switchAccountUrl = cfg.getString(SECTION_NAME, null, "switchAccountUrl");
    editFullNameUrl = cfg.getString(SECTION_NAME, null, "editFullNameUrl");
    httpPasswordUrl = cfg.getString(SECTION_NAME, null, "httpPasswordUrl");
    registerPageUrl = cfg.getString(SECTION_NAME, null, "registerPageUrl");
    registerUrl = cfg.getString(SECTION_NAME, null, "registerUrl");
    registerText = cfg.getString(SECTION_NAME, null, "registerText");
    openIdSsoUrl = cfg.getString(SECTION_NAME, null, "openidssourl");
    openIdDomains = Arrays.asList(cfg.getStringList(SECTION_NAME, null, "openIdDomain"));
    trustedOpenIDs = toPatterns(cfg, "trustedOpenID");
    allowedOpenIDs = toPatterns(cfg, "allowedOpenID");
    cookiePath = cfg.getString(SECTION_NAME, null, "cookiepath");
    cookieDomain = cfg.getString(SECTION_NAME, null, "cookiedomain");
    cookieSecure = cfg.getBoolean(SECTION_NAME, "cookiesecure", false);
    trustContainerAuth = cfg.getBoolean(SECTION_NAME, "trustContainerAuth", false);
    enableRunAs = cfg.getBoolean(SECTION_NAME, null, "enableRunAs", true);
    gitBasicAuthPolicy = getBasicAuthPolicy(cfg);
    useContributorAgreements = cfg.getBoolean(SECTION_NAME, "contributoragreements", false);
    userNameToLowerCase = cfg.getBoolean(SECTION_NAME, "userNameToLowerCase", false);
    allowRegisterNewEmail = cfg.getBoolean(SECTION_NAME, "allowRegisterNewEmail", true);
    httpPasswordLength =
        cfg.getInt(SECTION_NAME, "httpPasswordLength", DEFAULT_HTTP_PASSWORD_LENGTH);

    if (gitBasicAuthPolicy == GitBasicAuthPolicy.HTTP_LDAP
        && authType != AuthType.LDAP
        && authType != AuthType.LDAP_BIND) {
      throw new IllegalStateException(
          "use auth.gitBasicAuthPolicy HTTP_LDAP only with auth.type LDAP or LDAP_BIND");
    } else if (gitBasicAuthPolicy == GitBasicAuthPolicy.OAUTH && authType != AuthType.OAUTH) {
      throw new IllegalStateException(
          "use auth.gitBasicAuthPolicy OAUTH only with auth.type OAUTH");
    }

    String key = cfg.getString(SECTION_NAME, null, "registerEmailPrivateKey");
    if (key != null && !key.isEmpty()) {
      int age =
          (int)
              ConfigUtil.getTimeUnit(
                  cfg,
                  SECTION_NAME,
                  null,
                  "maxRegisterEmailTokenAge",
                  TimeUnit.SECONDS.convert(12, TimeUnit.HOURS),
                  TimeUnit.SECONDS);
      emailReg = new SignedToken(age, key);
    } else {
      emailReg = null;
    }

    if (httpPasswordLength < MIN_HTTP_PASSWORD_LENGTH
        || httpPasswordLength > MAX_HTTP_PASSWORD_LENGTH) {
      throw new IllegalStateException(
          "auth.httpPasswordLength must be greater than "
              + MIN_HTTP_PASSWORD_LENGTH
              + " and smaller than "
              + MAX_HTTP_PASSWORD_LENGTH);
    }
  }

  private static List<OpenIdProviderPattern> toPatterns(Config cfg, String name) {
    String[] s = cfg.getStringList(SECTION_NAME, null, name);
    if (s.length == 0) {
      s = new String[] {"http://", "https://"};
    }

    List<OpenIdProviderPattern> r = new ArrayList<>();
    for (String pattern : s) {
      r.add(OpenIdProviderPattern.create(pattern));
    }
    return Collections.unmodifiableList(r);
  }

  private static AuthType toType(Config cfg) {
    return cfg.getEnum(SECTION_NAME, null, "type", AuthType.OPENID);
  }

  private GitBasicAuthPolicy getBasicAuthPolicy(Config cfg) {
    GitBasicAuthPolicy defaultAuthPolicy =
        isLdapAuthType()
            ? GitBasicAuthPolicy.LDAP
            : isOAuthType() ? GitBasicAuthPolicy.OAUTH : GitBasicAuthPolicy.HTTP;
    return cfg.getEnum(SECTION_NAME, null, "gitBasicAuthPolicy", defaultAuthPolicy);
  }

  /** Type of user authentication used by this Gerrit server. */
  public AuthType getAuthType() {
    return authType;
  }

  public String getLoginHttpHeader() {
    return httpHeader;
  }

  public String getHttpDisplaynameHeader() {
    return httpDisplaynameHeader;
  }

  public String getHttpEmailHeader() {
    return httpEmailHeader;
  }

  public String getHttpExternalIdHeader() {
    return httpExternalIdHeader;
  }

  public String getLoginUrl() {
    return loginUrl;
  }

  public String getLoginText() {
    return loginText;
  }

  public String getLogoutURL() {
    return logoutUrl;
  }

  public String getSwitchAccountUrl() {
    return switchAccountUrl;
  }

  public String getEditFullNameUrl() {
    return editFullNameUrl;
  }

  public String getHttpPasswordUrl() {
    return httpPasswordUrl;
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

  public String getCookieDomain() {
    return cookieDomain;
  }

  public boolean getCookieSecure() {
    return cookieSecure;
  }

  public SignedToken getEmailRegistrationToken() {
    return emailReg;
  }

  /** OpenID identities which the server permits for authentication. */
  public List<OpenIdProviderPattern> getAllowedOpenIDs() {
    return allowedOpenIDs;
  }

  /** Whether git-over-http should trust authentication done by container. */
  public boolean isTrustContainerAuth() {
    return trustContainerAuth;
  }

  /** @return true if users with Run As capability can impersonate others. */
  public boolean isRunAsEnabled() {
    return enableRunAs;
  }

  /** Whether user name should be converted to lower-case before validation */
  public boolean isUserNameToLowerCase() {
    return userNameToLowerCase;
  }

  public GitBasicAuthPolicy getGitBasicAuthPolicy() {
    return gitBasicAuthPolicy;
  }

  /** Whether contributor agreements are used. */
  public boolean isUseContributorAgreements() {
    return useContributorAgreements;
  }

  public boolean isIdentityTrustable(Collection<ExternalId> ids) {
    switch (getAuthType()) {
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case HTTP:
      case HTTP_LDAP:
      case LDAP:
      case LDAP_BIND:
      case CLIENT_SSL_CERT_LDAP:
      case CUSTOM_EXTENSION:
      case OAUTH:
        // only way in is through some external system that the admin trusts
        //
        return true;

      case OPENID_SSO:
        // There's only one provider in SSO mode, so it must be okay.
        return true;

      case OPENID:
        // All identities must be trusted in order to trust the account.
        //
        for (ExternalId e : ids) {
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

  private boolean isTrusted(ExternalId id) {
    if (id.isScheme(SCHEME_MAILTO)) {
      // mailto identities are created by sending a unique validation
      // token to the address and asking them to come back to the site
      // with that token.
      //
      return true;
    }

    if (id.isScheme(SCHEME_UUID)) {
      // UUID identities are absolutely meaningless and cannot be
      // constructed through any normal login process we use.
      //
      return true;
    }

    if (id.isScheme(SCHEME_USERNAME)) {
      // We can trust their username, its local to our server only.
      //
      return true;
    }

    for (OpenIdProviderPattern p : trustedOpenIDs) {
      if (p.matches(id)) {
        return true;
      }
    }
    return false;
  }

  public String getRegisterPageUrl() {
    return registerPageUrl;
  }

  public String getRegisterUrl() {
    return registerUrl;
  }

  public String getRegisterText() {
    return registerText;
  }

  public boolean isLdapAuthType() {
    return authType == AuthType.LDAP || authType == AuthType.LDAP_BIND;
  }

  public boolean isOAuthType() {
    return authType == AuthType.OAUTH;
  }

  public boolean isAllowRegisterNewEmail() {
    return allowRegisterNewEmail;
  }

  public int getHttpPasswordLength() {
    return httpPasswordLength;
  }
}
