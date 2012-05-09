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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class PluginLoader implements LifecycleListener {
  static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  private final File pluginsDir;
  private final PluginGuiceEnvironment env;
  private final Map<String, Plugin> running;
  private final Map<String, FileSnapshot> broken;
  private final PluginScannerThread scanner;

  @Inject
  public PluginLoader(SitePaths sitePaths,
      PluginGuiceEnvironment pe,
      @GerritServerConfig Config cfg) {
    pluginsDir = sitePaths.plugins_dir;
    env = pe;
    running = Maps.newHashMap();
    broken = Maps.newHashMap();
    scanner = new PluginScannerThread(
        this,
        ConfigUtil.getTimeUnit(cfg,
            "plugins", null, "checkFrequency",
            TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS));
  }

  public synchronized List<Plugin> getPlugins() {
    return Lists.newArrayList(running.values());
  }

  public void installPluginFromStream(String name, InputStream in)
      throws IOException, PluginInstallException {
    if (!name.endsWith(".jar")) {
      name += ".jar";
    }

    File jar = new File(pluginsDir, name);
    name = nameOf(jar);

    File old = new File(pluginsDir, ".last_" + name + ".zip");
    File tmp = copyToTemp(name, in);

    synchronized (this) {
      Plugin active = running.get(name);
      if (active != null) {
        log.info(String.format("Replacing plugin %s", name));
        old.delete();
        jar.renameTo(old);
      }

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
    }
  }

  private File copyToTemp(String name, InputStream in) throws IOException {
    File tmp = File.createTempFile(".next_" + name, ".zip", pluginsDir);
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

  public synchronized void disablePlugins(Set<String> names) {
    for (String name : names) {
      Plugin active = running.get(name);
      if (active == null) {
        continue;
      }

      log.info(String.format("Disabling plugin %s", name));
      active.stop();
      running.remove(name);

      File off = new File(pluginsDir, active.getName() + ".jar.disabled");
      active.getJar().renameTo(off);
    }
  }

  @Override
  public synchronized void start() {
    log.info("Loading plugins from " + pluginsDir.getAbsolutePath());
    rescan();
    scanner.start();
  }

  @Override
  public void stop() {
    scanner.end();
    synchronized (this) {
      for (Plugin p : running.values()) {
        p.stop();
      }
      running.clear();
      broken.clear();
    }
  }

  public synchronized void rescan() {
    List<File> jars = scanJarsInPluginsDirectory();

    stopRemovedPlugins(jars);

    for (File jar : jars) {
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
        log.warn(String.format(
            "Detected %s was replaced/overwritten."
            + " This is not a safe way to update a plugin.",
            jar.getAbsolutePath()));
        log.info(String.format("Reloading plugin %s", name));
      }

      try {
        runPlugin(name, jar, active);
        if (active == null) {
          log.info(String.format("Loaded plugin %s", name));
        }
      } catch (PluginInstallException e) {
        log.warn(String.format("Cannot load plugin %s", name), e.getCause());
      }
    }
  }

  private void runPlugin(String name, File jar, Plugin oldPlugin)
      throws PluginInstallException {
    FileSnapshot snapshot = FileSnapshot.save(jar);
    try {
      Plugin newPlugin = loadPlugin(name, jar, snapshot);
      boolean reload = oldPlugin != null
          && oldPlugin.canReload()
          && newPlugin.canReload();
      if (!reload && oldPlugin != null) {
        oldPlugin.stop();
        running.remove(name);
      }
      newPlugin.start(env);
      if (reload) {
        env.onReloadPlugin(oldPlugin, newPlugin);
        oldPlugin.stop();
      } else {
        env.onStartPlugin(newPlugin);
      }
      running.put(name, newPlugin);
      broken.remove(name);
    } catch (Throwable err) {
      broken.put(name, snapshot);
      throw new PluginInstallException(err);
    }
  }

  private void stopRemovedPlugins(List<File> jars) {
    Set<String> unload = Sets.newHashSet(running.keySet());
    for (File jar : jars) {
      unload.remove(nameOf(jar));
    }
    for (String name : unload){
      log.info(String.format("Unloading plugin %s", name));
      running.remove(name).stop();
    }
  }

  private static String nameOf(File jar) {
    String name = jar.getName();
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(0, ext) : name;
  }

  private Plugin loadPlugin(String name, File jarFile, FileSnapshot snapshot)
      throws IOException, ClassNotFoundException {
    Manifest manifest = new JarFile(jarFile).getManifest();

    Attributes main = manifest.getMainAttributes();
    String sysName = main.getValue("Gerrit-Module");
    String sshName = main.getValue("Gerrit-SshModule");
    String httpName = main.getValue("Gerrit-HttpModule");

    URL[] urls = {jarFile.toURI().toURL()};
    ClassLoader parentLoader = PluginLoader.class.getClassLoader();
    ClassLoader pluginLoader = new URLClassLoader(urls, parentLoader);

    Class<? extends Module> sysModule = load(sysName, pluginLoader);
    Class<? extends Module> sshModule = load(sshName, pluginLoader);
    Class<? extends Module> httpModule = load(httpName, pluginLoader);
    return new Plugin(name, jarFile, manifest, snapshot,
        sysModule, sshModule, httpModule);
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
        return pathname.getName().endsWith(".jar") && pathname.isFile();
      }
    });
    if (matches == null) {
      log.error("Cannot list " + pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }
    return Arrays.asList(matches);
  }
}
