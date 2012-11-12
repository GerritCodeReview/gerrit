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

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Iterables.size;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.gerrit.extensions.registration.DynamicSet;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UniversalAuthBackend implements AuthBackend {
  private final DynamicSet<AuthBackend> authBackends;

  @Inject
  UniversalAuthBackend(DynamicSet<AuthBackend> authBackends) {
    this.authBackends = authBackends;
  }

  @Override
  public AuthUser authenticate(final AuthRequest request) throws AuthException,
      NoCredentialsException {
    Iterable<AuthUser> authResults = getAuthUsers(request);
    if (isEmpty(authResults)) {
      throw new NoCredentialsException(request);
    } else if (size(authResults) > 1) {
      String ambiguosBackends =
          Joiner.on(", ").join(
              transform(authResults, new Function<AuthUser, String>() {
                public String apply(AuthUser input) {
                  return input.getClass().getName();
                };
              }));
      String msg =
          String.format("Ambiguous AuthBackend response from: %s",
              ambiguosBackends);
      throw new AuthException(msg);
    } else { // shold be exactly one authResult
      return getLast(authResults);
    }
  }

  @Override
  public String getDomain() {
    throw new UnsupportedOperationException(
        "UniversalAuthBackend doesn't support schema");
  }

  private Iterable<AuthUser> getAuthUsers(final AuthRequest request)
      throws AuthException {
    final List<AuthException> exceptions = new ArrayList<AuthException>();
    Iterable<AuthUser> authResults =
        transform(authBackends, new Function<AuthBackend, AuthUser>() {
          @Override
          @Nullable
          public AuthUser apply(@Nullable AuthBackend input) {
            if (input == null) {
              return null;
            }

            try {
              return input.authenticate(request);
            } catch (AuthException e) {
              exceptions.add(e);
              return null;
            }
          }
        });

    if (!exceptions.isEmpty()) {
      throw new AuthException(exceptions);
    }
    return authResults;
  }
}
