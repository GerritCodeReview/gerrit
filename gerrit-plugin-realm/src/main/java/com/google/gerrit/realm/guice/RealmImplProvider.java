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

import com.google.gerrit.realm.Realm;
import com.google.gerrit.realm.RealmProvider;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
class RealmImplProvider implements Provider<Realm> {
  private final Injector injector;
  private final RealmProvider realmProvider;

  @Inject
  RealmImplProvider(Injector injector, RealmProvider realmProvider) {
    this.injector = injector;
    this.realmProvider = realmProvider;
  }

  @Override
  public Realm get() {
    return injector.getInstance(realmProvider.getRealm());
  }
}
