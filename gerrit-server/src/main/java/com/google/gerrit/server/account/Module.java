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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.config.FactoryModule;

public class Module extends FactoryModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), AccountResource.VIEW_TYPE);

    bind(AccountResource.VIEW_TYPE)
      .annotatedWith(Exports.named("GET.capabilities"))
      .to(Capabilities.class);
  }
}
