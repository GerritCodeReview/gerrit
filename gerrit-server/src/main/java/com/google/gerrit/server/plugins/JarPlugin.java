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
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.storage.file.FileSnapshot;

import java.io.File;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nullable;

class JarPlugin extends Plugin {
  static {
    // Guice logs warnings about multiple injectors being created.
    // Silence this in case HTTP plugins are used.
    java.util.logging.Logger.getLogger("com.google.inject.servlet.GuiceFilter")
        .setLevel(java.util.logging.Level.OFF);
  }

  private final JarFile jarFile;
  private final Manifest manifest;
  private final File dataDir;
  private final ClassLoader classLoader;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;
  private Class<? extends Module> httpModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private Injector httpInjector;

  JarPlugin(String name,
      File srcJar,
      FileSnapshot snapshot,
      JarFile jarFile,
      Manifest manifest,
      File dataDir,
      Plugin.ApiType apiType,
      ClassLoader classLoader,
      @Nullable Class<? extends Module> sysModule,
      @Nullable Class<? extends Module> sshModule,
      @Nullable Class<? extends Module> httpModule) {
    super(name, srcJar, snapshot, apiType);
    this.jarFile = jarFile;
    this.manifest = manifest;
    this.dataDir = dataDir;
    this.classLoader = classLoader;
    this.sysModule = sysModule;
    this.sshModule = sshModule;
    this.httpModule = httpModule;
  }

  @Override
  @Nullable
  public String getVersion() {
    Attributes main = manifest.getMainAttributes();
    return main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
  }

  @Override
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
          getName(), v));
      return false;
    }
  }

  @Override
  public void start(PluginGuiceEnvironment env) throws Exception {
    Injector root = newRootInjector(env);
    manager = new LifecycleManager();

    AutoRegisterModules auto = null;
    if (sysModule == null && sshModule == null && httpModule == null) {
      auto = new AutoRegisterModules(getName(), env, jarFile, classLoader);
      auto.discover();
    }

    if (sysModule != null) {
      sysInjector = root.createChildInjector(root.getInstance(sysModule));
      manager.add(sysInjector);
    } else if (auto != null && auto.sysModule != null) {
      sysInjector = root.createChildInjector(auto.sysModule);
      manager.add(sysInjector);
    } else {
      sysInjector = root;
    }

    if (env.hasSshModule()) {
      List<Module> modules = Lists.newLinkedList();
      if (getApiType() == Plugin.ApiType.PLUGIN) {
        modules.add(env.getSshModule());
      }
      if (sshModule != null) {
        modules.add(sysInjector.getInstance(sshModule));
        sshInjector = sysInjector.createChildInjector(modules);
        manager.add(sshInjector);
      } else if (auto != null && auto.sshModule != null) {
        modules.add(auto.sshModule);
        sshInjector = sysInjector.createChildInjector(modules);
        manager.add(sshInjector);
      }
    }

    if (env.hasHttpModule()) {
      List<Module> modules = Lists.newLinkedList();
      if (getApiType() == Plugin.ApiType.PLUGIN) {
        modules.add(env.getHttpModule());
      }
      if (httpModule != null) {
        modules.add(sysInjector.getInstance(httpModule));
        httpInjector = sysInjector.createChildInjector(modules);
        manager.add(httpInjector);
      } else if (auto != null && auto.httpModule != null) {
        modules.add(auto.httpModule);
        httpInjector = sysInjector.createChildInjector(modules);
        manager.add(httpInjector);
      }
    }

    manager.start();
  }

  private Injector newRootInjector(final PluginGuiceEnvironment env) {
    List<Module> modules = Lists.newArrayListWithCapacity(4);
    modules.add(env.getSysModule());
    if (getApiType() == Plugin.ApiType.PLUGIN) {
      modules.add(env.getSysModule());
    }
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class)
          .annotatedWith(PluginName.class)
          .toInstance(getName());

        bind(File.class)
          .annotatedWith(PluginData.class)
          .toProvider(new Provider<File>() {
            private volatile boolean ready;

            @Override
            public File get() {
              if (!ready) {
                synchronized (dataDir) {
                  if (!dataDir.exists() && !dataDir.mkdirs()) {
                    throw new ProvisionException(String.format(
                        "Cannot create %s for plugin %s",
                        dataDir.getAbsolutePath(), getName()));
                  }
                  ready = true;
                }
              }
              return dataDir;
            }
          });
      }
    });
    return Guice.createInjector(modules);
  }

  @Override
  public void stop() {
    if (manager != null) {
      manager.stop();
      manager = null;
      sysInjector = null;
      sshInjector = null;
      httpInjector = null;
    }
  }

  @Override
  public JarFile getJarFile() {
    return jarFile;
  }

  @Override
  public Injector getSysInjector() {
    return sysInjector;
  }

  @Override
  @Nullable
  public Injector getSshInjector() {
    return sshInjector;
  }

  @Override
  @Nullable
  public Injector getHttpInjector() {
    return httpInjector;
  }
}
