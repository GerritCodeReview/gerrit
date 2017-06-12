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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

public class ServerPlugin extends Plugin {
  private final Manifest manifest;
  private final PluginContentScanner scanner;
  private final Path dataDir;
  private final String pluginCanonicalWebUrl;
  private final ClassLoader classLoader;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;
  private Class<? extends Module> httpModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private Injector httpInjector;
  private LifecycleManager serverManager;
  private List<ReloadableRegistrationHandle<?>> reloadableHandles;

  public ServerPlugin(
      String name,
      String pluginCanonicalWebUrl,
      PluginUser pluginUser,
      Path srcJar,
      FileSnapshot snapshot,
      PluginContentScanner scanner,
      Path dataDir,
      ClassLoader classLoader)
      throws InvalidPluginException {
    super(name, srcJar, pluginUser, snapshot, Plugin.getApiType(getPluginManifest(scanner)));
    this.pluginCanonicalWebUrl = pluginCanonicalWebUrl;
    this.scanner = scanner;
    this.dataDir = dataDir;
    this.classLoader = classLoader;
    this.manifest = getPluginManifest(scanner);
    loadGuiceModules(manifest, classLoader);
  }

  private void loadGuiceModules(Manifest manifest, ClassLoader classLoader)
      throws InvalidPluginException {
    Attributes main = manifest.getMainAttributes();
    String sysName = main.getValue("Gerrit-Module");
    String sshName = main.getValue("Gerrit-SshModule");
    String httpName = main.getValue("Gerrit-HttpModule");

    if (!Strings.isNullOrEmpty(sshName) && getApiType() != Plugin.ApiType.PLUGIN) {
      throw new InvalidPluginException(
          String.format(
              "Using Gerrit-SshModule requires Gerrit-ApiType: %s", Plugin.ApiType.PLUGIN));
    }

    try {
      this.sysModule = load(sysName, classLoader);
      this.sshModule = load(sshName, classLoader);
      this.httpModule = load(httpName, classLoader);
    } catch (ClassNotFoundException e) {
      throw new InvalidPluginException("Unable to load plugin Guice Modules", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends Module> load(String name, ClassLoader pluginLoader)
      throws ClassNotFoundException {
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }

    Class<?> clazz = Class.forName(name, false, pluginLoader);
    if (!Module.class.isAssignableFrom(clazz)) {
      throw new ClassCastException(
          String.format("Class %s does not implement %s", name, Module.class.getName()));
    }
    return (Class<? extends Module>) clazz;
  }

  Path getDataDir() {
    return dataDir;
  }

  String getPluginCanonicalWebUrl() {
    return pluginCanonicalWebUrl;
  }

  private static Manifest getPluginManifest(PluginContentScanner scanner)
      throws InvalidPluginException {
    try {
      return scanner.getManifest();
    } catch (IOException e) {
      throw new InvalidPluginException("Cannot get plugin manifest", e);
    }
  }

  @Override
  @Nullable
  public String getVersion() {
    Attributes main = manifest.getMainAttributes();
    return main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
  }

  @Override
  protected boolean canReload() {
    Attributes main = manifest.getMainAttributes();
    String v = main.getValue("Gerrit-ReloadMode");
    if (Strings.isNullOrEmpty(v) || "reload".equalsIgnoreCase(v)) {
      return true;
    } else if ("restart".equalsIgnoreCase(v)) {
      return false;
    } else {
      PluginLoader.log.warn(
          String.format(
              "Plugin %s has invalid Gerrit-ReloadMode %s; assuming restart", getName(), v));
      return false;
    }
  }

  @Override
  protected void start(PluginGuiceEnvironment env) throws Exception {
    RequestContext oldContext = env.enter(this);
    try {
      startPlugin(env);
    } finally {
      env.exit(oldContext);
    }
  }

  private void startPlugin(PluginGuiceEnvironment env) throws Exception {
    Injector root = newRootInjector(env);
    serverManager = new LifecycleManager();
    serverManager.add(root);

    AutoRegisterModules auto = null;
    if (sysModule == null && sshModule == null && httpModule == null) {
      auto = new AutoRegisterModules(getName(), env, scanner, classLoader);
      auto.discover();
    }

    if (sysModule != null) {
      sysInjector = root.createChildInjector(root.getInstance(sysModule));
      serverManager.add(sysInjector);
    } else if (auto != null && auto.sysModule != null) {
      sysInjector = root.createChildInjector(auto.sysModule);
      serverManager.add(sysInjector);
    } else {
      sysInjector = root;
    }

    if (env.hasSshModule()) {
      List<Module> modules = new LinkedList<>();
      if (getApiType() == ApiType.PLUGIN) {
        modules.add(env.getSshModule());
      }
      if (sshModule != null) {
        modules.add(sysInjector.getInstance(sshModule));
        sshInjector = sysInjector.createChildInjector(modules);
        serverManager.add(sshInjector);
      } else if (auto != null && auto.sshModule != null) {
        modules.add(auto.sshModule);
        sshInjector = sysInjector.createChildInjector(modules);
        serverManager.add(sshInjector);
      }
    }

    if (env.hasHttpModule()) {
      List<Module> modules = new LinkedList<>();
      if (getApiType() == ApiType.PLUGIN) {
        modules.add(env.getHttpModule());
      }
      if (httpModule != null) {
        modules.add(sysInjector.getInstance(httpModule));
        httpInjector = sysInjector.createChildInjector(modules);
        serverManager.add(httpInjector);
      } else if (auto != null && auto.httpModule != null) {
        modules.add(auto.httpModule);
        httpInjector = sysInjector.createChildInjector(modules);
        serverManager.add(httpInjector);
      }
    }

    serverManager.start();
  }

  private Injector newRootInjector(final PluginGuiceEnvironment env) {
    List<Module> modules = Lists.newArrayListWithCapacity(2);
    if (getApiType() == ApiType.PLUGIN) {
      modules.add(env.getSysModule());
    }
    modules.add(new ServerPluginInfoModule(this, env.getServerMetrics()));
    return Guice.createInjector(modules);
  }

  @Override
  protected void stop(PluginGuiceEnvironment env) {
    if (serverManager != null) {
      RequestContext oldContext = env.enter(this);
      try {
        serverManager.stop();
      } finally {
        env.exit(oldContext);
      }
      serverManager = null;
      sysInjector = null;
      sshInjector = null;
      httpInjector = null;
    }
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

  @Override
  public void add(RegistrationHandle handle) {
    if (serverManager != null) {
      if (handle instanceof ReloadableRegistrationHandle) {
        if (reloadableHandles == null) {
          reloadableHandles = new ArrayList<>();
        }
        reloadableHandles.add((ReloadableRegistrationHandle<?>) handle);
      }
      serverManager.add(handle);
    }
  }

  @Override
  public PluginContentScanner getContentScanner() {
    return scanner;
  }
}
