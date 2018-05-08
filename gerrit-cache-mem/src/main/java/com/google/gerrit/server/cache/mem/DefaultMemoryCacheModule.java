// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.cache.mem;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.ModuleImpl;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
import com.google.gerrit.server.cache.MemoryCacheFactory;

@ModuleImpl(name = CacheModule.MEMORY_MODULE)
public class DefaultMemoryCacheModule extends FactoryModule {
  @Override
  protected void configure() {
    factory(ForwardingRemovalListener.Factory.class);
    bind(MemoryCacheFactory.class).to(DefaultMemoryCacheFactory.class);
  }
}
