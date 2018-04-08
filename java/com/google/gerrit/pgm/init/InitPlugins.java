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

import com.google.common.collect.FluentIterable;
import com.google.gerrit.common.PluginData;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.server.plugins.JarPluginProvider;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class InitPlugins implements InitStep {
  public static final String PLUGIN_DIR = "WEB-INF/plugins/";
  public static final String JAR = ".jar";

  public static List<PluginData> listPlugins(
      SitePaths site, PluginsDistribution pluginsDistribution) throws IOException {
    return listPlugins(site, false, pluginsDistribution);
  }

  public static List<PluginData> listPluginsAndRemoveTempFiles(
      SitePaths site, PluginsDistribution pluginsDistribution) throws IOException {
    return listPlugins(site, true, pluginsDistribution);
  }

  private static List<PluginData> listPlugins(
      final SitePaths site,
      final boolean deleteTempPluginFile,
      PluginsDistribution pluginsDistribution)
      throws IOException {
    final List<PluginData> result = new ArrayList<>();
    pluginsDistribution.foreach(
        new PluginsDistribution.Processor() {
          @Override
          public void process(String pluginName, InputStream in) throws IOException {
            Path tmpPlugin = JarPluginProvider.storeInTemp(pluginName, in, site);
            String pluginVersion = getVersion(tmpPlugin);
            if (deleteTempPluginFile) {
              Files.delete(tmpPlugin);
            }
            result.add(new PluginData(pluginName, pluginVersion, tmpPlugin));
          }
        });
    return FluentIterable.from(result)
        .toSortedList(
            new Comparator<PluginData>() {
              @Override
              public int compare(PluginData a, PluginData b) {
                return a.name.compareTo(b.name);
              }
            });
  }

  private final ConsoleUI ui;
  private final SitePaths site;
  private final InitFlags initFlags;
  private final InitPluginStepsLoader pluginLoader;
  private final PluginsDistribution pluginsDistribution;

  private Injector postRunInjector;

  @Inject
  InitPlugins(
      final ConsoleUI ui,
      final SitePaths site,
      InitFlags initFlags,
      InitPluginStepsLoader pluginLoader,
      PluginsDistribution pluginsDistribution) {
    this.ui = ui;
    this.site = site;
    this.initFlags = initFlags;
    this.pluginLoader = pluginLoader;
    this.pluginsDistribution = pluginsDistribution;
  }

  @Override
  public void run() throws Exception {
    ui.header("Plugins");

    installPlugins();
    initPlugins();
  }

  @Override
  public void postRun() throws Exception {
    postInitPlugins();
  }

  @Inject(optional = true)
  void setPostRunInjector(Injector injector) {
    postRunInjector = injector;
  }

  private void installPlugins() throws IOException {
    ui.message("Installing plugins.\n");
    List<PluginData> plugins = listPlugins(site, pluginsDistribution);
    for (PluginData plugin : plugins) {
      String pluginName = plugin.name;
      try {
        final Path tmpPlugin = plugin.pluginPath;
        Path p = site.plugins_dir.resolve(plugin.name + ".jar");
        boolean upgrade = Files.exists(p);

        if (!(initFlags.installPlugins.contains(pluginName)
            || initFlags.installAllPlugins
            || ui.yesno(upgrade, "Install plugin %s version %s", pluginName, plugin.version))) {
          Files.deleteIfExists(tmpPlugin);
          continue;
        }

        if (upgrade) {
          final String installedPluginVersion = getVersion(p);
          if (!ui.yesno(
              upgrade,
              "%s %s is already installed, overwrite it",
              plugin.name,
              installedPluginVersion)) {
            Files.deleteIfExists(tmpPlugin);
            continue;
          }
          try {
            Files.delete(p);
          } catch (IOException e) {
            throw new IOException(
                "Failed to delete plugin " + pluginName + ": " + p.toAbsolutePath(), e);
          }
        }
        try {
          Files.move(tmpPlugin, p);
          if (upgrade) {
            // or update that is not an upgrade
            ui.message("Updated %s to %s\n", plugin.name, plugin.version);
          } else {
            ui.message("Installed %s %s\n", plugin.name, plugin.version);
          }
        } catch (IOException e) {
          throw new IOException(
              "Failed to install plugin "
                  + pluginName
                  + ": "
                  + tmpPlugin.toAbsolutePath()
                  + " -> "
                  + p.toAbsolutePath(),
              e);
        }
      } finally {
        Files.deleteIfExists(plugin.pluginPath);
      }
    }
    if (plugins.isEmpty()) {
      ui.message("No plugins found to install.\n");
    }
  }

  private void initPlugins() throws Exception {
    ui.message("Initializing plugins.\n");
    Collection<InitStep> initSteps = pluginLoader.getInitSteps();
    if (initSteps.isEmpty()) {
      ui.message("No plugins found with init steps.\n");
    } else {
      for (InitStep initStep : initSteps) {
        initStep.run();
      }
    }
  }

  private void postInitPlugins() throws Exception {
    for (InitStep initStep : pluginLoader.getInitSteps()) {
      postRunInjector.injectMembers(initStep);
      initStep.postRun();
    }
  }

  private static String getVersion(Path plugin) throws IOException {
    try (JarFile jarFile = new JarFile(plugin.toFile())) {
      Manifest manifest = jarFile.getManifest();
      Attributes main = manifest.getMainAttributes();
      return main.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    }
  }
}
