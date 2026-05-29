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

package com.google.gerrit.server.permissions;

import com.google.inject.PrivateModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

/** Binds the default {@link PermissionBackend}. */
public class DefaultPermissionBackendModule extends PrivateModule {

  @Override
  protected void configure() {
    // TODO(hiesel) Hide ProjectControl, RefControl, ChangeControl related bindings.
    install(new FactoryModuleBuilder().build(DefaultRefFilter.Factory.class));
    installRefControlFactory();
    installChangeControlFactory();
    installProjectControlFactory();
    // Expose only ProjectControl.Factory, so other modules can't use RefControl and ChangeControl.
    expose(ProjectControl.Factory.class);
  }

  protected void installProjectControlFactory() {
    install(new FactoryModuleBuilder().build(ProjectControl.Factory.class));
  }

  protected void installChangeControlFactory() {
    install(new FactoryModuleBuilder().build(ChangeControl.Factory.class));
  }

  protected void installRefControlFactory() {
    install(new FactoryModuleBuilder().build(RefControl.Factory.class));
  }
}
