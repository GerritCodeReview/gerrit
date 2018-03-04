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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_EXTERNAL;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_GERRIT;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_MAILTO;

import com.google.gerrit.reviewdb.client.AccountExternalId;

/**
 * Information for {@link AccountManager#authenticate(AuthRequest)}.
 *
 * <p>Callers should populate this object with as much information as possible about the user
 * account. For example, OpenID authentication might return registration information including a
 * display name for the user, and an email address for them. These fields however are optional, as
 * not all OpenID providers return them, and not all non-OpenID systems can use them.
 */
public class AuthRequest {
  /** Create a request for a local username, such as from LDAP. */
  public static AuthRequest forUser(final String username) {
    final AccountExternalId.Key i = new AccountExternalId.Key(SCHEME_GERRIT, username);
    final AuthRequest r = new AuthRequest(i.get());
    r.setUserName(username);
    return r;
  }

  /** Create a request for an external username. */
  public static AuthRequest forExternalUser(String username) {
    AccountExternalId.Key i = new AccountExternalId.Key(SCHEME_EXTERNAL, username);
    AuthRequest r = new AuthRequest(i.get());
    r.setUserName(username);
    return r;
  }

  /**
   * Create a request for an email address registration.
   *
   * <p>This type of request should be used only to attach a new email address to an existing user
   * account.
   */
  public static AuthRequest forEmail(final String email) {
    final AccountExternalId.Key i = new AccountExternalId.Key(SCHEME_MAILTO, email);
    final AuthRequest r = new AuthRequest(i.get());
    r.setEmailAddress(email);
    return r;
  }

  private String externalId;
  private String password;
  private String displayName;
  private String emailAddress;
  private String userName;
  private boolean skipAuthentication;
  private String authPlugin;
  private String authProvider;

  public AuthRequest(final String externalId) {
    this.externalId = externalId;
  }

  public String getExternalId() {
    return externalId;
  }

  public boolean isScheme(final String scheme) {
    return getExternalId().startsWith(scheme);
  }

  public String getLocalUser() {
    if (isScheme(SCHEME_GERRIT)) {
      return getExternalId().substring(SCHEME_GERRIT.length());
    }
    return null;
  }

  public void setLocalUser(final String localUser) {
    if (isScheme(SCHEME_GERRIT)) {
      final AccountExternalId.Key key = new AccountExternalId.Key(SCHEME_GERRIT, localUser);
      externalId = key.get();
    }
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String pass) {
    password = pass;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(final String name) {
    displayName = name != null && name.length() > 0 ? name : null;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(final String email) {
    emailAddress = email != null && email.length() > 0 ? email : null;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(final String user) {
    userName = user;
  }

  public boolean isSkipAuthentication() {
    return skipAuthentication;
  }

  public void setSkipAuthentication(boolean skip) {
    skipAuthentication = skip;
  }

  public String getAuthPlugin() {
    return authPlugin;
  }

  public void setAuthPlugin(String authPlugin) {
    this.authPlugin = authPlugin;
  }

  public String getAuthProvider() {
    return authProvider;
  }

  public void setAuthProvider(String authProvider) {
    this.authProvider = authProvider;
  }
}
