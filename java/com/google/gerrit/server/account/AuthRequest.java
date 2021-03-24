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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.account.externalids.ExternalId;
import java.util.Optional;

/**
 * Information for {@link AccountManager#authenticate(AuthRequest)}.
 *
 * <p>Callers should populate this object with as much information as possible about the user
 * account. For example, OpenID authentication might return registration information including a
 * display name for the user, and an email address for them. These fields however are optional, as
 * not all OpenID providers return them, and not all non-OpenID systems can use them.
 */
public class AuthRequest {
  private ExternalId.Key externalId;
  private String password;
  private String displayName;
  private String emailAddress;
  private Optional<String> userName = Optional.empty();
  private boolean skipAuthentication;
  private String authPlugin;
  private String authProvider;
  private boolean authProvidesAccountActiveStatus;
  private boolean active;

  public AuthRequest(ExternalId.Key externalId) {
    this.externalId = externalId;
  }

  public ExternalId.Key getExternalIdKey() {
    return externalId;
  }

  public String getLocalUser() {
    if (externalId.isScheme(SCHEME_GERRIT)) {
      return externalId.id();
    }
    return null;
  }

  public void setLocalUser(String localUser) {
    if (externalId.isScheme(SCHEME_GERRIT)) {
      externalId = ExternalId.Key.create(SCHEME_GERRIT, localUser, externalId.isCaseInsensitive());
    }
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String pass) {
    password = pass;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String name) {
    displayName = name != null && name.length() > 0 ? name : null;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String email) {
    emailAddress = email != null && email.length() > 0 ? email : null;
  }

  public Optional<String> getUserName() {
    return userName;
  }

  public void setUserName(@Nullable String user) {
    userName = Optional.ofNullable(Strings.emptyToNull(user));
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

  public boolean authProvidesAccountActiveStatus() {
    return authProvidesAccountActiveStatus;
  }

  public void setAuthProvidesAccountActiveStatus(boolean authProvidesAccountActiveStatus) {
    this.authProvidesAccountActiveStatus = authProvidesAccountActiveStatus;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(Boolean isActive) {
    this.active = isActive;
  }
}
