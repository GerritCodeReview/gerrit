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

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.realm.Realm;
import com.google.gerrit.realm.RealmConfigurationInitializer;
import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.config.AuthType;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * Provide {@link RealmConfigurationInitializer} for selected {@link Realm}
 * implementation.
 *
 * Require provider binding for String annotated with {@link AuthType}.
 */
@Singleton
public class RealmConfigurationInitializerProvider implements
    Provider<RealmConfigurationInitializer> {

  private final Injector injector;
  private final Provider<String> authTypeProvider;
  private final DynamicSet<RealmProvider> realmProviders;

  @Inject
  RealmConfigurationInitializerProvider(Injector injector,
      DynamicSet<RealmProvider> providers,
      @AuthType Provider<String> authTypeProvider) {
    this.injector = injector;
    this.realmProviders = providers;
    this.authTypeProvider = authTypeProvider;
  }

  @Override
  public RealmConfigurationInitializer get() {
    RealmProvider realmProvider =
        SelectedRealmProvider.get(realmProviders, authTypeProvider.get());
    return injector.getInstance(realmProvider.getConfigurationInitializer());
  }

}
