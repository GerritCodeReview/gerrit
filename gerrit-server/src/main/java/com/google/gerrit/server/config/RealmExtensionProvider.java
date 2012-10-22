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

package com.google.gerrit.server.config;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.account.RealmExtension;
import com.google.inject.Provider;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class RealmExtensionProvider implements Provider<RealmExtension> {

  private final String authType;
  private final DynamicSet<RealmExtension> realms;

  @Inject
  RealmExtensionProvider(DynamicSet<RealmExtension> realms, AuthConfig authConfig) {
    this.realms = realms;
    authType = authConfig.getAuthType();
  }

  @Override
  public RealmExtension get() {
    Optional<RealmExtension> extension = Iterables.tryFind(realms, new Predicate<RealmExtension>() {
      public boolean apply(RealmExtension input) {
        return authType.equalsIgnoreCase(input.getName());
      };
    });
    if (!extension.isPresent()) {
      // throw exception
      throw new RuntimeException(String.format("Cannot find Realm provider for name: %s", authType));
    }
    return extension.get();
  }

}
