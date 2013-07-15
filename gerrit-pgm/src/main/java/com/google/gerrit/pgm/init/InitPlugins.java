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

package com.google.gerrit.pgm.init;

import com.google.common.collect.Lists;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Singleton
public class InitPlugins implements InitStep {
  private final static String PLUGIN_DIR = "WEB-INF/plugins/";
  private final static String JAR = ".jar";

  private final ConsoleUI ui;
  private final SitePaths site;
  private InitPluginStepsLoader pluginLoader;

  @Inject
  InitPlugins(final ConsoleUI ui, final SitePaths site, InitPluginStepsLoader pluginLoader) {
    this.ui = ui;
    this.site = site;
    this.pluginLoader = pluginLoader;
  }

  public static class PluginData {
    public final String name;
    public final String jarName;
    public final ByteArrayOutputStream content;

    private PluginData(String name, String jarName, ByteArrayOutputStream content) {
      this.name = name;
      this.content = content;
      this.jarName = jarName;
    }
  }

  public static List<PluginData> listPlugins() throws IOException {
    final File myWar = GerritLauncher.getDistributionArchive();
    final List<PluginData> result = Lists.newArrayList();
    try {
      final ZipFile zf = new ZipFile(myWar);
      try {
        final Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
          final ZipEntry ze = e.nextElement();
          if (ze.isDirectory()) {
            continue;
          }

          if (ze.getName().startsWith(PLUGIN_DIR) && ze.getName().endsWith(JAR)) {
            final String pluginJarName = new File(ze.getName()).getName();
            final String pluginName = pluginJarName.substring(0,  pluginJarName.length() - JAR.length());
            final InputStream in = zf.getInputStream(ze);

            result.add(new PluginData(pluginName, pluginJarName, copy(in)));
          }
        }
      } finally {
        zf.close();
      }
    } catch (IOException e) {
      throw new IOException("Failure during plugin installation", e);
    }
    return result;
  }

  @Override
  public void run() throws Exception {
    ui.header("Plugins");

    installPlugins();
    initPlugins();
  }

  private void installPlugins() throws IOException {
    List<PluginData> plugins = listPlugins();
    for (PluginData plugin : plugins) {
      String pluginName = plugin.name;
      String pluginJarName = plugin.jarName;
      InputStream in = new ByteArrayInputStream(plugin.content.toByteArray());
      try {
        final File tmpPlugin = PluginLoader.storeInTemp(pluginName, in, site);
        final String pluginVersion = getVersion(tmpPlugin);

        if (!ui.yesno(false, "Install plugin %s version %s", pluginName,
            pluginVersion)) {
          tmpPlugin.delete();
          continue;
        }

        final File p = new File(site.plugins_dir, pluginJarName);
        if (p.exists()) {
          final String installedPluginVersion = getVersion(p);
          if (!ui.yesno(false,
              "version %s is already installed, overwrite it",
              installedPluginVersion)) {
            tmpPlugin.delete();
            continue;
          }
          if (!p.delete()) {
            throw new IOException("Failed to delete plugin " + pluginName
                + ": " + p.getAbsolutePath());
          }
        }
        if (!tmpPlugin.renameTo(p)) {
          throw new IOException("Failed to install plugin " + pluginName
              + ": " + tmpPlugin.getAbsolutePath() + " -> "
              + p.getAbsolutePath());
        }
      } finally {
        in.close();
      }
    }
    if (plugins.isEmpty()) {
      ui.message("No plugins found.");
    }
  }

  private static ByteArrayOutputStream copy(InputStream in) throws IOException {
    ByteArrayOutputStream copy = new ByteArrayOutputStream();
    int chunk = 0;
    byte[] data = new byte[256];

    while (-1 != (chunk = in.read(data))) {
      copy.write(data, 0, chunk);
    }

    return copy;
  }

  private void initPlugins() throws Exception {
    for (InitStep initStep : pluginLoader.getInitSteps()) {
      initStep.run();
    }
  }

  private static String getVersion(final File plugin) throws IOException {
    final JarFile jarFile = new JarFile(plugin);
    try {
      final Manifest manifest = jarFile.getManifest();
      final Attributes main = manifest.getMainAttributes();
      return main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    } finally {
      jarFile.close();
    }
  }
}
