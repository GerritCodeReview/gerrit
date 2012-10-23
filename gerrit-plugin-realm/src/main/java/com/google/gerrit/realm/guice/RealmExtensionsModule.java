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

import static com.google.inject.Scopes.SINGLETON;

import javax.inject.Provider;

import com.google.gerrit.realm.Realm;
import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.RealmServletModule;
import com.google.gerrit.realm.cache.RealmCacheModule;
import com.google.inject.AbstractModule;

public class RealmExtensionsModule extends AbstractModule {

  @Override
  protected void configure() {
    install(new RealmProvidersModule());

    bindProviderSingleton(Realm.class, RealmImplProvider.class);
    bindProviderSingleton(RealmProvider.class, SelectedRealmProvider.class);
    bindProviderSingleton(RealmCacheModule.class, RealmCacheModuleProvider.class);
    bindProviderSingleton(RealmServletModule.class, RealmServletModuleProvider.class);
  }

  private <T> void bindProviderSingleton(Class<T> clazz,
      Class<? extends Provider<T>> impl) {
    bind(clazz).toProvider(impl).in(SINGLETON);
  }
}
