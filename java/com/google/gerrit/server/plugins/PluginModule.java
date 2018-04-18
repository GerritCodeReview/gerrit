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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.plugins.classloader.JarPluginClassLoader;

public class PluginModule extends LifecycleModule {
  @Override
  protected void configure() {
    bind(ServerInformationImpl.class);
    bind(ServerInformation.class).to(ServerInformationImpl.class);

    factory(JarPluginClassLoader.Factory.class);
    bind(PluginCleanerTask.class);
    bind(PluginGuiceEnvironment.class);
    bind(PluginLoader.class);
    bind(CopyConfigModule.class);
    listener().to(PluginLoader.class);

    DynamicSet.setOf(binder(), ServerPluginProvider.class);
    DynamicSet.bind(binder(), ServerPluginProvider.class).to(JarPluginProvider.class);
    bind(UniversalServerPluginProvider.class);
  }
}
