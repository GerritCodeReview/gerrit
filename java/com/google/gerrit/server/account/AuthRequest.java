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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_EXTERNAL;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_OAUTH;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
  @Singleton
  public static class Factory {
    private final ExternalIdKeyFactory externalIdKeyFactory;

    @Inject
    public Factory(ExternalIdKeyFactory externalIdKeyFactory) {
      this.externalIdKeyFactory = externalIdKeyFactory;
    }

    public AuthRequest create(ExternalId.Key externalIdKey) {
      return new AuthRequest(externalIdKey, externalIdKeyFactory);
    }

    /** Create a request for a local username, such as from LDAP. */
    public AuthRequest createForUser(String userName) {
      AuthRequest r =
          new AuthRequest(
              externalIdKeyFactory.create(SCHEME_GERRIT, userName), externalIdKeyFactory);
      r.setUserName(userName);
      return r;
    }

    /** Create a request for an external username. */
    public AuthRequest createForExternalUser(String userName) {
      AuthRequest r =
          new AuthRequest(
              externalIdKeyFactory.create(SCHEME_EXTERNAL, userName), externalIdKeyFactory);
      r.setUserName(userName);
      return r;
    }

    public AuthRequest createForOAuthUser(String userName) {
      AuthRequest r =
          new AuthRequest(
              externalIdKeyFactory.create(SCHEME_OAUTH, userName), externalIdKeyFactory);
      r.setUserName(userName);
      return r;
    }

    /**
     * Create a request for an email address registration.
     *
     * <p>This type of request should be used only to attach a new email address to an existing user
     * account.
     */
    public AuthRequest createForEmail(String email) {
      AuthRequest r =
          new AuthRequest(externalIdKeyFactory.create(SCHEME_MAILTO, email), externalIdKeyFactory);
      r.setEmailAddress(email);
      return r;
    }
  }

  private final ExternalIdKeyFactory externalIdKeyFactory;

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

  private AuthRequest(ExternalId.Key externalId, ExternalIdKeyFactory externalIdKeyFactory) {
    this.externalId = externalId;
    this.externalIdKeyFactory = externalIdKeyFactory;
  }

  public ExternalId.Key getExternalIdKey() {
    return externalId;
  }

  @Nullable
  public String getLocalUser() {
    if (externalId.isScheme(SCHEME_GERRIT)) {
      return externalId.id();
    }
    return null;
  }

  public void setLocalUser(String localUser) {
    if (externalId.isScheme(SCHEME_GERRIT)) {
      externalId = externalIdKeyFactory.create(SCHEME_GERRIT, localUser);
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
