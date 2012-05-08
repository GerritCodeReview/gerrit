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

import com.google.common.base.Strings;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
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
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class PluginLoader {
  private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  private final File pluginsDir;
  private Map<String, Plugin> pluginCache;

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
        log.error("Cannot access plugin jar " + jarFile, e);
      } catch (ClassNotFoundException e) {
        log.error("Cannot load plugin class module from " + jarFile, e);
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
    if (Strings.isNullOrEmpty(pluginName)) {
      throw new IOException("No Gerrit-Plugin attribute in manifest");
    }

    String moduleName = attrs.getValue("Gerrit-SshModule");
    if (Strings.isNullOrEmpty(moduleName)) {
      throw new IOException("No Gerrit-SshModule attribute in manifest");
    }

    Class<?> moduleClass = Class.forName(moduleName, false, jarClassLoader);
    if (!Module.class.isAssignableFrom(moduleClass)) {
      throw new ClassNotFoundException(String.format(
          "Gerrit-SshModule %s is not a Guice Module",
          moduleClass.getName()));
    }

    return new Plugin(pluginName, (Class<? extends Module>) moduleClass);
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
      log.error("Cannot list " + pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }

    return Arrays.asList(plugins);
  }
}
