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

import static com.google.gerrit.server.plugins.PluginLoader.asTemp;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.storage.file.FileSnapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.annotation.Nullable;

public class Plugin {
  public static enum ApiType {
    EXTENSION, PLUGIN;
  }

  public static enum ModuleType {
    STARTUP("Gerrit-StartupModule"),
    SYS("Gerrit-Module"),
    SSH("Gerrit-SshModule"),
    HTTP("Gerrit-HttpModule");

    private final String name;

    private ModuleType(String name) {
      this.name = name;
    }

    public String from(Manifest manifest) {
      return manifest.getMainAttributes().getValue(name);
    }

    public String get() {
      return name;
    }

    @Override
    public String toString() {
      return get();
    }
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
    } else {
      throw new InvalidPluginException("Invalid Gerrit-ApiType: " + v);
    }
  }

  private final CacheKey cacheKey;
  private final SitePaths sitePaths;
  private final String name;
  private final File srcJar;
  private final FileSnapshot snapshot;
  private final boolean required;

  private File tmp;
  private JarFile jarFile;
  private Manifest manifest;
  private ApiType apiType;
  private ClassLoader classLoader;
  private Class<? extends Module> startupModule;
  private Class<? extends Module> sysModule;
  private Class<? extends Module> sshModule;
  private Class<? extends Module> httpModule;

  private Injector sysInjector;
  private Injector sshInjector;
  private Injector httpInjector;
  private LifecycleManager manager;
  private List<ReloadableRegistrationHandle<?>> reloadableHandles;

  Plugin(SitePaths sitePaths, String name, File srcJar, boolean required) {
    this.cacheKey = new CacheKey(name);
    this.sitePaths = sitePaths;
    this.name = name;
    this.srcJar = srcJar;
    this.snapshot = FileSnapshot.save(srcJar);
    this.required = required;
  }

  void loadStartup() throws PluginInstallException {
    if (jarFile != null) {
      return;
    }
    boolean keep = false;
    JarFile jar = null;
    try {
      FileInputStream in = new FileInputStream(srcJar);
      try {
        tmp = asTemp(in, tempNameFor(name), ".jar", sitePaths.tmp_dir);
      } finally {
        in.close();
      }

      jar = new JarFile(tmp);
      manifest = jar.getManifest();

      if (required) {
        if (Plugin.getApiType(manifest) != Plugin.ApiType.PLUGIN) {
          throw new InvalidPluginException(String.format(
              "Required plugin %s must have Gerrit-ApiType: %s",
              name, Plugin.ApiType.PLUGIN));
        }
      } else if (ModuleType.STARTUP.from(manifest) != null) {
        throw new InvalidPluginException(String.format(
            "Non-required plugin %s cannot have %s",
            name, ModuleType.STARTUP));
      }

      Plugin.ApiType type = Plugin.getApiType(manifest);
      URL[] urls = {tmp.toURI().toURL()};
      classLoader = new URLClassLoader(urls, parentFor(type));
      startupModule = load(ModuleType.STARTUP);
      jarFile = jar;
      keep = true;
    } catch (Throwable t) {
      throw new PluginInstallException(t);
    } finally {
      if (!keep) {
        try {
          jar.close();
        } catch (IOException e) {
          throw new PluginInstallException(e);
        }
      }
    }
  }

  void load(PluginLoader loader)
      throws IOException, ClassNotFoundException, InvalidPluginException,
      PluginInstallException {
    loadStartup();
    boolean keep = false;
    try {
      String sshName = ModuleType.SSH.from(manifest);
      String httpName = ModuleType.HTTP.from(manifest);

      Plugin.ApiType type = Plugin.getApiType(manifest);
      if (!Strings.isNullOrEmpty(sshName) && type != Plugin.ApiType.PLUGIN) {
        throw new InvalidPluginException(String.format(
            "Plugin %s with %s requires Gerrit-ApiType: %s",
            name, ModuleType.SSH, Plugin.ApiType.PLUGIN));
      }

      if (loader != null) {
        loader.addCleanupHandle(tmp, jarFile, classLoader);
      }
      sysModule = load(ModuleType.SYS);
      sshModule = load(ModuleType.SSH);
      httpModule = load(ModuleType.HTTP);
      keep = true;
    } finally {
      if (!keep) {
        jarFile.close();
      }
    }
  }

  Class<? extends Module> getStartupModuleClass() {
    return startupModule;
  }

  boolean isRequired() {
    return required;
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

  FileSnapshot getSnapshot() {
    return snapshot;
  }

  boolean canReload() {
    String v = manifest.getMainAttributes().getValue("Gerrit-ReloadMode");
    if (required) {
      if (!Strings.isNullOrEmpty(v)) {
        PluginLoader.log.warn(String.format(
            "Ignoring Gerrit-ReloadMode: %s for required plugin %s",
            v, name));
      }
      return false;
    } else {
      if (Strings.isNullOrEmpty(v) || "reload".equalsIgnoreCase(v)) {
        return true;
      } else if ("restart".equalsIgnoreCase(v)) {
        return false;
      } else {
        PluginLoader.log.warn(String.format(
            "Plugin %s has invalid Gerrit-ReloadMode: %s; assuming restart",
            name, v));
        return false;
      }
    }
  }

  boolean isModified(File jar) {
    return snapshot.lastModified() != jar.lastModified();
  }

  public void start(PluginGuiceEnvironment env) throws Exception {
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
    modules.add(env.getSysModule());
    if (apiType == ApiType.PLUGIN) {
      modules.add(env.getSysModule());
    } else {
      modules.add(new AbstractModule() {
        @Override
        protected void configure() {
          bind(ServerInformation.class).toInstance(env.getServerInformation());
        }
      });
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
              File dataDir = sitePaths.data_dir;
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
    if (handle instanceof ReloadableRegistrationHandle) {
      if (reloadableHandles == null) {
        reloadableHandles = Lists.newArrayList();
      }
      reloadableHandles.add((ReloadableRegistrationHandle<?>) handle);
    }
    manager.add(handle);
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

  private Class<? extends Module> load(ModuleType type)
      throws ClassNotFoundException {
    String name = type.from(manifest);
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Class<? extends Module> clazz =
        (Class<? extends Module>) Class.forName(name, false, classLoader);
    if (!Module.class.isAssignableFrom(clazz)) {
      throw new ClassCastException(String.format(
          "Class %s does not implement %s",
          name, Module.class.getName()));
    }
    return clazz;
  }

  private static String tempNameFor(String name) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmm");
    return "plugin_" + name + "_" + fmt.format(new Date()) + "_";
  }

  private static ClassLoader parentFor(Plugin.ApiType type)
      throws InvalidPluginException {
    switch (type) {
      case EXTENSION:
        return PluginName.class.getClassLoader();
      case PLUGIN:
        return PluginLoader.class.getClassLoader();
      default:
        throw new InvalidPluginException("Unsupported ApiType " + type);
    }
  }
}
