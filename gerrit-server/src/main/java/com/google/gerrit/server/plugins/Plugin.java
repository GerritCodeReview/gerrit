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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.eclipse.jgit.storage.file.FileSnapshot;

import java.io.File;

import javax.annotation.Nullable;

public class Plugin {
  private final String name;
  private final FileSnapshot snapshot;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private LifecycleManager manager;

  public Plugin(String name,
      FileSnapshot snapshot,
      @Nullable Class<? extends Module> sysModule,
      @Nullable Class<? extends Module> sshModule) {
    this.name = name;
    this.snapshot = snapshot;
    this.sysModule = sysModule;
    this.sshModule = sshModule;
  }

  public String getName() {
    return name;
  }

  boolean isModified(File jar) {
    return snapshot.lastModified() != jar.lastModified();
  }

  public void start(PluginGuiceEnvironment env) throws Exception {
    Injector root = newRootInjector(env);
    manager = new LifecycleManager();

    if (sysModule != null) {
      sysInjector = root.createChildInjector(root.getInstance(sysModule));
      manager.add(sysInjector);
    } else {
      sysInjector = root;
    }

    if (sshModule != null && env.hasSshModule()) {
      sshInjector = sysInjector.createChildInjector(
          env.getSshModule(),
          sysInjector.getInstance(sshModule));
      manager.add(sshInjector);
    }

    manager.start();
    env.onStartPlugin(this);
  }

  private Injector newRootInjector(PluginGuiceEnvironment env) {
    return Guice.createInjector(
        env.getSysModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class)
              .annotatedWith(PluginName.class)
              .toInstance(name);
          }
        });
  }

  public void stop() {
    if (manager != null) {
      manager.stop();
      manager = null;
      sysInjector = null;
      sshInjector = null;
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
