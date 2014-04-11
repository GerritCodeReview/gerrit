// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.gerrit.server.plugins.PluginLoader.parentFor;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
class JarPluginLoader {
  static final Logger log = LoggerFactory.getLogger(JarPluginLoader.class);

  private final File dataDir;
  private final Provider<String> urlProvider;
  private final PluginUser.Factory pluginUserFactory;
  private final Map<Plugin, CleanupHandle> cleanupHandles;

  @Inject
  JarPluginLoader(
      SitePaths sitePaths,
      PluginUser.Factory puf,
      @CanonicalWebUrl Provider<String> provider) {
    dataDir = sitePaths.data_dir;
    this.urlProvider = provider;
    this.pluginUserFactory = puf;
    cleanupHandles = Maps.newConcurrentMap();
  }

  Plugin load(String name, File srcJar, FileSnapshot snapshot,
      File tmp) throws IOException, InvalidPluginException,
      MalformedURLException, ClassNotFoundException {
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

      List<URL> urls = new ArrayList<>(2);
      String overlay = System.getProperty("gerrit.plugin-classes");
      if (overlay != null) {
        File classes = new File(new File(new File(overlay), name), "main");
        if (classes.isDirectory()) {
          log.info(String.format(
              "plugin %s: including %s",
              name, classes.getPath()));
          urls.add(classes.toURI().toURL());
        }
      }
      urls.add(tmp.toURI().toURL());

      ClassLoader pluginLoader = new URLClassLoader(
          urls.toArray(new URL[urls.size()]),
          parentFor(type));
      Class<? extends Module> sysModule = load(sysName, pluginLoader);
      Class<? extends Module> sshModule = load(sshName, pluginLoader);
      Class<? extends Module> httpModule = load(httpName, pluginLoader);

      String url = String.format("%s/plugins/%s/",
          CharMatcher.is('/').trimTrailingFrom(urlProvider.get()),
          name);

      Plugin plugin = new JarPlugin(name, url,
          pluginUserFactory.create(name),
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

  void cleanup(Plugin plugin) {
    CleanupHandle cleanupHandle = cleanupHandles.remove(plugin);
    if (cleanupHandle != null) {
      cleanupHandle.cleanup();
    }
  }

  private static Class<? extends Module> load(String name,
      ClassLoader pluginLoader) throws ClassNotFoundException {
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Class<? extends Module> clazz =
        (Class<? extends Module>) Class.forName(name, false, pluginLoader);
    if (!Module.class.isAssignableFrom(clazz)) {
      throw new ClassCastException(String.format(
          "Class %s does not implement %s", name, Module.class.getName()));
    }
    return clazz;
  }
}
