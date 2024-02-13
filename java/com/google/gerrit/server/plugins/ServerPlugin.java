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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.config.GerritRuntime;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

public class ServerPlugin extends Plugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final String API_MODULE = "Gerrit-ApiModule";

  private final Manifest manifest;
  private final PluginContentScanner scanner;
  private final Path dataDir;
  private final String pluginCanonicalWebUrl;
  private final ClassLoader classLoader;
  private final String metricsPrefix;
  private final GerritRuntime gerritRuntime;
  protected Class<? extends Module> sysModule;
  protected Class<? extends Module> batchModule;
  protected Class<? extends Module> sshModule;
  protected Class<? extends Module> httpModule;
  private Class<? extends Module> apiModuleClass;

  private Injector apiInjector;
  private Injector sysInjector;
  private Injector sshInjector;
  private Injector httpInjector;
  private LifecycleManager serverManager;
  private List<ReloadableRegistrationHandle<?>> reloadableHandles;

  private Optional<Module> apiModule = Optional.empty();

  public ServerPlugin(
      String name,
      String pluginCanonicalWebUrl,
      PluginUser pluginUser,
      Path srcJar,
      FileSnapshot snapshot,
      PluginContentScanner scanner,
      Path dataDir,
      ClassLoader classLoader,
      String metricsPrefix,
      GerritRuntime gerritRuntime)
      throws InvalidPluginException {
    super(
        name,
        srcJar,
        pluginUser,
        snapshot,
        scanner == null ? ApiType.PLUGIN : Plugin.getApiType(getPluginManifest(scanner)));
    this.pluginCanonicalWebUrl = pluginCanonicalWebUrl;
    this.scanner = scanner;
    this.dataDir = dataDir;
    this.classLoader = classLoader;
    this.manifest = scanner == null ? null : getPluginManifest(scanner);
    this.metricsPrefix = metricsPrefix;
    this.gerritRuntime = gerritRuntime;
    if (manifest != null) {
      loadGuiceModules(manifest, classLoader);
    }
  }

  private void loadGuiceModules(Manifest manifest, ClassLoader classLoader)
      throws InvalidPluginException {
    Attributes main = manifest.getMainAttributes();
    String sysName = main.getValue("Gerrit-Module");
    String sshName = main.getValue("Gerrit-SshModule");
    String httpName = main.getValue("Gerrit-HttpModule");
    String batchName = main.getValue("Gerrit-BatchModule");
    String apiName = main.getValue(API_MODULE);

    if (!Strings.isNullOrEmpty(sshName) && getApiType() != Plugin.ApiType.PLUGIN) {
      throw new InvalidPluginException(
          String.format(
              "Using Gerrit-SshModule requires Gerrit-ApiType: %s", Plugin.ApiType.PLUGIN));
    }

    try {
      this.batchModule = load(batchName, classLoader);
      this.sysModule = load(sysName, classLoader);
      this.sshModule = load(sshName, classLoader);
      this.httpModule = load(httpName, classLoader);
      this.apiModuleClass = load(apiName, classLoader);
    } catch (ClassNotFoundException e) {
      throw new InvalidPluginException("Unable to load plugin Guice Modules", e);
    }
  }

  @Nullable
  @SuppressWarnings("unchecked")
  protected static Class<? extends Module> load(@Nullable String name, ClassLoader pluginLoader)
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

  String getMetricsPrefix() {
    return metricsPrefix;
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
  @Nullable
  public String getApiVersion() {
    Attributes main = manifest.getMainAttributes();
    return main.getValue("Gerrit-ApiVersion");
  }

  @Override
  protected boolean canReload() {
    Attributes main = manifest.getMainAttributes();
    String apiModule = main.getValue("Gerrit-ApiModule");
    if (apiModule != null) {
      return false;
    }

    String v = main.getValue("Gerrit-ReloadMode");
    if (Strings.isNullOrEmpty(v) || "reload".equalsIgnoreCase(v)) {
      return true;
    } else if ("restart".equalsIgnoreCase(v)) {
      return false;
    } else {
      logger.atWarning().log(
          "Plugin %s has invalid Gerrit-ReloadMode %s; assuming restart", getName(), v);
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
    serverManager = new LifecycleManager();

    if (gerritRuntime == GerritRuntime.BATCH) {
      Injector root = newRootInjector(env);
      if (batchModule != null) {
        sysInjector = root.createChildInjector(root.getInstance(batchModule));
        serverManager.add(sysInjector);
      } else {
        sysInjector = root;
      }

      serverManager.start();
      return;
    }

    AutoRegisterModules auto = null;
    if (sysModule == null && sshModule == null && httpModule == null && apiModuleClass == null) {
      auto = new AutoRegisterModules(getName(), env, scanner, classLoader);
      auto.discover();
    }

    Injector baseInjector;
    if (apiModuleClass == null) {
      baseInjector = newRootInjector(env);
    } else {
      baseInjector = newRootInjectorWithApiModule(env, apiModuleClass);
    }
    serverManager.add(baseInjector);

    if (sysModule != null) {
      sysInjector = baseInjector.createChildInjector(baseInjector.getInstance(sysModule));
      serverManager.add(sysInjector);
    } else if (auto != null && auto.sysModule != null) {
      sysInjector = baseInjector.createChildInjector(auto.sysModule);
      serverManager.add(sysInjector);
    } else {
      sysInjector = baseInjector;
    }

    if (env.hasSshModule()) {
      List<Module> modules = new ArrayList<>();
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
      List<Module> modules = new ArrayList<>();
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

  private Injector newRootInjector(PluginGuiceEnvironment env) {
    Optional<Injector> apiInjector = Optional.ofNullable(env.getApiInjector());

    List<Module> modules = Lists.newArrayListWithCapacity(2);
    if (getApiType() == ApiType.PLUGIN) {
      if (!apiInjector.isPresent()) {
        modules.add(env.getSysModule());
      }
    }
    modules.add(new ServerPluginInfoModule(this, env.getServerMetrics()));
    return apiInjector
        .map(injector -> injector.createChildInjector(modules))
        .orElse(Guice.createInjector(modules));
  }

  private Injector newRootInjectorWithApiModule(
      PluginGuiceEnvironment env, Class<? extends Module> apiModuleClass) {

    Injector baseInjector =
        Optional.ofNullable(env.getApiInjector())
            .orElseGet(() -> Guice.createInjector(env.getSysModule()));
    apiModule = Optional.of(baseInjector.getInstance(apiModuleClass));
    apiInjector = baseInjector.createChildInjector(apiModule.get());

    return apiInjector.createChildInjector(
        new ServerPluginInfoModule(this, env.getServerMetrics()));
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
  public Injector getApiInjector() {
    return apiInjector;
  }

  @Override
  public Optional<Module> getApiModule() {
    return apiModule;
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
