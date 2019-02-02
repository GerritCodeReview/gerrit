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

package com.google.gerrit.server;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class StartupChecks implements LifecycleListener {
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicSet.setOf(binder(), StartupCheck.class);
      listener().to(StartupChecks.class);
      DynamicSet.bind(binder(), StartupCheck.class).to(UniversalGroupBackend.ConfigCheck.class);
      DynamicSet.bind(binder(), StartupCheck.class).to(SystemGroupBackend.NameCheck.class);
    }
  }

  private final PluginSetContext<StartupCheck> startupChecks;

  @Inject
  StartupChecks(PluginSetContext<StartupCheck> startupChecks) {
    this.startupChecks = startupChecks;
  }

  @Override
  public void start() throws StartupException {
    startupChecks.runEach(StartupCheck::check, StartupException.class);
  }

  @Override
  public void stop() {}
}
