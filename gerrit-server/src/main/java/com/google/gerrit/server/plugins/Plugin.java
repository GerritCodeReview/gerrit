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
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nullable;

public class Plugin {
  public static enum ApiType {
    EXTENSION, PLUGIN, JS;
  }

  /** Unique key that changes whenever a plugin reloads. */
  public static final class CacheKey {
    private final String name;

    CacheKey(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      int id = System.identityHashCode(this);
      return String.format("Plugin[%s@%x]", name, id);
    }
  }

  static {
    // Guice logs warnings about multiple injectors being created.
    // Silence this in case HTTP plugins are used.
    java.util.logging.Logger.getLogger("com.google.inject.servlet.GuiceFilter")
        .setLevel(java.util.logging.Level.OFF);
  }

  static ApiType getApiType(Manifest manifest) throws InvalidPluginException {
    Attributes main = manifest.getMainAttributes();
    String v = main.getValue("Gerrit-ApiType");
    if (Strings.isNullOrEmpty(v)
        || ApiType.EXTENSION.name().equalsIgnoreCase(v)) {
      return ApiType.EXTENSION;
    } else if (ApiType.PLUGIN.name().equalsIgnoreCase(v)) {
      return ApiType.PLUGIN;
    } else if (ApiType.JS.name().equalsIgnoreCase(v)) {
      return ApiType.JS;
    } else {
      throw new InvalidPluginException("Invalid Gerrit-ApiType: " + v);
    }
  }

  private final CacheKey cacheKey;
  private final String name;
  private final File srcJar;
  private final FileSnapshot snapshot;
  private final JarFile jarFile;
  private final Manifest manifest;
  private final File dataDir;
  private final ApiType apiType;
  private final ClassLoader classLoader;
  private final boolean disabled;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;
  private Class<? extends Module> httpModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private Injector httpInjector;
  private LifecycleManager manager;
  private List<ReloadableRegistrationHandle<?>> reloadableHandles;

  public Plugin(String name,
      File srcJar,
      FileSnapshot snapshot,
      JarFile jarFile,
      Manifest manifest,
      File dataDir,
      ApiType apiType,
      ClassLoader classLoader,
      @Nullable Class<? extends Module> sysModule,
      @Nullable Class<? extends Module> sshModule,
      @Nullable Class<? extends Module> httpModule) {
    this.cacheKey = new CacheKey(name);
    this.name = name;
    this.srcJar = srcJar;
    this.snapshot = snapshot;
    this.jarFile = jarFile;
    this.manifest = manifest;
    this.dataDir = dataDir;
    this.apiType = apiType;
    this.classLoader = classLoader;
    this.disabled = srcJar.getName().endsWith(".disabled");
    this.sysModule = sysModule;
    this.sshModule = sshModule;
    this.httpModule = httpModule;
  }

  File getSrcJar() {
    return srcJar;
  }

  public CacheKey getCacheKey() {
    return cacheKey;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public String getVersion() {
    Attributes main = manifest.getMainAttributes();
    return main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
  }

  public ApiType getApiType() {
    return apiType;
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

  public boolean isDisabled() {
    return disabled;
  }

  void start(PluginGuiceEnvironment env) throws Exception {
    RequestContext oldContext = env.enter(this);
    try {
      startPlugin(env);
    } finally {
      env.exit(oldContext);
    }
  }

  private void startPlugin(PluginGuiceEnvironment env) throws Exception {
    Injector root = newRootInjector(env);
    manager = new LifecycleManager();

    AutoRegisterModules auto = null;
    if (sysModule == null && sshModule == null && httpModule == null) {
      auto = new AutoRegisterModules(name, env, jarFile, classLoader);
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
      if (apiType == ApiType.PLUGIN) {
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
      if (apiType == ApiType.PLUGIN) {
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
    if (apiType == ApiType.PLUGIN) {
      modules.add(env.getSysModule());
    }
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class)
          .annotatedWith(PluginName.class)
          .toInstance(name);

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
                        dataDir.getAbsolutePath(), name));
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

  void stop(PluginGuiceEnvironment env) {
    if (manager != null) {
      RequestContext oldContext = env.enter(this);
      try {
        manager.stop();
      } finally {
        env.exit(oldContext);
      }
      manager = null;
      sysInjector = null;
      sshInjector = null;
      httpInjector = null;
    }
  }

  public JarFile getJarFile() {
    return jarFile;
  }

  public Injector getSysInjector() {
    return sysInjector;
  }

  @Nullable
  public Injector getSshInjector() {
    return sshInjector;
  }

  @Nullable
  public Injector getHttpInjector() {
    return httpInjector;
  }

  public void add(RegistrationHandle handle) {
    if (manager != null) {
      if (handle instanceof ReloadableRegistrationHandle) {
        if (reloadableHandles == null) {
          reloadableHandles = Lists.newArrayList();
        }
        reloadableHandles.add((ReloadableRegistrationHandle<?>) handle);
      }
      manager.add(handle);
    }
  }

  List<ReloadableRegistrationHandle<?>> getReloadableHandles() {
    if (reloadableHandles != null) {
      return reloadableHandles;
    }
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "Plugin [" + name + "]";
  }
}
