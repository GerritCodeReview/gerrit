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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class PluginLoader implements LifecycleListener {
  static final String PLUGIN_TMP_PREFIX = "plugin_";
  static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  private final File pluginsDir;
  private final File dataDir;
  private final File tmpDir;
  private final PluginGuiceEnvironment env;
  private final ServerInformationImpl srvInfoImpl;
  private final PluginUser.Factory pluginUserFactory;
  private final ConcurrentMap<String, Plugin> running;
  private final ConcurrentMap<String, Plugin> disabled;
  private final Map<String, FileSnapshot> broken;
  private final Map<Plugin, CleanupHandle> cleanupHandles;
  private final Queue<Plugin> toCleanup;
  private final Provider<PluginCleanerTask> cleaner;
  private final PluginScannerThread scanner;

  @Inject
  public PluginLoader(SitePaths sitePaths,
      PluginGuiceEnvironment pe,
      ServerInformationImpl sii,
      PluginUser.Factory puf,
      Provider<PluginCleanerTask> pct,
      @GerritServerConfig Config cfg) {
    pluginsDir = sitePaths.plugins_dir;
    dataDir = sitePaths.data_dir;
    tmpDir = sitePaths.tmp_dir;
    env = pe;
    srvInfoImpl = sii;
    pluginUserFactory = puf;
    running = Maps.newConcurrentMap();
    disabled = Maps.newConcurrentMap();
    broken = Maps.newHashMap();
    toCleanup = Queues.newArrayDeque();
    cleanupHandles = Maps.newConcurrentMap();
    cleaner = pct;

    long checkFrequency = ConfigUtil.getTimeUnit(cfg,
        "plugins", null, "checkFrequency",
        TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS);
    if (checkFrequency > 0) {
      scanner = new PluginScannerThread(this, checkFrequency);
    } else {
      scanner = null;
    }
  }

  public Plugin get(String name) {
    Plugin p = running.get(name);
    if (p != null) {
      return p;
    }
    return disabled.get(name);
  }

  public Iterable<Plugin> getPlugins(boolean all) {
    if (!all) {
      return running.values();
    } else {
      ArrayList<Plugin> plugins = new ArrayList<Plugin>(running.values());
      plugins.addAll(disabled.values());
      return plugins;
    }
  }

  public void installPluginFromStream(String name, InputStream in)
      throws IOException, PluginInstallException {
    if (!name.endsWith(".jar")) {
      name += ".jar";
    }

    File jar = new File(pluginsDir, name);
    name = nameOf(jar);

    File old = new File(pluginsDir, ".last_" + name + ".zip");
    File tmp = asTemp(in, ".next_" + name, ".zip", pluginsDir);
    synchronized (this) {
      Plugin active = running.get(name);
      if (active != null) {
        log.info(String.format("Replacing plugin %s", name));
        old.delete();
        jar.renameTo(old);
      }

      new File(pluginsDir, name + ".jar.disabled").delete();
      tmp.renameTo(jar);
      try {
        runPlugin(name, jar, active);
        if (active == null) {
          log.info(String.format("Installed plugin %s", name));
        }
      } catch (PluginInstallException e) {
        jar.delete();
        throw e;
      }

      cleanInBackground();
    }
  }

  public static File storeInTemp(String pluginName, InputStream in,
      SitePaths sitePaths) throws IOException {
    return asTemp(in, tempNameFor(pluginName), ".jar", sitePaths.tmp_dir);
  }

  private static File asTemp(InputStream in,
      String prefix, String suffix,
      File dir) throws IOException {
    File tmp = File.createTempFile(prefix, suffix, dir);
    boolean keep = false;
    try {
      FileOutputStream out = new FileOutputStream(tmp);
      try {
        byte[] data = new byte[8192];
        int n;
        while ((n = in.read(data)) > 0) {
          out.write(data, 0, n);
        }
        keep = true;
        return tmp;
      } finally {
        out.close();
      }
    } finally {
      if (!keep) {
        tmp.delete();
      }
    }
  }

  synchronized private void unloadPlugin(Plugin plugin) {
    String name = plugin.getName();
    log.info(String.format("Unloading plugin %s", name));
    plugin.stop(env);
    running.remove(name);
    disabled.remove(name);
    toCleanup.add(plugin);
  }

  public void disablePlugins(Set<String> names) {
    synchronized (this) {
      for (String name : names) {
        Plugin active = running.get(name);
        if (active == null) {
          continue;
        }

        log.info(String.format("Disabling plugin %s", name));
        File off = new File(pluginsDir, active.getName() + ".jar.disabled");
        active.getSrcJar().renameTo(off);

        unloadPlugin(active);
        try {
          FileSnapshot snapshot = FileSnapshot.save(off);
          Plugin offPlugin = loadPlugin(name, off, snapshot);
          disabled.put(name, offPlugin);
        } catch (Throwable e) {
          // This shouldn't happen, as the plugin was loaded earlier.
          log.warn(String.format("Cannot load disabled plugin %s", name),
              e.getCause());
        }
      }
      cleanInBackground();
    }
  }

  public void enablePlugins(Set<String> names) throws PluginInstallException {
    synchronized (this) {
      for (String name : names) {
        Plugin off = disabled.get(name);
        if (off == null) {
          continue;
        }

        log.info(String.format("Enabling plugin %s", name));
        File on = new File(pluginsDir, off.getName() + ".jar");
        off.getSrcJar().renameTo(on);

        disabled.remove(name);
        runPlugin(name, on, null);
      }
      cleanInBackground();
    }
  }

  @Override
  public synchronized void start() {
    log.info("Loading plugins from " + pluginsDir.getAbsolutePath());
    srvInfoImpl.state = ServerInformation.State.STARTUP;
    rescan();
    srvInfoImpl.state = ServerInformation.State.RUNNING;
    if (scanner != null) {
      scanner.start();
    }
  }

  @Override
  public void stop() {
    if (scanner != null) {
      scanner.end();
    }
    srvInfoImpl.state = ServerInformation.State.SHUTDOWN;
    synchronized (this) {
      for (Plugin p : running.values()) {
        unloadPlugin(p);
      }
      running.clear();
      disabled.clear();
      broken.clear();
      if (!toCleanup.isEmpty()) {
        System.gc();
        processPendingCleanups();
      }
    }
  }

  public void reload(List<String> names)
      throws InvalidPluginException, PluginInstallException {
    synchronized (this) {
      List<Plugin> reload = Lists.newArrayListWithCapacity(names.size());
      List<String> bad = Lists.newArrayListWithExpectedSize(4);
      for (String name : names) {
        Plugin active = running.get(name);
        if (active != null) {
          reload.add(active);
        } else {
          bad.add(name);
        }
      }
      if (!bad.isEmpty()) {
        throw new InvalidPluginException(String.format(
            "Plugin(s) \"%s\" not running",
            Joiner.on("\", \"").join(bad)));
      }

      for (Plugin active : reload) {
        String name = active.getName();
        try {
          log.info(String.format("Reloading plugin %s", name));
          runPlugin(name, active.getSrcJar(), active);
        } catch (PluginInstallException e) {
          log.warn(String.format("Cannot reload plugin %s", name), e.getCause());
          throw e;
        }
      }

      cleanInBackground();
    }
  }

  public synchronized void rescan() {
    List<File> jars = scanJarsInPluginsDirectory();
    stopRemovedPlugins(jars);
    dropRemovedDisabledPlugins(jars);

    for (File jar : jars) {
      if (jar.getName().endsWith(".disabled")) {
        continue;
      }

      String name = nameOf(jar);
      FileSnapshot brokenTime = broken.get(name);
      if (brokenTime != null && !brokenTime.isModified(jar)) {
        continue;
      }

      Plugin active = running.get(name);
      if (active != null && !active.isModified(jar)) {
        continue;
      }

      if (active != null) {
        log.info(String.format("Reloading plugin %s", name));
      }

      try {
        Plugin loadedPlugin = runPlugin(name, jar, active);
        if (active == null && !loadedPlugin.isDisabled()) {
          log.info(String.format("Loaded plugin %s", name));
        }
      } catch (PluginInstallException e) {
        log.warn(String.format("Cannot load plugin %s", name), e.getCause());
      }
    }

    cleanInBackground();
  }

  private Plugin runPlugin(String name, File jar, Plugin oldPlugin)
      throws PluginInstallException {
    FileSnapshot snapshot = FileSnapshot.save(jar);
    try {
      Plugin newPlugin = loadPlugin(name, jar, snapshot);
      boolean reload = oldPlugin != null
          && oldPlugin.canReload()
          && newPlugin.canReload();
      if (!reload && oldPlugin != null) {
        unloadPlugin(oldPlugin);
      }
      if (!newPlugin.isDisabled()) {
        newPlugin.start(env);
      }
      if (reload) {
        env.onReloadPlugin(oldPlugin, newPlugin);
        unloadPlugin(oldPlugin);
      } else if (!newPlugin.isDisabled()) {
        env.onStartPlugin(newPlugin);
      }
      if (!newPlugin.isDisabled()) {
        running.put(name, newPlugin);
      } else {
        disabled.put(name, newPlugin);
      }
      broken.remove(name);
      return newPlugin;
    } catch (Throwable err) {
      broken.put(name, snapshot);
      throw new PluginInstallException(err);
    }
  }

  private void stopRemovedPlugins(List<File> jars) {
    Set<String> unload = Sets.newHashSet(running.keySet());
    for (File jar : jars) {
      if (!jar.getName().endsWith(".disabled")) {
        unload.remove(nameOf(jar));
      }
    }
    for (String name : unload){
      unloadPlugin(running.get(name));
    }
  }

  private void dropRemovedDisabledPlugins(List<File> jars) {
    Set<String> unload = Sets.newHashSet(disabled.keySet());
    for (File jar : jars) {
      if (jar.getName().endsWith(".disabled")) {
        unload.remove(nameOf(jar));
      }
    }
    for (String name : unload) {
      disabled.remove(name);
    }
  }

  synchronized int processPendingCleanups() {
    Iterator<Plugin> iterator = toCleanup.iterator();
    while (iterator.hasNext()) {
      Plugin plugin = iterator.next();
      iterator.remove();

      CleanupHandle cleanupHandle = cleanupHandles.remove(plugin);
      cleanupHandle.cleanup();
    }
    return toCleanup.size();
  }

  private void cleanInBackground() {
    int cnt = toCleanup.size();
    if (0 < cnt) {
      cleaner.get().clean(cnt);
    }
  }

  private static String nameOf(File jar) {
    String name = jar.getName();
    if (name.endsWith(".disabled")) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(0, ext) : name;
  }

  private Plugin loadPlugin(String name, File srcJar, FileSnapshot snapshot)
      throws IOException, ClassNotFoundException, InvalidPluginException {
    File tmp;
    FileInputStream in = new FileInputStream(srcJar);
    try {
      tmp = asTemp(in, tempNameFor(name), ".jar", tmpDir);
    } finally {
      in.close();
    }

    JarFile jarFile = new JarFile(tmp);
    boolean keep = false;
    try {
      Manifest manifest = jarFile.getManifest();
      Plugin.ApiType type = Plugin.getApiType(manifest);
      Attributes main = manifest.getMainAttributes();
      String sysName = main.getValue("Gerrit-Module");
      String sshName = main.getValue("Gerrit-SshModule");
      String httpName = main.getValue("Gerrit-HttpModule");

      if (!Strings.isNullOrEmpty(sshName) && type != Plugin.ApiType.PLUGIN) {
        throw new InvalidPluginException(String.format(
            "Using Gerrit-SshModule requires Gerrit-ApiType: %s",
            Plugin.ApiType.PLUGIN));
      }

      URL[] urls = {tmp.toURI().toURL()};
      ClassLoader parentLoader = parentFor(type);
      ClassLoader pluginLoader = new URLClassLoader(urls, parentLoader);
      Class<? extends Module> sysModule = load(sysName, pluginLoader);
      Class<? extends Module> sshModule = load(sshName, pluginLoader);
      Class<? extends Module> httpModule = load(httpName, pluginLoader);
      Plugin plugin = new Plugin(name, pluginUserFactory.create(name),
          srcJar, snapshot,
          jarFile, manifest,
          new File(dataDir, name), type, pluginLoader,
          sysModule, sshModule, httpModule);
      cleanupHandles.put(plugin, new CleanupHandle(tmp, jarFile));
      keep = true;
      return plugin;
    } finally {
      if (!keep) {
        jarFile.close();
      }
    }
  }

  private static ClassLoader parentFor(Plugin.ApiType type)
      throws InvalidPluginException {
    switch (type) {
      case EXTENSION:
        return PluginName.class.getClassLoader();
      case PLUGIN:
        return PluginLoader.class.getClassLoader();
      case JS:
        return JavaScriptPlugin.class.getClassLoader();
      default:
        throw new InvalidPluginException("Unsupported ApiType " + type);
    }
  }

  private static String tempNameFor(String name) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmm");
    return PLUGIN_TMP_PREFIX + name + "_" + fmt.format(new Date()) + "_";
  }

  private Class<? extends Module> load(String name, ClassLoader pluginLoader)
      throws ClassNotFoundException {
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Class<? extends Module> clazz =
        (Class<? extends Module>) Class.forName(name, false, pluginLoader);
    if (!Module.class.isAssignableFrom(clazz)) {
      throw new ClassCastException(String.format(
          "Class %s does not implement %s",
          name, Module.class.getName()));
    }
    return clazz;
  }

  private List<File> scanJarsInPluginsDirectory() {
    if (pluginsDir == null || !pluginsDir.exists()) {
      return Collections.emptyList();
    }
    File[] matches = pluginsDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        String n = pathname.getName();
        return (n.endsWith(".jar") || n.endsWith(".jar.disabled"))
            && pathname.isFile();
      }
    });
    if (matches == null) {
      log.error("Cannot list " + pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }
    return Arrays.asList(matches);
  }
}
