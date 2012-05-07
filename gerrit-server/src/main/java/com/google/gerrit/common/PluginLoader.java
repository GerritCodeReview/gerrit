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
import com.google.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class PluginLoader {

  private File plugInsDir;

  private HashMap<String, Plugin> plugInCache;

  private Logger log = LoggerFactory.getLogger(PluginLoader.class);

  @Inject
  public PluginLoader(SitePaths sitePaths) {
    plugInsDir = sitePaths.plugins_dir;
  }

  private synchronized void initialize() {
    if (plugInCache != null) {
      return;
    }

    plugInCache = new HashMap<String, Plugin>();
    loadPlugIns();
  }

  public Plugin get(String plugInName) {
    initialize();
    return plugInCache.get(plugInName);
  }

  public Collection<Plugin> getPlugins() {
    initialize();
    return plugInCache.values();
  }

  private void loadPlugIns() {

    Collection<File> plugInJars = Collections.emptyList();
    try {
      plugInJars = getPlugIns();
    } catch (IOException e) {
      log.error("Cannot scan Gerrit plugins directory looking for jar files", e);
      return;
    }

    for (File jarFile : plugInJars) {

      Plugin plugIn;
      try {
        plugIn = loadPlugIn(jarFile);
        plugInCache.put(plugIn.name, plugIn);
      } catch (IOException e) {
        log.warn("Cannot access PlugIn jar " + jarFile, e);
      } catch (ClassNotFoundException e) {
        log.warn("Cannot load PlugIn class module from " + jarFile, e);
      }
    }
  }

  private Plugin loadPlugIn(File jarFile) throws IOException,
      ClassNotFoundException {

    Manifest jarManifest = new JarFile(jarFile).getManifest();
    ClassLoader jarClassLoader = new URLClassLoader(getPluginURLs(jarFile));

    Attributes attrs = jarManifest.getMainAttributes();
    String pluginName = attrs.getValue("Gerrit-Plugin");
    String moduleName = attrs.getValue("Gerrit-SshModule");

    Class<?> moduleClass = jarClassLoader.loadClass(moduleName);

    return new Plugin(pluginName, moduleClass);
  }

  private URL[] getPluginURLs(File jarFile) throws MalformedURLException {
    return new URL[] {jarFile.toURI().toURL()};
  }


  @SuppressWarnings("unchecked")
  private Collection<File> getPlugIns() throws IOException {
    if (plugInsDir == null || !plugInsDir.exists())
      return Collections.emptyList();

    return FileUtils.listFiles(plugInsDir, new String[] {"jar"}, false);
  }

}
