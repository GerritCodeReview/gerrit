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

import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.RealmExtension;
import com.google.inject.Injector;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class RealmProvider implements Provider<Realm> {
  private final Injector injector;
  private final Provider<RealmExtension> rep;

  @Inject
  RealmProvider(Provider<RealmExtension> realmExtensionProvider, Injector injector) {
    rep = realmExtensionProvider;
    this.injector = injector;
  }

  @Override
  public Realm get() {
    return injector.getInstance(rep.get().getRealm());
  }

}
