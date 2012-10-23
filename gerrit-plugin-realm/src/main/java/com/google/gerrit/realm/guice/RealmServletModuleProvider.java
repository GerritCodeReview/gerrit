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

import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.RealmServletModule;
import com.google.inject.Provider;

@Singleton
class RealmServletModuleProvider implements Provider<RealmServletModule> {
  private final RealmProvider realmProvider;

  @Inject
  RealmServletModuleProvider(RealmProvider realmProvider) {
    this.realmProvider = realmProvider;
  }

  @Override
  public RealmServletModule get() {
    return realmProvider.getServletModule();
  }

}
