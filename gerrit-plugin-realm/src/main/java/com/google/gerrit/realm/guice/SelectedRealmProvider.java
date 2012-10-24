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

package com.google.gerrit.realm.guice;

import static com.google.gerrit.realm.guice.RealmProviderUtil.getNames;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.config.GerritServerConfig;
import com.google.inject.Provider;

@Singleton
class SelectedRealmProvider implements Provider<RealmProvider> {
  private final String authType;
  private final DynamicSet<RealmProvider> realmProviders;

  @Inject
  SelectedRealmProvider(DynamicSet<RealmProvider> realmProviders,
      @GerritServerConfig Config config) {
    this.realmProviders = realmProviders;
    this.authType = config.getString("auth", null, "type");
  }

  @Override
  public RealmProvider get() {
    return get(realmProviders, authType);
  }

  static RealmProvider get(Iterable<RealmProvider> realmProviders, final String authType) {
    Optional<RealmProvider> provider = RealmProviderUtil.getByName(realmProviders, authType);
    if (provider.isPresent()) {
      return provider.get();
    }
    Iterable<String> providerNames = getNames(realmProviders);
    String msg =
        String.format(
            "Cannot find realm named: %s in list of available providers: %s.",
            authType, Joiner.on(", ").join(providerNames));
    throw new RuntimeException(msg);
  }
}
