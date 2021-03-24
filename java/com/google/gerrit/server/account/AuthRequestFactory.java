// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.inject.Inject;

public class AuthRequestFactory {
  private final ExternalIdKeyFactory externalIdKeyFactory;

  @Inject
  public AuthRequestFactory(ExternalIdKeyFactory externalIdKeyFactory) {
    this.externalIdKeyFactory = externalIdKeyFactory;
  }

  /** Create a request for a local username, such as from LDAP. */
  public AuthRequest createForUser(String username) {
    AuthRequest r = new AuthRequest(externalIdKeyFactory.create(SCHEME_GERRIT, username));
    r.setUserName(username);
    return r;
  }

  /** Create a request for an external username. */
  public AuthRequest createForExternalUser(String username) {
    AuthRequest r = new AuthRequest(externalIdKeyFactory.create(SCHEME_EXTERNAL, username));
    r.setUserName(username);
    return r;
  }

  /**
   * Create a request for an email address registration.
   *
   * <p>This type of request should be used only to attach a new email address to an existing user
   * account.
   */
  public AuthRequest createForEmail(String email) {
    AuthRequest r = new AuthRequest(externalIdKeyFactory.create(SCHEME_MAILTO, email));
    r.setEmailAddress(email);
    return r;
  }
}
