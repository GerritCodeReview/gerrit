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

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

@Singleton
public class PluginEnvironment {
  private final Injector sysInjector;
  private final Set<StartPluginListener> sysListeners;

  private Injector sshInjector;
  private Set<StartPluginListener> sshListeners;

  @Inject
  PluginEnvironment(Injector sysInjector, Set<StartPluginListener> sysListeners) {
    this.sysInjector = sysInjector;
    this.sysListeners = sysListeners;
    this.sshListeners = Collections.emptySet();
  }

  public Injector getSysInjector() {
    return sysInjector;
  }

  public Injector getSshInjector() {
    return sshInjector;
  }

  public void setSshInjector(Injector sshInjector) {
    this.sshInjector = sshInjector;
    this.sshListeners = sshInjector.getInstance(
        Key.get(new TypeLiteral<Set<StartPluginListener>>() {}));
  }

  void onStartPlugin(Plugin plugin) {
    for (StartPluginListener l : sysListeners) {
      l.onStartPlugin(plugin);
    }
    for (StartPluginListener l : sshListeners) {
      l.onStartPlugin(plugin);
    }
  }
}
