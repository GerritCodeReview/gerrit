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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;
import static com.google.gerrit.server.account.AccountResource.CAPABILITY_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class Module extends RestApiModule {
  @Override
  protected void configure() {
    bind(AccountsCollection.class);
    bind(Capabilities.class);

    DynamicMap.mapOf(binder(), ACCOUNT_KIND);
    DynamicMap.mapOf(binder(), CAPABILITY_KIND);

    put(ACCOUNT_KIND).to(PutAccount.class);
    get(ACCOUNT_KIND).to(GetAccount.class);
    get(ACCOUNT_KIND, "avatar").to(GetAvatar.class);
    get(ACCOUNT_KIND, "avatar.change.url").to(GetAvatarChangeUrl.class);
    child(ACCOUNT_KIND, "capabilities").to(Capabilities.class);
    get(ACCOUNT_KIND, "groups").to(GetGroups.class);
    get(ACCOUNT_KIND, "preferences.diff").to(GetDiffPreferences.class);
    put(ACCOUNT_KIND, "preferences.diff").to(SetDiffPreferences.class);
    get(CAPABILITY_KIND).to(GetCapabilities.CheckOne.class);

    install(new FactoryModuleBuilder().build(CreateAccount.Factory.class));
  }
}
