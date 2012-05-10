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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;

import org.eclipse.jgit.storage.file.FileSnapshot;

import java.io.File;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nullable;

public class Plugin {
  static {
    // Guice logs warnings about multiple injectors being created.
    // Silence this in case HTTP plugins are used.
    java.util.logging.Logger.getLogger(GuiceFilter.class.getName())
        .setLevel(java.util.logging.Level.OFF);
  }

  private final String name;
  private final File srcJar;
  private final FileSnapshot snapshot;
  private final JarFile jarFile;
  private final Manifest manifest;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;
  private Class<? extends Module> httpModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private Injector httpInjector;
  private LifecycleManager manager;

  public Plugin(String name,
      File srcJar,
      FileSnapshot snapshot,
      JarFile jarFile,
      Manifest manifest,
      @Nullable Class<? extends Module> sysModule,
      @Nullable Class<? extends Module> sshModule,
      @Nullable Class<? extends Module> httpModule) {
    this.name = name;
    this.srcJar = srcJar;
    this.snapshot = snapshot;
    this.jarFile = jarFile;
    this.manifest = manifest;
    this.sysModule = sysModule;
    this.sshModule = sshModule;
    this.httpModule = httpModule;
  }

  File getSrcJar() {
    return srcJar;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    Attributes main = manifest.getMainAttributes();
    return main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
  }

  boolean canReload() {
    Attributes main = manifest.getMainAttributes();
    String v = main.getValue("Gerrit-ReloadMode");
    if (Strings.isNullOrEmpty(v) || "reload".equalsIgnoreCase(v)) {
      return true;
    } else if ("restart".equalsIgnoreCase(v)) {
      return false;
    } else {
      PluginLoader.log.warn(String.format(
          "Plugin %s has invalid Gerrit-ReloadMode %s; assuming restart",
          name, v));
      return false;
    }
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

    if (httpModule != null && env.hasHttpModule()) {
      httpInjector = sysInjector.createChildInjector(
          env.getHttpModule(),
          sysInjector.getInstance(httpModule));
      manager.add(httpInjector);
    }

    manager.start();
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
      httpInjector = null;
    }
  }

  public JarFile getJarFile() {
    return jarFile;
  }

  @Nullable
  public Injector getSshInjector() {
    return sshInjector;
  }

  @Nullable
  public Injector getHttpInjector() {
    return httpInjector;
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
