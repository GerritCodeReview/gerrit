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

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.inject.Injector;
import com.google.inject.Module;

import javax.annotation.Nullable;

public class Plugin {
  private final String name;
  private final ClassLoader loader;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private LifecycleManager manager;

  public Plugin(String name, ClassLoader loader,
      @Nullable Class<? extends Module> sysModule,
      @Nullable Class<? extends Module> sshModule) {
    this.name = name;
    this.loader = loader;
    this.sysModule = sysModule;
    this.sshModule = sshModule;
  }

  public String getName() {
    return name;
  }

  public void start(PluginEnvironment env) throws Exception {
    Module sysmod = sysModule != null ? sysModule.newInstance() : null;
    Module sshmod = sshModule != null ? sshModule.newInstance() : null;
    manager = new LifecycleManager();

    if (sysmod != null) {
      sysInjector = env.getSysInjector().createChildInjector(sysmod);
      manager.add(sysInjector);
    }
    if (sshmod != null && env.getSshInjector() != null) {
      sshInjector = env.getSshInjector().createChildInjector(sshmod);
      manager.add(sshInjector);
    }
    manager.start();
  }

  public void stop() {
    if (manager != null) {
      manager.stop();

      sysInjector = null;
      sshInjector = null;
      manager = null;
    }
  }

  @Nullable
  public Injector getSshInjector() {
    return sshInjector;
  }

  public void add(final RegistrationHandle handle) {
    add(new LifecycleListener() {
      @Override
      public void start() {
      }

      @Override
      public void stop() {
        handle.remove();
      }
    });
  }

  public void add(LifecycleListener listener) {
    manager.add(listener);
  }

  @Override
  public String toString() {
    return "Plugin [" + name + "]";
  }
}
