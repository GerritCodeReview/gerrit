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
  private static final String PLUGIN_DIR = "WEB-INF/plugins/";
  private static final String JAR = ".jar";

  public static class PluginData {
    public final String name;
    public final String version;
    public final File pluginFile;

    private PluginData(String name, String version, File pluginFile) {
      this.name = name;
      this.version = version;
      this.pluginFile = pluginFile;
    }
  }

  public static List<PluginData> listPlugins(SitePaths site) throws IOException {
    return listPlugins(site, false);
  }

  public static List<PluginData> listPluginsAndRemoveTempFiles(SitePaths site) throws IOException {
    return listPlugins(site, true);
  }

  private static List<PluginData> listPlugins(SitePaths site, boolean deleteTempPluginFile) throws IOException {
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
            final File tmpPlugin = PluginLoader.storeInTemp(pluginName, in, site);
            final String pluginVersion = getVersion(tmpPlugin);
            if (deleteTempPluginFile) {
              tmpPlugin.delete();
            }

            result.add(new PluginData(pluginName, pluginVersion, tmpPlugin));
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

  private final ConsoleUI ui;
  private final SitePaths site;
  private final InitFlags initFlags;
  private final InitPluginStepsLoader pluginLoader;

  @Inject
  InitPlugins(final ConsoleUI ui, final SitePaths site,
      InitFlags initFlags, InitPluginStepsLoader pluginLoader) {
    this.ui = ui;
    this.site = site;
    this.initFlags = initFlags;
    this.pluginLoader = pluginLoader;
  }

  @Override
  public void run() throws Exception {
    ui.header("Plugins");

    installPlugins();
    initPlugins();
  }

  private void installPlugins() throws IOException {
    List<PluginData> plugins = listPlugins(site);
    for (PluginData plugin : plugins) {
      String pluginName = plugin.name;
      try {
        final File tmpPlugin = plugin.pluginFile;

        if (!(initFlags.installPlugins.contains(pluginName) || ui.yesno(false,
            "Install plugin %s version %s", pluginName, plugin.version))) {
          tmpPlugin.delete();
          continue;
        }

        final File p = new File(site.plugins_dir, plugin.name + ".jar");
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
        if (plugin.pluginFile.exists()) {
          plugin.pluginFile.delete();
        }
      }
    }
    if (plugins.isEmpty()) {
      ui.message("No plugins found.");
    }
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
