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

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.auth.AuthUser.UUID;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.annotation.Nullable;

/**
 * Default implementation of {@link RealmBackend} with search all registered
 * {@link RealmBackend} implementations and returns data from from first
 * backend that has non-null value
 */
@Singleton
public class UniversalRealmBackend implements RealmBackend {
  private final DynamicSet<RealmBackend> realmBackends;

  @Inject
  UniversalRealmBackend(DynamicSet<RealmBackend> realmBackends) {
    this.realmBackends = realmBackends;
  }

  @Override
  public boolean handles(UUID uuid) {
    return findBackend(uuid).isPresent();
  }

  @Override
  public Optional<UserData> getUserData(AuthUser user) {
    Optional<RealmBackend> backend = findBackend(user.getUUID());
    if (backend.isPresent()) {
      return backend.get().getUserData(user);
    }
    return Optional.absent();
  }

  private Optional<RealmBackend> findBackend(final AuthUser.UUID uuid) {
    return Iterables.tryFind(realmBackends, new Predicate<RealmBackend>() {
      @Override
      public boolean apply(@Nullable RealmBackend input) {
        return input != null && input.handles(uuid);
      }
    });
  }
}
