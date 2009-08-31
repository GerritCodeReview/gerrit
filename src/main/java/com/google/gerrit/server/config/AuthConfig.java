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

import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AuthType;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.spearce.jgit.lib.Config;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Authentication related settings from {@code gerrit.config}. */
@Singleton
public class AuthConfig {
  private final AuthType authType;
  private final String httpHeader;
  private final String logoutUrl;
  private final String[] trusted;
  private final SignedToken emailReg;

  private final AccountGroup.Id administratorGroup;
  private final Set<AccountGroup.Id> anonymousGroups;
  private final Set<AccountGroup.Id> registeredGroups;

  private final boolean allowGoogleAccountUpgrade;

  @Inject
  AuthConfig(@GerritServerConfig final Config cfg, final SystemConfig s)
      throws XsrfException {
    authType = toType(cfg);
    httpHeader = cfg.getString("auth", null, "httpheader");
    logoutUrl = cfg.getString("auth", null, "logouturl");
    trusted = toTrusted(cfg);
    emailReg = new SignedToken(5 * 24 * 60 * 60, s.registerEmailPrivateKey);

    final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>(2);
    r.add(s.anonymousGroupId);
    r.add(s.registeredGroupId);
    registeredGroups = Collections.unmodifiableSet(r);
    anonymousGroups = Collections.singleton(s.anonymousGroupId);
    administratorGroup = s.adminGroupId;

    if (authType == AuthType.OPENID) {
      allowGoogleAccountUpgrade =
          cfg.getBoolean("auth", "allowgoogleaccountupgrade", false);
    } else {
      allowGoogleAccountUpgrade = false;
    }
  }

  private String[] toTrusted(final Config cfg) {
    final String[] r = cfg.getStringList("auth", null, "trustedopenid");
    if (r.length == 0) {
      return new String[] {"http://", "https://"};
    }
    return r;
  }

  private static AuthType toType(final Config cfg) {
    if (isBecomeAnyoneEnabled()) {
      return AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT;
    }
    return ConfigUtil.getEnum(cfg, "auth", null, "type", AuthType.OPENID);
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
  public AuthType getAuthType() {
    return authType;
  }

  public String getLoginHttpHeader() {
    return httpHeader;
  }

  public String getLogoutURL() {
    return logoutUrl;
  }

  public SignedToken getEmailRegistrationToken() {
    return emailReg;
  }

  public boolean isAllowGoogleAccountUpgrade() {
    return allowGoogleAccountUpgrade;
  }

  /** Identity of the magic group with full powers. */
  public AccountGroup.Id getAdministratorsGroup() {
    return administratorGroup;
  }

  /** Groups that all users, including anonymous users, belong to. */
  public Set<AccountGroup.Id> getAnonymousGroups() {
    return anonymousGroups;
  }

  /** Groups that all users who have created an account belong to. */
  public Set<AccountGroup.Id> getRegisteredGroups() {
    return registeredGroups;
  }

  public boolean isIdentityTrustable(final Collection<AccountExternalId> ids) {
    switch (getAuthType()) {
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case HTTP:
      case HTTP_LDAP:
        // Its safe to assume yes for an HTTP authentication type, as the
        // only way in is through some external system that the admin trusts
        //
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

    for (final String p : trusted) {
      if (matches(p, id)) {
        return true;
      }
    }
    return false;
  }

  private boolean matches(final String p, final AccountExternalId id) {
    if (p.startsWith("^") && p.endsWith("$")) {
      return id.getExternalId().matches(p);

    } else {
      return id.getExternalId().startsWith(p);
    }
  }
}
