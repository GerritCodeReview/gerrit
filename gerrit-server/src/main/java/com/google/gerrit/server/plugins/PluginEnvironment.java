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

import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

@Singleton
public class PluginEnvironment {
  private final Injector sysInjector;
  private final List<StartPluginListener> listeners;
  private Injector sshInjector;

  @Inject
  PluginEnvironment(Injector sysInjector) {
    this.sysInjector = sysInjector;
    this.listeners = new CopyOnWriteArrayList<StartPluginListener>();
    this.listeners.addAll(get(sysInjector));
  }

  public Injector getSysInjector() {
    return sysInjector;
  }

  public Injector getSshInjector() {
    return sshInjector;
  }

  public void setSshInjector(Injector sshInjector) {
    this.sshInjector = sshInjector;
    listeners.addAll(get(sshInjector));
  }

  void onStartPlugin(Plugin plugin) {
    for (StartPluginListener l : listeners) {
      l.onStartPlugin(plugin);
    }
  }

  private static List<StartPluginListener> get(Injector i) {
    List<Binding<StartPluginListener>> bindings =
        i.findBindingsByType(new TypeLiteral<StartPluginListener>() {});
    List<StartPluginListener> r =
        Lists.newArrayListWithCapacity(bindings.size());
    for (Binding<StartPluginListener> b : bindings) {
      r.add(b.getProvider().get());
    }
    return r;
  }
}
