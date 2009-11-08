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

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_GERRIT;
import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_MAILTO;

/**
 * Information for {@link AccountManager#authenticate(AuthRequest)}.
 * <p>
 * Callers should populate this object with as much information as possible
 * about the user account. For example, OpenID authentication might return
 * registration information including a display name for the user, and an email
 * address for them. These fields however are optional, as not all OpenID
 * providers return them, and not all non-OpenID systems can use them.
 */
public class AuthRequest {
  /** Create a request for a local username, such as from LDAP. */
  public static AuthRequest forUser(final String username) {
    final AuthRequest r;
    r = new AuthRequest(SCHEME_GERRIT + username);
    r.setSshUserName(username);
    return r;
  }

  /**
   * Create a request for an email address registration.
   * <p>
   * This type of request should be used only to attach a new email address to
   * an existing user account.
   */
  public static AuthRequest forEmail(final String email) {
    final AuthRequest r;
    r = new AuthRequest(SCHEME_MAILTO + email);
    r.setEmailAddress(email);
    return r;
  }

  private final String externalId;
  private String password;
  private String displayName;
  private String emailAddress;
  private String sshUserName;

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

  public String getSshUserName() {
    return sshUserName;
  }

  public void setSshUserName(final String user) {
    sshUserName = user;
  }
}
