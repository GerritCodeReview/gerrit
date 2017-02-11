// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.group;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.IncludingGroupMembership;
import com.google.gerrit.server.account.InternalGroupBackend;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.InternalUser;

public class GroupModule extends FactoryModule {

  @Override
  protected void configure() {
    factory(InternalUser.Factory.class);
    factory(IncludingGroupMembership.Factory.class);

    bind(GroupBackend.class).to(UniversalGroupBackend.class).in(SINGLETON);
    DynamicSet.setOf(binder(), GroupBackend.class);

    bind(InternalGroupBackend.class).in(SINGLETON);
    DynamicSet.bind(binder(), GroupBackend.class).to(SystemGroupBackend.class);
    DynamicSet.bind(binder(), GroupBackend.class).to(InternalGroupBackend.class);
  }
}
