// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.auth;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/** Universal implementation of the AuthBackend that works with the injected set of AuthBackends. */
@Singleton
public final class UniversalAuthBackend implements AuthBackend {
  private final DynamicSet<AuthBackend> authBackends;

  @Inject
  UniversalAuthBackend(DynamicSet<AuthBackend> authBackends) {
    this.authBackends = authBackends;
  }

  @Override
  public AuthUser authenticate(final AuthRequest request) throws AuthException {
    List<AuthUser> authUsers = new ArrayList<>();
    List<AuthException> authExs = new ArrayList<>();
    for (AuthBackend backend : authBackends) {
      try {
        authUsers.add(checkNotNull(backend.authenticate(request)));
      } catch (MissingCredentialsException ex) {
        // Not handled by this backend.
      } catch (AuthException ex) {
        authExs.add(ex);
      }
    }

    // Handle the valid responses
    if (authUsers.size() == 1) {
      return authUsers.get(0);
    } else if (authUsers.isEmpty() && authExs.size() == 1) {
      throw authExs.get(0);
    } else if (authExs.isEmpty() && authUsers.isEmpty()) {
      throw new MissingCredentialsException();
    }

    String msg =
        String.format(
            "Multiple AuthBackends attempted to handle request:" + " authUsers=%s authExs=%s",
            authUsers, authExs);
    throw new AuthException(msg);
  }

  @Override
  public String getDomain() {
    throw new UnsupportedOperationException("UniversalAuthBackend doesn't support domain.");
  }
}
