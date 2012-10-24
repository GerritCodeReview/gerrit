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

package com.google.gerrit.pgm.init;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.realm.RealmConfigurationInitializer;
import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.guice.RealmProviderUtil;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import java.util.Set;

import javax.annotation.Nullable;

/** Initialize the {@code auth} configuration section. */
@Singleton
class InitAuth implements InitStep {
  private final ConsoleUI ui;
  private final Section auth;
  private final DynamicSet<RealmProvider> realmProviders;
  private Injector injector;

  @Inject
  InitAuth(
      final ConsoleUI ui,
      final Injector injector,
      final Section.Factory sections,
      final DynamicSet<RealmProvider> realmProviders) {
    this.ui = ui;
    this.injector = injector;
    this.realmProviders = realmProviders;
    this.auth = sections.get("auth", null);
  }

  public void run() {
    ui.header("User Authentication");
    Set<String> providerNames = getProviderNames();
    String selection =
        auth.select("Authentication method", "type", "OPENID", providerNames);
    initProvider(selection);

    if (auth.getSecure("registerEmailPrivateKey") == null) {
      auth.setSecure("registerEmailPrivateKey", SignedToken.generateRandomKey());
    }

    if (auth.getSecure("restTokenPrivateKey") == null) {
      auth.setSecure("restTokenPrivateKey", SignedToken.generateRandomKey());
    }
  }

  private Set<String> getProviderNames() {
    Iterable<String> providerNamesRaw = RealmProviderUtil.getNames(realmProviders);
    providerNamesRaw = Iterables.transform(providerNamesRaw, new Function<String, String>() {
      @Override
      @Nullable
      public String apply(@Nullable String input) {
        return input.toLowerCase();
      }
    });
    return Sets.newHashSet(providerNamesRaw);
  }

  private void initProvider(String selection) {
    Optional<RealmProvider> provider =
        RealmProviderUtil.getByName(realmProviders, selection);
    RealmConfigurationInitializer initializer =
        injector.getInstance(provider.get().getConfigurationInitializer());
    initializer.init();
  }
}
