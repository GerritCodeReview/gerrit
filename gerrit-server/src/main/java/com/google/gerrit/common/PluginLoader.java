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

package com.google.gerrit.common;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class PluginLoader {
  private Logger log = LoggerFactory.getLogger(PluginLoader.class);

  private File pluginsDir;

  private HashMap<String, Plugin> pluginCache;

  @Inject
  public PluginLoader(SitePaths sitePaths) {
    pluginsDir = sitePaths.plugins_dir;
  }

  private synchronized void initialize() {
    if (pluginCache != null) {
      return;
    }

    pluginCache = new HashMap<String, Plugin>();
    loadPlugins();
  }

  public Plugin get(String pluginName) {
    initialize();
    return pluginCache.get(pluginName);
  }

  public Collection<Plugin> getPlugins() {
    initialize();
    return pluginCache.values();
  }

  private void loadPlugins() {
    Collection<File> pluginJars;
    try {
      pluginJars = getPluginFiles();
    } catch (IOException e) {
      log.error("Cannot scan Gerrit plugins directory looking for jar files", e);
      return;
    }

    for (File jarFile : pluginJars) {
      Plugin plugin;
      try {
        plugin = loadPlugin(jarFile);
        pluginCache.put(plugin.name, plugin);
      } catch (IOException e) {
        log.warn("Cannot access Plugin jar " + jarFile, e);
      } catch (ClassNotFoundException e) {
        log.warn("Cannot load Plugin class module from " + jarFile, e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Plugin loadPlugin(File jarFile) throws IOException,
      ClassNotFoundException {
    Manifest jarManifest = new JarFile(jarFile).getManifest();
    ClassLoader parentLoader = PluginLoader.class.getClassLoader();
    ClassLoader jarClassLoader =
        new URLClassLoader(getPluginURLs(jarFile), parentLoader);

    Attributes attrs = jarManifest.getMainAttributes();
    String pluginName = attrs.getValue("Gerrit-Plugin");
    String moduleName = attrs.getValue("Gerrit-SshModule");

    Class<?> moduleClass = Class.forName(moduleName, false, jarClassLoader);
    if (!AbstractModule.class.isAssignableFrom(moduleClass)) {
      throw new ClassNotFoundException("ModuleClass "
          + moduleClass.getName() + " is not a Guice AbstractModule");
    }

    return new Plugin(pluginName, (Class<? extends AbstractModule>) moduleClass);
  }

  private URL[] getPluginURLs(File jarFile) throws MalformedURLException {
    return new URL[] {jarFile.toURI().toURL()};
  }

  private List<File> getPluginFiles() throws IOException {
    if (pluginsDir == null || !pluginsDir.exists()) {
      return Collections.emptyList();
    }

    File[] plugins = pluginsDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.isFile() && pathname.getName().endsWith(".jar");
      }
    });
    if (plugins == null) {
      log.warn("Cannot list " + pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }

    return Arrays.asList(plugins);
  }
}
