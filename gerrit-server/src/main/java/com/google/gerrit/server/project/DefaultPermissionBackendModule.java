// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Scopes;

/** Binds the default {@link PermissionBackend}. */
public class DefaultPermissionBackendModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(PermissionBackend.class).to(DefaultPermissionBackend.class).in(Scopes.SINGLETON);

    // TODO(sop) Hide ProjectControl, RefControl, ChangeControl related bindings.
    bind(ProjectControl.GenericFactory.class);
    factory(ProjectControl.AssistedFactory.class);
    bind(ChangeControl.GenericFactory.class);
    bind(ChangeControl.Factory.class);
  }
}
