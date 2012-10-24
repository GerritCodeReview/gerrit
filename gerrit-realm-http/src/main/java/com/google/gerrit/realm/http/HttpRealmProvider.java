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

package com.google.gerrit.realm.http;

import com.google.gerrit.realm.Realm;
import com.google.gerrit.realm.RealmConfigurationInitializer;
import com.google.gerrit.realm.RealmProvider;
import com.google.gerrit.realm.RealmServletModule;
import com.google.gerrit.realm.cache.RealmCacheModule;
import com.google.gerrit.realm.http.server.HttpAuthModule;

public class HttpRealmProvider implements RealmProvider {

  public static final String NAME = "HTTP";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Class<? extends Realm> getRealm() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RealmServletModule getServletModule() {
    return new HttpAuthModule();
  }

  @Override
  public RealmCacheModule getCacheModule() {
    return RealmProvider.EMPTY_CACHE_MODULE;
  }

  @Override
  public Class<? extends RealmConfigurationInitializer> getConfigurationInitializer() {
    return HttpRealmConfigurationInitializer.class;
  }

}
